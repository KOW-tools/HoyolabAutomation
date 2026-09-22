package cc.kowx712.autohoyolab.auth

import java.security.MessageDigest
import kotlin.random.Random

/**
 * Dynamic Secret (DS) generator for HoYoLAB API requests
 */
object DSGenerator {

    private const val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /**
     * Generate DS for app login requests
     * @param jsonBody The compact JSON body string
     * @return DS string in format: "timestamp,random,hash"
     */
    fun generateAppLoginDS(jsonBody: String): String {
        val t = System.currentTimeMillis() / 1000
        val r = generateRandomString(6, ALPHANUMERIC)
        val b = jsonBody
        val q = ""

        val hashInput = "salt=${HoyoLabAuthConstants.Salts.APP_LOGIN}&t=$t&r=$r&b=$b&q=$q"
        val h = md5(hashInput)

        return "$t,$r,$h"
    }

    /**
     * Generate DS for token refresh requests
     * @param salt The salt to use (default: APP_LOGIN)
     * @return DS string in format: "timestamp,random,hash"
     */
    fun generateDynamicSecret(salt: String = HoyoLabAuthConstants.Salts.APP_LOGIN): String {
        val t = System.currentTimeMillis() / 1000
        val r = generateRandomString(6, LETTERS)

        val hashInput = "salt=$salt&t=$t&r=$r"
        val h = md5(hashInput)

        return "$t,$r,$h"
    }

    private fun generateRandomString(length: Int, chars: String): String {
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
