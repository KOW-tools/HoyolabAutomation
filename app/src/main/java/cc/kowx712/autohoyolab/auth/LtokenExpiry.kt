package cc.kowx712.autohoyolab.auth

object LtokenExpiry {
    fun expiresAt(setCookieHeaders: List<String>, nowMillis: Long = System.currentTimeMillis()): Long {
        for (header in setCookieHeaders) {
            val name = header.substringBefore('=').trim()
            if (!name.equals("ltoken_v2", ignoreCase = true)) continue

            val seconds = Regex("""(?:^|;)\s*Max-Age\s*=\s*(-?\d+)""", RegexOption.IGNORE_CASE)
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: continue
            if (seconds > 0) return nowMillis + seconds * 1000L
        }
        return 0L
    }
}
