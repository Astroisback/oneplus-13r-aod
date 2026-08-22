package com.astro.displayhelper

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

// Long-lived ADB client identity for this app. Key + cert are cached in the
// private data dir so pairing survives launches. Neither leaves the device.
class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)

        val filesDir = context.filesDir
        val keyFile = File(filesDir, "adbkey.pk8")
        val certFile = File(filesDir, "adbkey.cert")

        if (keyFile.exists() && certFile.exists()) {
            privateKey = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certFile.inputStream())
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            }.generateKeyPair()

            privateKey = kp.private
            certificate = buildCertificate(kp.public, privateKey)

            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "DisplayHelper"

    companion object {
        @Volatile
        private var instance: AdbConnectionManager? = null

        fun getInstance(context: Context): AdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(context.applicationContext).also { instance = it }
            }

        // Self-signed X.509 built with BouncyCastle. adbd only checks the TLS handshake
        // succeeds and the pubkey matches what the user paired with; the certificate
        // metadata is not inspected, so a 100-year validity is fine and avoids
        // spurious rotation.
        private fun buildCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
            val subject = X500Name("CN=DisplayHelper")
            val notBefore = Date()
            val notAfter = Date(notBefore.time + 100L * 365 * 24 * 60 * 60 * 1000)
            val serial = BigInteger.valueOf(System.currentTimeMillis())

            val builder = JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, publicKey
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))

            val signer = JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
