package com.antigravity.wifimanager.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Gọi API chia sẻ WiFi tùy chỉnh theo vị trí (nguồn bổ sung).
 */
class SharedWifiApiClient(
    private val baseUrl: String,
    private val apiKey: String = "",
    private val providerLabel: String = "API tùy chỉnh"
) {

    fun fetchNearby(latitude: Double, longitude: Double, radiusMeters: Int = 800): List<SharedWifiCredential> {
        if (baseUrl.isBlank()) return emptyList()

        val endpoint = buildUrl(latitude, longitude, radiusMeters)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("X-Api-Key", apiKey)
            }
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code !in 200..299 || body.isBlank()) {
                emptyList()
            } else {
                SharedWifiJsonParser.parse(body, providerLabel, latitude, longitude)
            }
        } catch (_: Exception) {
            connection.disconnect()
            emptyList()
        }
    }

    /** Chỉ dùng khi URL cấu hình có `{bssid}` hoặc `{mac}`. */
    fun fetchByBssid(bssid: String): List<SharedWifiCredential> {
        if (baseUrl.isBlank()) return emptyList()
        val template = baseUrl.trim()
        if (!template.contains("{bssid}", ignoreCase = true) &&
            !template.contains("{mac}", ignoreCase = true)
        ) {
            return emptyList()
        }

        val normalized = bssid.trim().lowercase()
        val endpoint = template
            .replace("{bssid}", URLEncoder.encode(normalized, StandardCharsets.UTF_8.name()))
            .replace("{mac}", URLEncoder.encode(normalized, StandardCharsets.UTF_8.name()))

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("X-Api-Key", apiKey)
            }
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (code !in 200..299 || body.isBlank()) emptyList()
            else SharedWifiJsonParser.parse(body, providerLabel, null, null)
        } catch (_: Exception) {
            connection.disconnect()
            emptyList()
        }
    }

    private fun buildUrl(lat: Double, lng: Double, radiusMeters: Int): String {
        val template = baseUrl.trim()
        if (template.contains("{lat}") || template.contains("{lng}")) {
            return template
                .replace("{lat}", lat.toString())
                .replace("{lng}", lng.toString())
                .replace("{lon}", lng.toString())
                .replace("{radius}", radiusMeters.toString())
        }

        val separator = if (template.contains("?")) "&" else "?"
        val latEnc = URLEncoder.encode(lat.toString(), StandardCharsets.UTF_8.name())
        val lngEnc = URLEncoder.encode(lng.toString(), StandardCharsets.UTF_8.name())
        val radiusEnc = URLEncoder.encode(radiusMeters.toString(), StandardCharsets.UTF_8.name())
        return "$template${separator}lat=$latEnc&lng=$lngEnc&radius=$radiusEnc"
    }
}
