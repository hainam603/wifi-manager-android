package com.antigravity.wifimanager.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiConnectionState

/** Phân nhóm danh sách quét — đồng bộ, nhẹ (dữ liệu đã có trên WifiApInfo). */
@Composable
fun rememberScannerDisplayState(
    networks: List<WifiApInfo>,
    connection: WifiConnectionState,
    resolvePassword: (ssid: String, bssid: String?) -> String? = { _, _ -> null }
): ScannerDisplayState {
    val connSsid = connection.ssid
    val connBssid = connection.bssid
    val connFreq = connection.frequencyMhz
    val connConnected = connection.isConnected

    return remember(
        networks,
        connSsid,
        connBssid,
        connFreq,
        connConnected
    ) {
        ScannerUiMapper.build(networks, connection, resolvePassword)
    }
}
