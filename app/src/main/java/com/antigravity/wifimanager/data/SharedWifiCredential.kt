package com.antigravity.wifimanager.data

/** Mật khẩu WiFi lấy từ API chia sẻ cộng đồng theo khu vực. */
data class SharedWifiCredential(
    val ssid: String,
    val password: String,
    val bssid: String? = null,
    val providerName: String = "",
    val distanceMeters: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cachedAtMs: Long = 0L,
    /** ID bản ghi WifiMaster (tra nhanh bằng getWifiById). */
    val wifiMasterId: Long? = null
)
