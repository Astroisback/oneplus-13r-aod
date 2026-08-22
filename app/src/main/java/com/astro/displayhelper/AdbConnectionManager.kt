package com.astro.displayhelper

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateExtensions
import sun.security.x509.CertificateIssuerName
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateSubjectName
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.KeyIdentifier
import sun.security.x509.PrivateKeyUsageExtension
import sun.security.x509.SubjectKeyIdentifierExtension
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

// Keeps the app's ADB client identity across launches. Stored in the app's private
// data dir which Paytm/other apps cannot read, and never leaves the device.
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

        private fun buildCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
            val subject = "CN=DisplayHelper"
            val algorithm = "SHA512withRSA"
            val notBefore = Date()
            // 100 years - private cert on-device only, expiry is meaningless here
            // and a short window means we'd need to regenerate periodically.
            val notAfter = Date(notBefore.time + 100L * 365 * 24 * 60 * 60 * 1000)

            val exts = CertificateExtensions().apply {
                set("SubjectKeyIdentifier",
                    SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
                set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
            }
            val x500 = X500Name(subject)
            val info = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
                set("subject", CertificateSubjectName(x500))
                set("key", CertificateX509Key(publicKey))
                set("validity", CertificateValidity(notBefore, notAfter))
                set("issuer", CertificateIssuerName(x500))
                set("extensions", exts)
            }
            val cert = X509CertImpl(info)
            cert.sign(privateKey, algorithm)
            return cert
        }
    }
}

// Silence lint about unused import - kept for future auto-discovery use.
@Suppress("unused")
private val _keepAdbMdns = AdbMdns::class
