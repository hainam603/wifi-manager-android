package com.antigravity.wifimanager.ui.scanner

import androidx.compose.runtime.Immutable
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiCredentialKeys
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

    fun hasRows(): Boolean =
        connectedRow != null || savedRows.isNotEmpty() || nearbyRows.isNotEmpty()

    fun totalRowCount(): Int =
        (if (connectedRow != null) 1 else 0) + savedRows.size + nearbyRows.size
}

object ScannerUiMapper {

    /** Hiển thị ngay trên UI thread — không resolve mật khẩu (tránh màn hình trống). */
    fun buildQuick(
        networks: List<WifiApInfo>,
        connection: WifiConnectionState
    ): ScannerDisplayState = build(
        networks = networks,
        connection = connection,
        resolvePassword = { _, _ -> null },
        resolveSimilarPassword = { null }
    )

    private val wifiApComparator = compareByDescending<WifiApInfo> { it.signalPercent }
        .thenByDescending { it.is5GHz }
        .thenByDescending { it.frequencyMhz }
        .thenBy { it.ssid.lowercase(Locale.getDefault()) }

    /** Dùng dữ liệu đã enrich khi quét — không gọi repository từng dòng (tránh list trống/lag). */
    fun build(
        networks: List<WifiApInfo>,
        connection: WifiConnectionState,
        resolvePassword: (ssid: String, bssid: String?) -> String? = { _, _ -> null },
        resolveSimilarPassword: (ssid: String) -> Pair<String, String>? = { null }
    ): ScannerDisplayState {
        val connectedSsid = resolveConnectedSsid(connection)

        fun pickBestPerSsid(input: List<WifiApInfo>): List<WifiApInfo> {
            if (input.isEmpty()) return emptyList()
            return input
                .filter { it.ssid.isNotBlank() }
                .groupBy { it.ssid.lowercase(Locale.getDefault()) }
                .values
                .mapNotNull { group -> group.maxWithOrNull(wifiApComparator) }
        }

        val rawConnectable = networks.filter { it.isScannerConnectable() }
        val rawNonConnectable = networks.filter { !it.isScannerConnectable() }

        val bestConnectable = pickBestPerSsid(rawConnectable).filter { it.ssid != connectedSsid }
        
        val connectableSsids = bestConnectable.map { it.ssid.lowercase(Locale.getDefault()) }.toSet()
        val connectedSsidLower = connectedSsid?.lowercase(Locale.getDefault())
        val bestNonConnectable = pickBestPerSsid(rawNonConnectable)
            .filter { ap ->
                val lowerSsid = ap.ssid.lowercase(Locale.getDefault())
                lowerSsid != connectedSsidLower && !connectableSsids.contains(lowerSsid)
            }

        val connectedAp = resolveConnectedAp(networks, connection, connectedSsid)

        fun rowModel(ap: WifiApInfo, isNearbyGroup: Boolean): ScannerApRowModel {
            val needsPassword = ap.securityType.contains("WPA", ignoreCase = true) ||
                ap.securityType.contains("SAE", ignoreCase = true) ||
                ap.securityType.contains("PSK", ignoreCase = true)
            val hasSystemCredential = ap.hasStoredPassword || ap.isReadyToConnect
            val resolvedPassword = resolvePassword(ap.ssid, ap.bssid)
            val passwordDisplay = buildPasswordDisplay(
                ap = ap,
                isNearbyGroup = isNearbyGroup,
                hasSystemCredential = hasSystemCredential,
                resolvedPassword = resolvedPassword
            )
            val similar = resolveSimilarPassword(ap.ssid)
            return ScannerApRowModel(
                ap = ap,
                passwordDisplay = passwordDisplay,
                needsPassword = needsPassword,
                hasSystemCredential = hasSystemCredential,
                savedPassword = resolvedPassword,
                similarSsid = similar?.first,
                similarPassword = similar?.second
            )
        }

        val savedRows = bestConnectable
            .sortedWith(wifiApComparator)
            .map { rowModel(it, isNearbyGroup = false) }

        val nearbyRows = bestNonConnectable
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
        var ssid = connection.ssid.trim()
        if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length >= 2) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        if (ssid == "Mạng WiFi" ||
            ssid == "Đang kết nối WiFi" ||
            ssid == "<unknown ssid>"
        ) {
            return null
        }
        return ssid
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
        hasSystemCredential: Boolean,
        resolvedPassword: String?
    ): String? {
        val apiPass = ap.sharedPasswordFromApi
        val provider = ap.sharedProviderName

        if (ap.isSharedPasswordRejected) {
            return when {
                WifiCredentialKeys.isPlausibleWifiPassword(resolvedPassword, ap.bssid) ->
                    resolvedPassword!!.trim()
                WifiCredentialKeys.isPlausibleWifiPassword(apiPass, ap.bssid) && !provider.isNullOrBlank() ->
                    "${apiPass!!.trim()} (API: $provider)"
                WifiCredentialKeys.isPlausibleWifiPassword(apiPass, ap.bssid) -> apiPass!!.trim()
                else -> null
            }
        }

        if (WifiCredentialKeys.isPlausibleWifiPassword(resolvedPassword, ap.bssid)) {
            return resolvedPassword!!.trim()
        }

        if (!apiPass.isNullOrBlank() && WifiCredentialKeys.isPlausibleWifiPassword(apiPass, ap.bssid)) {
            return if (!provider.isNullOrBlank()) {
                "$apiPass (chia sẻ: $provider)"
            } else {
                apiPass
            }
        }

        return null
    }
}
