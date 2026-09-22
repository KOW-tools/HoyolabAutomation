package cc.kowx712.autohoyolab.auth

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA encryptor for user credentials
 */
object CredentialsEncryptor {

    /**
     * Encrypt text using RSA public key
     * @param text The plaintext to encrypt
     * @return Base64-encoded encrypted text
     */
    fun encryptCredentials(text: String): String {
        // Remove PEM headers/footers and decode Base64
        val publicKeyPEM = HoyoLabAuthConstants.LOGIN_RSA_PUBLIC_KEY
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.decode(publicKeyPEM, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        // Encrypt using RSA/ECB/PKCS1Padding
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }
}
