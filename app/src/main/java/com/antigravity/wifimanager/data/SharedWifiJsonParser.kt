package com.antigravity.wifimanager.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SharedWifiJsonParser {

    fun parse(body: String, defaultProvider: String, userLat: Double? = null, userLng: Double? = null): List<SharedWifiCredential> {
        val result = mutableListOf<SharedWifiCredential>()
        try {
            val trimmed = body.trim()
            if (trimmed.isBlank()) return emptyList()

            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("hotspots") -> obj.getJSONArray("hotspots")
                        obj.has("networks") -> obj.getJSONArray("networks")
                        obj.has("data") -> obj.getJSONArray("data")
                        obj.has("items") -> obj.getJSONArray("items")
                        obj.has("results") -> obj.getJSONArray("results")
                        obj.has("ssid") -> JSONArray().put(obj)
                        else -> JSONArray()
                    }
                }
            }

            for (i in 0 until array.length()) {
                parseItem(array.optJSONObject(i) ?: continue, defaultProvider, userLat, userLng)?.let { result.add(it) }
            }
        } catch (_: Exception) {
            // JSON không hợp lệ
        }
        return result.distinctBy { "${it.ssid}|${it.bssid ?: ""}" }
    }

    private fun parseItem(
        item: JSONObject,
        defaultProvider: String,
        userLat: Double?,
        userLng: Double?
    ): SharedWifiCredential? {
        val ssid = firstNonBlank(
            item.optString("ssid"),
            item.optString("name"),
            item.optString("network_name"),
            item.optString("networkName")
        )
        val password = firstNonBlank(
            item.optString("password"),
            item.optString("psk"),
            item.optString("pass"),
            item.optString("key"),
            item.optString("security.password"),
            item.optNestedString("security", "password")
        )
        if (ssid.isBlank() || password.isBlank()) return null

        val apLat = item.optDouble("location.latitude", Double.NaN).takeIf { !it.isNaN() }
            ?: item.optNestedDouble("location", "latitude")
        val apLng = item.optDouble("location.longitude", Double.NaN).takeIf { !it.isNaN() }
            ?: item.optNestedDouble("location", "longitude")

        val distance = when {
            item.has("distance_m") -> item.optInt("distance_m").takeIf { it >= 0 }
            item.has("distance") -> item.optInt("distance").takeIf { it >= 0 }
            userLat != null && userLng != null && apLat != null && apLng != null ->
                haversineMeters(userLat, userLng, apLat, apLng).toInt()
            else -> null
        }

        val wifiMasterId = item.optLong("id", 0L).takeIf { it > 0L }

        return SharedWifiCredential(
            ssid = ssid,
            password = password,
            bssid = parseBssid(item),
            providerName = firstNonBlank(
                item.optString("provider"),
                item.optString("user.name"),
                defaultProvider
            ),
            distanceMeters = distance,
            latitude = apLat ?: userLat,
            longitude = apLng ?: userLng,
            cachedAtMs = System.currentTimeMillis(),
            wifiMasterId = wifiMasterId
        )
    }

    private fun parseBssid(item: JSONObject): String? {
        val direct = item.optString("bssid").ifBlank { item.optString("netid") }
        if (direct.isNotBlank()) return direct

        val bssids = item.optJSONArray("bssids") ?: return null
        if (bssids.length() == 0) return null

        return when (val first = bssids.opt(0)) {
            is Number -> numericBssidToMac(first.toLong())
            is String -> first.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun numericBssidToMac(value: Long): String {
        val hex = value.toString(16).padStart(12, '0')
        return (0 until 6).joinToString(":") { i ->
            hex.substring(i * 2, i * 2 + 2).uppercase()
        }
    }

    private fun JSONObject.optNestedString(parent: String, child: String): String {
        return optJSONObject(parent)?.optString(child).orEmpty()
    }

    private fun JSONObject.optNestedDouble(parent: String, child: String): Double? {
        val obj = optJSONObject(parent) ?: return null
        if (!obj.has(child)) return null
        return obj.optDouble(child)
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() } ?: ""
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
