package cc.kowx712.autohoyolab.auth

import kotlin.random.Random

/**
 * Device ID generator for HoYoLAB authentication
 */
object DeviceIdGenerator {

    private const val DEVICE_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * Generate a 16-character lowercase alphanumeric device ID
     */
    fun generate(): String {
        return (1..16)
            .map { DEVICE_ID_CHARS[Random.nextInt(DEVICE_ID_CHARS.length)] }
            .joinToString("")
    }
}
