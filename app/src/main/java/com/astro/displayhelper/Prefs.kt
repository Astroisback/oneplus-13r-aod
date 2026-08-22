package com.astro.displayhelper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

// Minimal prefs wrapper. Just remembers the pairing port sometimes and the last
// connect port so you do not have to re-type it every reboot.
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("dh", Context.MODE_PRIVATE)

    var lastConnectPort: Int
        get() = sp.getInt("last_connect_port", 0)
        set(value) = sp.edit { putInt("last_connect_port", value) }

    var paired: Boolean
        get() = sp.getBoolean("paired", false)
        set(value) = sp.edit { putBoolean("paired", value) }

    fun clearPairing() = sp.edit { remove("paired") }
}
