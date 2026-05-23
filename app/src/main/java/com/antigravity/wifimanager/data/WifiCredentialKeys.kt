package com.antigravity.wifimanager.data

import java.util.Locale

/** Khóa lưu/tra mật khẩu: ưu tiên SSID|BSSID, tương thích khóa chỉ SSID (cũ). */
object WifiCredentialKeys {

    private const val SEPARATOR = "|"
    private val MAC_ADDRESS_PATTERN = Regex(
        "^([0-9a-f]{2}[:-]){5}[0-9a-f]{2}$",
        RegexOption.IGNORE_CASE
    )
    private val PLACEHOLDER_BSSIDS = setOf(
        "02:00:00:00:00:00",
        "00:00:00:00:00:00"
    )

    fun normalizeBssid(bssid: String?): String {
        val raw = bssid?.trim().orEmpty()
        if (raw.isBlank()) return ""
        val lower = raw.lowercase(Locale.getDefault())
        if (PLACEHOLDER_BSSIDS.contains(lower)) return ""
        if (lower.replace(":", "").replace("0", "").isEmpty()) return ""
        return lower
    }

    fun isPlaceholderBssid(bssid: String?): Boolean {
        val raw = bssid?.trim().orEmpty()
        if (raw.isBlank()) return true
        return normalizeBssid(raw).isEmpty()
    }

    fun isValidBssid(bssid: String?): Boolean = normalizeBssid(bssid).isNotEmpty()

    /** Khóa trong SharedPreferences / map mật khẩu đã lưu. */
    fun storageKey(ssid: String, bssid: String?): String {
        val normalizedSsid = ssid.trim()
        val nb = normalizeBssid(bssid)
        return if (nb.isNotEmpty()) {
            "${normalizedSsid.lowercase(Locale.getDefault())}$SEPARATOR$nb"
        } else {
            normalizedSsid
        }
    }

    fun parseStorageKey(key: String): Pair<String, String?> {
        val sep = key.indexOf(SEPARATOR)
        if (sep <= 0) return key to null
        val ssid = key.substring(0, sep)
        val bssid = key.substring(sep + 1)
        return ssid to bssid.ifBlank { null }
    }

    fun credentialKey(ssid: String, bssid: String?): String = storageKey(ssid, bssid)

    fun looksLikeMacAddress(value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isBlank()) return false
        return MAC_ADDRESS_PATTERN.matches(v)
    }

    /** Loại BSSID, chuỗi capabilities scan, và giá trị rỗng — tránh hiển thị nhầm là mật khẩu. */
    fun isPlausibleWifiPassword(value: String?, bssid: String? = null): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isBlank()) return false
        if (looksLikeMacAddress(v)) return false
        if (v.contains('[') && v.contains(']')) return false
        val nb = normalizeBssid(bssid)
        if (nb.isNotEmpty() && normalizeBssid(v) == nb) return false
        return true
    }
}
