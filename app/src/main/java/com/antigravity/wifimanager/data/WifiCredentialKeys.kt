package com.antigravity.wifimanager.data

import java.util.Locale

/** Khóa lưu/tra mật khẩu: ưu tiên SSID|BSSID, tương thích khóa chỉ SSID (cũ). */
object WifiCredentialKeys {

    private const val SEPARATOR = "|"
    private const val PLACEHOLDER_BSSID = "02:00:00:00:00:00"

    fun normalizeBssid(bssid: String?): String {
        val raw = bssid?.trim().orEmpty()
        if (raw.isBlank() || raw.equals(PLACEHOLDER_BSSID, ignoreCase = true)) return ""
        return raw.lowercase(Locale.getDefault())
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
}
