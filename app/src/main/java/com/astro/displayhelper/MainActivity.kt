package com.astro.displayhelper

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro.displayhelper.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// The three commands that make up the "fire" sequence. Order matters:
//   1. keep_aod_on -> enable full-screen AOD past the timeout
//   2. disable dev-settings menu (hides toggles from Paytm)
//   3. disable adb / wifi-adb LAST because the moment those go the connection dies
//
// NOTE: we deliberately leave adb_wifi_enabled ON if you plan to reuse pairing
// from LADB-style apps. Here we turn it off too because pairing survives it.
private const val CMD_KEEP =
    "dumpsys activity service com.android.systemui/.keyguard.KeyguardService debug keep_aod_on 1"
private const val CMD_DEV_OFF =
    "settings put global development_settings_enabled 0"
private const val CMD_ADB_OFF =
    "settings put global adb_enabled 0"
private const val CMD_WIFI_ADB_OFF =
    "settings put global adb_wifi_enabled 0"

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        prefs = Prefs(this)
        if (prefs.lastConnectPort > 0) {
            b.connectPort.setText(prefs.lastConnectPort.toString())
        }
        updatePairedState()

        b.btnPair.setOnClickListener { doPair() }
        b.btnFire.setOnClickListener { doFire() }
        b.btnForget.setOnClickListener {
            prefs.clearPairing()
            updatePairedState()
            status("Pairing forgotten. You'll need to re-pair next time.")
        }
    }

    private fun updatePairedState() {
        val paired = prefs.paired
        b.pairSection.visibility = if (paired) View.GONE else View.VISIBLE
        b.btnForget.visibility = if (paired) View.VISIBLE else View.GONE
        b.status.text = if (paired) "Paired. Enter port and Fire." else "Not paired yet."
    }

    private fun doPair() {
        val port = b.pairPort.text.toString().toIntOrNull()
        val code = b.pairCode.text.toString().trim()
        if (port == null || port !in 1..65535) {
            toast("Pair port looks wrong"); return
        }
        if (code.length != 6 || code.any { !it.isDigit() }) {
            toast("Pair code should be 6 digits"); return
        }

        setBusy(true, "Pairing on 127.0.0.1:$port ...")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AdbConnectionManager.getInstance(this@MainActivity)
                        .pair("127.0.0.1", port, code)
                }
            }
            setBusy(false)
            if (result.isSuccess && result.getOrDefault(false)) {
                prefs.paired = true
                updatePairedState()
                status("Paired. Now enter the CONNECT port and tap Fire.")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "pair returned false"
                status("Pair failed: $msg")
            }
        }
    }

    private fun doFire() {
        if (!prefs.paired) {
            toast("Pair first."); return
        }
        val port = b.connectPort.text.toString().toIntOrNull()
        if (port == null || port !in 1..65535) {
            toast("Connect port looks wrong"); return
        }
        prefs.lastConnectPort = port

        setBusy(true, "Connecting to 127.0.0.1:$port ...")
        lifecycleScope.launch {
            val log = StringBuilder()
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val mgr = AdbConnectionManager.getInstance(this@MainActivity)
                    if (!mgr.connect("127.0.0.1", port)) {
                        throw IllegalStateException("connect() returned false")
                    }
                    try {
                        // Each `run` is best-effort - a failure of any single command
                        // must not abort the sequence, because:
                        //  * dumpsys sometimes returns an already-closed stream
                        //    (no output payload), which upstream reads as "Stream
                        //    closed" instead of EOF. The command still executed.
                        //  * disabling adb kills the very socket we are talking
                        //    over, so the LAST reply is always an IO failure.
                        run(mgr, "keep AOD on", CMD_KEEP, log)
                        run(mgr, "hide developer options", CMD_DEV_OFF, log)
                        // Wireless adb goes before USB adb; ordering matters
                        // because the moment either is off the connection dies.
                        run(mgr, "disable wireless adb", CMD_WIFI_ADB_OFF, log)
                        run(mgr, "disable adb (connection ends here)", CMD_ADB_OFF, log)
                    } finally {
                        runCatching { mgr.disconnect() }
                    }
                }
                true
            }.getOrElse { e ->
                log.appendLine("FATAL: ${e.message}")
                false
            }
            setBusy(false)
            // As long as we got past connect() we count it a success. The AOD
            // command runs first and is the one that matters; the tidy-up
            // commands can complain and it does not undo AOD.
            status(if (ok) "Done. AOD is on, dev options off. Unplug and enjoy." else "Failed. See log below.")
            b.log.text = log.toString()
        }
    }

    // Runs one shell command. Never throws to the caller; any IO issue is written
    // to the visible log and the sequence continues.
    private fun run(
        mgr: AdbConnectionManager,
        label: String,
        cmd: String,
        log: StringBuilder,
    ) {
        log.appendLine("> $label")
        val result = runCatching { runShell(mgr, cmd) }
        result.fold(
            onSuccess = { text ->
                val trimmed = text.trim()
                log.appendLine(if (trimmed.isEmpty()) "(ok, no output)" else trimmed)
            },
            onFailure = { e ->
                // "Stream closed" here is common and harmless: dumpsys or the
                // adbd-shutdown command terminate the stream before we can drain
                // more bytes. The write already went through.
                val msg = e.message ?: e.javaClass.simpleName
                log.appendLine("(no reply: $msg — command likely still ran)")
            },
        )
    }

    // Reads to EOF, blocking. Kept simple because the adblib stream is already
    // buffered and closes cleanly when the shell command exits.
    private fun runShell(mgr: AdbConnectionManager, cmd: String): String {
        val out = ByteArrayOutputStream()
        mgr.openStream("shell:$cmd").use { stream ->
            stream.openInputStream().use { input ->
                val buf = ByteArray(4096)
                while (true) {
                    val n = try {
                        input.read(buf)
                    } catch (_: java.io.IOException) {
                        // Treat abrupt close as EOF - we captured whatever bytes
                        // arrived; anything unread is fine because dumpsys/settings
                        // finish before the socket goes down.
                        break
                    }
                    if (n < 0) break
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    private fun setBusy(busy: Boolean, msg: String? = null) {
        b.btnFire.isEnabled = !busy
        b.btnPair.isEnabled = !busy
        b.progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (msg != null) status(msg)
    }

    private fun status(msg: String) {
        b.status.text = msg
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
