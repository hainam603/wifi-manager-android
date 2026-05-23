package com.antigravity.wifimanager.data

/** Kết quả cập nhật mật khẩu cộng đồng cho một AP (theo BSSID). */
data class WifiCredentialRefreshResult(
    val success: Boolean,
    val message: String,
    val password: String? = null,
    val stillRejected: Boolean = false
)
