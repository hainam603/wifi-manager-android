package com.antigravity.wifimanager.ui.scanner

import androidx.compose.runtime.Immutable
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiConnectionState
import java.util.Locale

@Immutable
data class ScannerApRowModel(
    val ap: WifiApInfo,
    val passwordDisplay: String?,
    val needsPassword: Boolean,
    val hasSystemCredential: Boolean,
    val savedPassword: String?,
    val similarSsid: String?,
    val similarPassword: String?
) {
    val stableKey: String
        get() = "${ap.ssid.lowercase(Locale.getDefault())}|${ap.bssid.lowercase(Locale.getDefault())}"
}

@Immutable
data class ScannerDisplayState(
    val connectedRow: ScannerApRowModel?,
    val savedRows: List<ScannerApRowModel>,
    val nearbyRows: List<ScannerApRowModel>
) {
    companion object {
        val Empty = ScannerDisplayState(null, emptyList(), emptyList())
    }
}

object ScannerUiMapper {

    private val wifiApComparator = compareByDescending<WifiApInfo> { it.signalPercent }
        .thenByDescending { it.is5GHz }
        .thenByDescending { it.frequencyMhz }
        .thenBy { it.ssid.lowercase(Locale.getDefault()) }

    /** Dùng dữ liệu đã enrich khi quét — không gọi repository từng dòng (tránh list trống/lag). */
    fun build(
        networks: List<WifiApInfo>,
        connection: WifiConnectionState
    ): ScannerDisplayState {
        val connectedSsid = resolveConnectedSsid(connection)
        val connectedAp = resolveConnectedAp(networks, connection, connectedSsid)

        fun rowModel(ap: WifiApInfo, isNearbyGroup: Boolean): ScannerApRowModel {
            val needsPassword = ap.securityType.contains("WPA", ignoreCase = true) ||
                ap.securityType.contains("SAE", ignoreCase = true) ||
                ap.securityType.contains("PSK", ignoreCase = true)
            val hasSystemCredential = ap.hasStoredPassword || ap.isReadyToConnect
            val passwordDisplay = buildPasswordDisplay(ap, isNearbyGroup, hasSystemCredential)
            return ScannerApRowModel(
                ap = ap,
                passwordDisplay = passwordDisplay,
                needsPassword = needsPassword,
                hasSystemCredential = hasSystemCredential,
                savedPassword = null,
                similarSsid = null,
                similarPassword = null
            )
        }

        val savedRows = networks
            .filter { it.ssid != connectedSsid && it.hasStoredPassword }
            .sortedWith(wifiApComparator)
            .map { rowModel(it, isNearbyGroup = false) }

        val nearbyRows = networks
            .filter { it.ssid != connectedSsid && !it.hasStoredPassword }
            .sortedWith(wifiApComparator)
            .map { rowModel(it, isNearbyGroup = true) }

        val connectedRow = connectedAp?.let { rowModel(it, isNearbyGroup = false) }

        return ScannerDisplayState(
            connectedRow = connectedRow,
            savedRows = savedRows,
            nearbyRows = nearbyRows
        )
    }

    private fun resolveConnectedSsid(connection: WifiConnectionState): String? {
        if (!connection.isConnected || connection.ssid.isEmpty()) return null
        if (connection.ssid == "Mạng WiFi" ||
            connection.ssid == "Đang kết nối WiFi" ||
            connection.ssid == "<unknown ssid>"
        ) {
            return null
        }
        return connection.ssid
    }

    private fun resolveConnectedAp(
        networks: List<WifiApInfo>,
        connection: WifiConnectionState,
        connectedSsid: String?
    ): WifiApInfo? {
        if (connectedSsid == null) return null
        val sameSsidAps = networks.filter { it.ssid == connectedSsid }
        val matched = when {
            sameSsidAps.isNotEmpty() -> {
                when {
                    connection.frequencyMhz >= 4900 ->
                        sameSsidAps.filter { it.is5GHz }.maxByOrNull { it.signalPercent }
                            ?: sameSsidAps.maxByOrNull { it.signalPercent }
                    connection.frequencyMhz in 2400..2500 ->
                        sameSsidAps.filter { !it.is5GHz }.maxByOrNull { it.signalPercent }
                            ?: sameSsidAps.maxByOrNull { it.signalPercent }
                    else -> sameSsidAps.maxByOrNull { it.signalPercent }
                }
            }
            else -> WifiApInfo(
                ssid = connectedSsid,
                bssid = connection.bssid.ifEmpty { "00:00:00:00:00:00" },
                signalPercent = connection.signalPercent,
                frequencyMhz = connection.frequencyMhz.coerceAtLeast(2412),
                isSaved = true,
                hasStoredPassword = true,
                securityType = connection.authType
            )
        }
        return matched?.copy(signalPercent = connection.signalPercent)
    }

    private fun buildPasswordDisplay(
        ap: WifiApInfo,
        isNearbyGroup: Boolean,
        hasSystemCredential: Boolean
    ): String? {
        val apiPass = ap.sharedPasswordFromApi
        val provider = ap.sharedProviderName

        if (ap.isSharedPasswordRejected) {
            return when {
                !apiPass.isNullOrBlank() && !provider.isNullOrBlank() -> "$apiPass (API: $provider)"
                !apiPass.isNullOrBlank() -> apiPass
                else -> null
            }
        }

        if (!apiPass.isNullOrBlank()) {
            return if (!provider.isNullOrBlank()) {
                "$apiPass (chia sẻ: $provider)"
            } else {
                apiPass
            }
        }

        return when {
            hasSystemCredential && (isNearbyGroup && ap.isReadyToConnect || !isNearbyGroup) ->
                "(đã lưu trên máy — mật khẩu ẩn)"
            else -> null
        }
    }
}
