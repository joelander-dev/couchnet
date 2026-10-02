package com.local.firetvremote.net

/**
 * Accepts only LAN addresses. The remote never dials a public host.
 */
object PrivateAddress {
    fun isPrivate(host: String, permitLoopback: Boolean = false): Boolean {
        val parts = host.trim().split('.')
        if (parts.size != 4) return false
        val numbers = parts.map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        val a = numbers[0]
        val b = numbers[1]
        if (permitLoopback && a == 127) return true
        return when (a) {
            10 -> true
            172 -> b in 16..31
            192 -> b == 168
            169 -> b == 254
            else -> false
        }
    }

    fun normalize(input: String, permitLoopback: Boolean = false): String? {
        val host = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
        return host.takeIf { isPrivate(it, permitLoopback) }
    }
}
