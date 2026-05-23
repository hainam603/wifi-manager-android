package com.antigravity.wifimanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Mạng có mật khẩu API đã thử kết nối thất bại — không thử lại cho đến khi API trả mật khẩu mới. */
class SharedWifiRejectedStore(context: Context) {

    private val cacheFile = File(context.filesDir, "shared_wifi_rejected.json")
    private val lock = Any()

    data class RejectedEntry(
        val ssid: String,
        val bssid: String,
        val password: String,
        val rejectedAtMs: Long
    )

    fun markRejected(ssid: String, bssid: String?, password: String) {
        if (ssid.isBlank() || password.isBlank()) return
        val normalizedBssid = normalizeBssid(bssid)

        synchronized(lock) {
            val entries = loadAll().associateBy { dedupeKey(it.ssid, it.bssid) }.toMutableMap()
            entries[dedupeKey(ssid, normalizedBssid)] = RejectedEntry(
                ssid = ssid,
                bssid = normalizedBssid,
                password = password,
                rejectedAtMs = System.currentTimeMillis()
            )
            saveAll(entries.values.toList())
        }
    }

    fun isRejected(ssid: String, bssid: String?, password: String): Boolean {
        if (ssid.isBlank() || password.isBlank()) return false
        val apBssid = WifiCredentialKeys.normalizeBssid(bssid)

        synchronized(lock) {
            return loadAll().any { entry ->
                if (!entry.ssid.equals(ssid, ignoreCase = true) || entry.password != password) {
                    return@any false
                }
                val entryBssid = WifiCredentialKeys.normalizeBssid(entry.bssid)
                when {
                    apBssid.isNotEmpty() && entryBssid.isNotEmpty() -> entryBssid == apBssid
                    apBssid.isNotEmpty() && entryBssid.isEmpty() -> false
                    else -> true
                }
            }
        }
    }

    fun findRejected(ssid: String, bssid: String? = null): RejectedEntry? {
        if (ssid.isBlank()) return null
        val apBssid = WifiCredentialKeys.normalizeBssid(bssid)

        synchronized(lock) {
            val forSsid = loadAll().filter { it.ssid.equals(ssid, ignoreCase = true) }
            if (apBssid.isNotEmpty()) {
                forSsid.firstOrNull {
                    WifiCredentialKeys.normalizeBssid(it.bssid) == apBssid
                }?.let { return it }
            }
            return forSsid
                .filter { WifiCredentialKeys.normalizeBssid(it.bssid).isEmpty() }
                .maxByOrNull { it.rejectedAtMs }
                ?: forSsid.maxByOrNull { it.rejectedAtMs }
        }
    }

    fun findRejectedForSsid(ssid: String): RejectedEntry? = findRejected(ssid, null)

    /** Gỡ cờ khi API trả mật khẩu khác với bản đã bị từ chối (cùng SSID). */
    fun clearIfPasswordUpdated(credentials: List<SharedWifiCredential>) {
        if (credentials.isEmpty()) return

        synchronized(lock) {
            val entries = loadAll().associateBy { dedupeKey(it.ssid, it.bssid) }.toMutableMap()
            var changed = false

            credentials.forEach { cred ->
                val credBssid = WifiCredentialKeys.normalizeBssid(cred.bssid)
                val removeKeys = entries.filter { (_, rejected) ->
                    if (!rejected.ssid.equals(cred.ssid, ignoreCase = true)) return@filter false
                    if (rejected.password == cred.password) return@filter false
                    val rejectedBssid = WifiCredentialKeys.normalizeBssid(rejected.bssid)
                    when {
                        credBssid.isNotEmpty() && rejectedBssid.isNotEmpty() ->
                            rejectedBssid == credBssid
                        credBssid.isEmpty() -> rejectedBssid.isEmpty()
                        else -> false
                    }
                }.keys
                removeKeys.forEach { key ->
                    entries.remove(key)
                    changed = true
                }
            }

            if (changed) {
                saveAll(entries.values.toList())
            }
        }
    }

    private fun loadAll(): List<RejectedEntry> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val array = JSONArray(cacheFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val ssid = obj.optString("ssid")
                    val password = obj.optString("password")
                    if (ssid.isBlank() || password.isBlank()) continue
                    add(
                        RejectedEntry(
                            ssid = ssid,
                            bssid = obj.optString("bssid"),
                            password = password,
                            rejectedAtMs = obj.optLong("at", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(entries: List<RejectedEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("ssid", entry.ssid)
                    put("bssid", entry.bssid)
                    put("password", entry.password)
                    put("at", entry.rejectedAtMs)
                }
            )
        }
        cacheFile.writeText(array.toString())
    }

    private fun dedupeKey(ssid: String, bssid: String): String =
        "${ssid.lowercase()}|${bssid.lowercase()}"

    private fun normalizeBssid(bssid: String?): String {
        val raw = bssid?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("02:00:00:00:00:00", ignoreCase = true)) return ""
        return raw.lowercase()
    }
}
