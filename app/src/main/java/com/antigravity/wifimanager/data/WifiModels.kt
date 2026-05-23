package com.antigravity.wifimanager.data

// Trạng thái kết nối hiện tại
data class WifiConnectionState(
    val ssid: String = "",
    val bssid: String = "",
    val signalPercent: Int = 0,
    val authType: String = "",
    val isConnected: Boolean = false,
    val frequencyMhz: Int = 0
) {
    val is5GHz: Boolean
        get() = frequencyMhz >= 4900
}

// Thông tin các mạng WiFi xung quanh quét được
data class WifiApInfo(
    val ssid: String,
    val bssid: String,
    val signalPercent: Int,
    val frequencyMhz: Int,
    val isSaved: Boolean = false,
    val hasStoredPassword: Boolean = false,
    val securityType: String = "Open",
    val isReadyToConnect: Boolean = false,
    val sharedProviderName: String? = null,
    /** Mật khẩu từ API (hiển thị; có thể đã bị đánh dấu không hợp lệ). */
    val sharedPasswordFromApi: String? = null,
    /** Mật khẩu từ API đã thử kết nối thất bại — chờ API cập nhật mật khẩu mới. */
    val isSharedPasswordRejected: Boolean = false
) {
    val is5GHz: Boolean
        get() = frequencyMhz >= 4900
}

enum class RootStatus {
    GRANTED,
    DENIED,
    UNAVAILABLE
}

// Nhật ký chuyển đổi mạng
data class SwitchLog(
    val timestamp: String,
    val fromSsid: String,
    val fromSignal: Int,
    val toSsid: String,
    val toSignal: Int,
    val isSuccess: Boolean,
    val failureReason: String? = null,
    val connectionStatus: String? = null
)
