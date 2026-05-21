package com.linkbridge.common.util

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Lightweight AES-128 obfuscation using a pre-shared key "linkbridge".
 * This is NOT production-grade security — just simple payload obfuscation
 * for the user's own LAN. Reliability > security.
 */
object CryptoUtil {

    private const val KEY_STRING = "linkbridge"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

    private fun getKey(): SecretKeySpec {
        // Derive a 16-byte key from the passphrase using MD5
        val md = MessageDigest.getInstance("MD5")
        val keyBytes = md.digest(KEY_STRING.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(plainText: String): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            plainText.toByteArray(Charsets.UTF_8)
        }
    }

    fun decrypt(cipherBytes: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey())
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            String(cipherBytes, Charsets.UTF_8)
        }
    }

    /**
     * Simple checksum for integrity verification.
     */
    fun checksum(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
