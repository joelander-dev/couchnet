package com.local.firetvremote.net

data class DialDescription(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
)

fun parseDialDescription(xml: String): DialDescription {
    return DialDescription(
        friendlyName = xmlTag(xml, "friendlyName")?.decodeXml(),
        manufacturer = xmlTag(xml, "manufacturer")?.decodeXml(),
        modelName = xmlTag(xml, "modelName")?.decodeXml(),
    )
}

fun isFireTv(description: DialDescription): Boolean {
    val model = description.modelName.orEmpty()
    if (model.startsWith("AFT", ignoreCase = true)) return true
    val blob = listOf(description.manufacturer, description.modelName, description.friendlyName)
        .joinToString(" ")
        .lowercase()
    return blob.contains("amazon") || blob.contains("fire tv") || blob.contains("firetv")
}

fun locationFromSsdp(message: String): String? {
    return message.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

fun hostFromHttpUrl(url: String): String? {
    val withoutScheme = url.trim().removePrefix("https://").removePrefix("http://")
    val host = withoutScheme.substringBefore('/').substringBefore(':').trim()
    return host.takeIf { PrivateAddress.isPrivate(it) }
}

fun tokenFromPinResponse(body: String?, headerToken: String?): String? {
    headerToken?.trim()?.takeIf { looksLikeToken(it) }?.let { return it }
    if (body.isNullOrBlank()) return null
    val description = Regex("\"description\"\\s*:\\s*\"([^\"]*)\"")
        .find(body)
        ?.groupValues
        ?.get(1)
    return description?.takeIf { looksLikeToken(it) }
}

private fun looksLikeToken(value: String): Boolean {
    if (value.length !in 4..80) return false
    val lower = value.lowercase()
    val rejected = listOf("unauthorized", "invalid", "error", "fail", "denied", "bad ")
    if (rejected.any { lower.contains(it) }) return false
    return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

private fun xmlTag(xml: String, name: String): String? {
    val match = Regex(
        """<(?:[A-Za-z0-9]+:)?$name(?:\s[^>]*)?>([^<]*)</(?:[A-Za-z0-9]+:)?$name>""",
        RegexOption.IGNORE_CASE,
    ).find(xml) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
}

private fun String.decodeXml(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
