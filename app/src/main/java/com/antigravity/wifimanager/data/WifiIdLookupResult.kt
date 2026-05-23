package com.antigravity.wifimanager.data

/** Kết quả tra mật khẩu theo ID bản ghi WifiMaster. */
data class WifiIdLookupResult(
    val success: Boolean,
    val message: String,
    val credential: SharedWifiCredential? = null
)
