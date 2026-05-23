package com.antigravity.wifimanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lưu WiFi cộng đồng đã tải xuống để tra cứu khi không có Internet.
 */
class SharedWifiOfflineStore(private val context: Context) {

    private val cacheFile = File(context.filesDir, "shared_wifi_offline.json")
    private val lock = Any()

    companion object {
        private val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000
        private const val BYTES_PER_ENTRY_ESTIMATE = 280
    }

    fun count(): Int = loadAll().size

    fun getStorageBytes(): Long = synchronized(lock) {
        if (cacheFile.exists()) cacheFile.length() else 0L
    }

    fun getStorageMb(): Double = getStorageBytes() / (1024.0 * 1024.0)

    fun estimateMaxNetworksForStorageMb(storageMb: Int): Int {
        val bytes = storageMb.toLong() * 1024L * 1024L
        return (bytes / BYTES_PER_ENTRY_ESTIMATE).toInt().coerceAtLeast(10_000)
    }

    fun clear() {
        synchronized(lock) {
            if (cacheFile.exists()) cacheFile.delete()
        }
    }

    fun loadAll(): List<SharedWifiCredential> {
        synchronized(lock) {
            if (!cacheFile.exists()) return emptyList()
            return try {
                val text = cacheFile.readText()
                if (text.isBlank()) return emptyList()
                parseArray(JSONArray(text))
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun queryNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<SharedWifiCredential> {
        val now = System.currentTimeMillis()
        return loadAll()
            .filter { cred -> now - cred.cachedAtMs <= MAX_AGE_MS }
            .mapNotNull { cred ->
                val apLat = cred.latitude ?: return@mapNotNull null
                val apLng = cred.longitude ?: return@mapNotNull null
                val dist = haversineMeters(latitude, longitude, apLat, apLng).toInt()
                if (dist <= radiusMeters) {
                    cred.copy(distanceMeters = dist)
                } else {
                    null
                }
            }
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    fun upsert(
        credentials: List<SharedWifiCredential>,
        fallbackLat: Double,
        fallbackLng: Double,
        maxEntries: Int,
        maxStorageBytes: Long
    ) {
        if (credentials.isEmpty()) return

        synchronized(lock) {
            val existing = loadAll().associateBy { dedupeKey(it) }.toMutableMap()
            val now = System.currentTimeMillis()

            credentials.forEach { raw ->
                val normalized = raw.copy(
                    latitude = raw.latitude ?: fallbackLat,
                    longitude = raw.longitude ?: fallbackLng,
                    cachedAtMs = if (raw.cachedAtMs > 0L) raw.cachedAtMs else now
                )
                existing[dedupeKey(normalized)] = normalized
            }

            var pruned = existing.values
                .filter { now - it.cachedAtMs <= MAX_AGE_MS }
                .sortedByDescending { it.cachedAtMs }

            if (pruned.size > maxEntries) {
                pruned = pruned.take(maxEntries)
            }

            while (pruned.isNotEmpty() && estimateBytes(pruned) > maxStorageBytes) {
                pruned = pruned.dropLast(1)
            }

            val array = JSONArray()
            pruned.forEach { array.put(toJson(it)) }
            cacheFile.writeText(array.toString())
        }
    }

    private fun estimateBytes(credentials: List<SharedWifiCredential>): Long {
        return credentials.size.toLong() * BYTES_PER_ENTRY_ESTIMATE
    }

    private fun dedupeKey(cred: SharedWifiCredential): String {
        val bssid = cred.bssid?.lowercase().orEmpty()
        return "${cred.ssid.lowercase()}|$bssid"
    }

    private fun parseArray(array: JSONArray): List<SharedWifiCredential> {
        val result = mutableListOf<SharedWifiCredential>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val ssid = obj.optString("ssid")
            val password = obj.optString("password")
            if (ssid.isBlank() || password.isBlank()) continue
            result.add(
                SharedWifiCredential(
                    ssid = ssid,
                    password = password,
                    bssid = obj.optString("bssid").ifBlank { null },
                    providerName = obj.optString("provider"),
                    latitude = obj.optDouble("lat").takeIf { !it.isNaN() && obj.has("lat") },
                    longitude = obj.optDouble("lng").takeIf { !it.isNaN() && obj.has("lng") },
                    cachedAtMs = obj.optLong("at", 0L)
                )
            )
        }
        return result
    }

    private fun toJson(cred: SharedWifiCredential): JSONObject {
        return JSONObject().apply {
            put("ssid", cred.ssid)
            put("password", cred.password)
            cred.bssid?.let { put("bssid", it) }
            put("provider", cred.providerName)
            cred.latitude?.let { put("lat", it) }
            cred.longitude?.let { put("lng", it) }
            put("at", cred.cachedAtMs)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
