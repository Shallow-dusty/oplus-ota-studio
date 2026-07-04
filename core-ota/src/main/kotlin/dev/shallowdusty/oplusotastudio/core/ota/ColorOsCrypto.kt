package dev.shallowdusty.oplusotastudio.core.ota

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ColorOsCrypto {

    fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(SecureRandom()::nextBytes)

    fun encryptCtrV2(
        plainText: String,
        key: ByteArray,
        iv: ByteArray,
    ): String {
        val encrypted = aesCtr(
            input = plainText.toByteArray(Charsets.UTF_8),
            key = key,
            iv = iv,
            mode = Cipher.ENCRYPT_MODE,
        )
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptCtrV2(
        cipher: String,
        key: String,
        iv: String,
    ): String {
        val decrypted = aesCtr(
            input = Base64.getDecoder().decode(cipher),
            key = Base64.getDecoder().decode(key),
            iv = Base64.getDecoder().decode(iv),
            mode = Cipher.DECRYPT_MODE,
        )
        return decrypted.toString(Charsets.UTF_8)
    }

    fun generateProtectedKey(
        key: String,
        publicKey: String,
    ): String {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))
        val rsaKey = keyFactory.generatePublic(publicKeySpec)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, rsaKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)))
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun aesCtr(
        input: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        mode: Int,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(input)
    }
}
