package com.antigravity.wifimanager.data

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SwitchAttemptResult(
    val attempted: Boolean,
    val success: Boolean = false,
    val userMessage: String? = null
)

object ConnectionStatusFormatter {
    fun fromState(state: WifiConnectionState): String {
        return if (state.isConnected) {
            "Đang kết nối: ${state.ssid} (${state.signalPercent}%)"
        } else {
            "Ngoại tuyến — chưa kết nối WiFi"
        }
    }
}

class WifiAutoSwitcher(private val repository: WifiRepository) {

    companion object {
        private const val SIGNAL_MARGIN_FOR_5GHZ_UPGRADE = 5
        private const val MIN_SIGNAL_GAIN_FOR_SWITCH = 10
    }

    suspend fun attemptSwitch(
        currentState: WifiConnectionState,
        enforceCooldown: Boolean = true,
        onNotifyUser: ((WifiConnectionState, WifiApInfo) -> Unit)? = null
    ): SwitchAttemptResult {
        if (!currentState.isConnected) {
            return SwitchAttemptResult(attempted = false, userMessage = "Chưa kết nối WiFi")
        }

        val now = System.currentTimeMillis()
        val lastSwitchTimeMs = repository.getLastSwitchAtMs()
        if (enforceCooldown && now - lastSwitchTimeMs < WifiRepository.SWITCH_COOLDOWN_MS) {
            return SwitchAttemptResult(
                attempted = false,
                userMessage = "Vui lòng đợi 60 giây trước khi chuyển mạng lại"
            )
        }

        if (repository.isRootAvailable()) {
            repository.syncPasswordsFromSystem(forceRefresh = false)
        }

        val scanResults = repository.scanNearbyNetworks(forceRefresh = enforceCooldown)
        val currentAp = findCurrentAp(scanResults, currentState)
        val on24Ghz = isCurrentlyOn24Ghz(currentState, currentAp)

        val candidates = scanResults.filter { ap ->
            isEligibleCandidate(ap, currentState, on24Ghz) &&
                (!ap.isSharedPasswordRejected || repository.hasSavedWifiPassword(ap.ssid))
        }

        if (candidates.isEmpty()) {
            val message = if (repository.isPrefer5GhzEnabled() && on24Ghz) {
                "Không có mạng 5 GHz tin cậy phù hợp để chuyển"
            } else {
                "Không tìm thấy mạng tin cậy mạnh hơn (+10%)"
            }
            return SwitchAttemptResult(attempted = false, userMessage = message)
        }

        repository.setLastSwitchAtMs(now)

        val best = candidates.sortedWith(
            compareByDescending<WifiApInfo> { it.is5GHz }
                .thenByDescending { it.signalPercent }
                .thenByDescending { it.frequencyMhz }
                .thenBy { it.ssid.lowercase(Locale.getDefault()) }
        ).first()

        val savedPassword = repository.resolveConnectionPassword(best.ssid, bssid = best.bssid)
        val connectResult = repository.connectToNetwork(
            ssid = best.ssid,
            password = savedPassword,
            securityHint = best.securityType,
            bssid = best.bssid
        )
        var success = connectResult.success
        var failureReason: String? = if (!success) connectResult.message else null

        if (!success && !repository.isRootAvailable()) {
            onNotifyUser?.invoke(currentState, best)
        }

        val finalState = repository.getCurrentConnectionState()
        val connectionStatus = ConnectionStatusFormatter.fromState(finalState)

        val sdf = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
        repository.addHistoryLog(
            SwitchLog(
                timestamp = sdf.format(Date()),
                fromSsid = currentState.ssid,
                fromSignal = currentState.signalPercent,
                toSsid = best.ssid,
                toSignal = best.signalPercent,
                isSuccess = success,
                failureReason = failureReason,
                connectionStatus = connectionStatus
            )
        )

        val bandLabel = if (best.is5GHz) "5 GHz" else "2.4 GHz"
        val userMessage = if (success) {
            "Đã chuyển sang ${best.ssid} ($bandLabel, ${best.signalPercent}%)"
        } else {
            failureReason ?: "Không chuyển được sang ${best.ssid}"
        }

        return SwitchAttemptResult(attempted = true, success = success, userMessage = userMessage)
    }

    /** Có thể nâng cấp lên 5 GHz không (đang 2.4G + có mạng 5G tin cậy). */
    fun canUpgradeTo5Ghz(currentState: WifiConnectionState, scanResults: List<WifiApInfo>): Boolean {
        if (!currentState.isConnected || !repository.isPrefer5GhzEnabled()) return false
        if (!isCurrentlyOn24Ghz(currentState, findCurrentAp(scanResults, currentState))) return false
        return scanResults.any { ap ->
            isEligibleCandidate(ap, currentState, on24Ghz = true)
        }
    }

    private fun findCurrentAp(scanResults: List<WifiApInfo>, currentState: WifiConnectionState): WifiApInfo? {
        val bySsid = scanResults.filter { repository.ssidsMatch(it.ssid, currentState.ssid) }
        if (bySsid.isNotEmpty()) {
            return bySsid.maxByOrNull { it.signalPercent }
        }
        val bssid = currentState.bssid
        if (bssid.isNotBlank() && !bssid.equals("02:00:00:00:00:00", ignoreCase = true)) {
            scanResults.find { it.bssid.equals(bssid, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun isCurrentlyOn24Ghz(currentState: WifiConnectionState, currentAp: WifiApInfo?): Boolean {
        if (currentState.frequencyMhz >= 4900) return false
        if (currentState.frequencyMhz in 2400..2500) return true
        if (currentAp != null) return !currentAp.is5GHz
        val ssid = currentState.ssid.lowercase(Locale.getDefault())
        return !ssid.contains("5g")
    }

    private fun isTrustedForSwitch(targetSsid: String, currentSsid: String): Boolean {
        return repository.isSsidAllowed(targetSsid) ||
            repository.areRelatedSsids(currentSsid, targetSsid) ||
            repository.hasConnectableCredential(targetSsid)
    }

    private fun isEligibleCandidate(
        ap: WifiApInfo,
        currentState: WifiConnectionState,
        on24Ghz: Boolean
    ): Boolean {
        val sameAp = ap.bssid.equals(currentState.bssid, ignoreCase = true) &&
            currentState.bssid.isNotBlank() &&
            !currentState.bssid.equals("02:00:00:00:00:00", ignoreCase = true)
        if (sameAp) return false

        if (!isTrustedForSwitch(ap.ssid, currentState.ssid)) return false

        if (repository.isPrefer5GhzEnabled() && on24Ghz && ap.is5GHz) {
            return ap.signalPercent >= currentState.signalPercent - SIGNAL_MARGIN_FOR_5GHZ_UPGRADE
        }

        return ap.signalPercent > currentState.signalPercent + MIN_SIGNAL_GAIN_FOR_SWITCH
    }

    private fun resolveFailureReason(
        suggestionOk: Boolean,
        needsPassword: Boolean,
        savedPassword: String?,
        hasSystemSavedProfile: Boolean,
        rootFailed: Boolean
    ): String {
        return when {
            !suggestionOk && rootFailed -> "Root lỗi và không đăng ký được gợi ý mạng"
            !suggestionOk -> "Không đăng ký được gợi ý mạng với hệ thống"
            needsPassword && savedPassword.isNullOrBlank() && !hasSystemSavedProfile ->
                "Mạng đích cần mật khẩu. Hãy lưu mật khẩu trong tab Quét WiFi"
            !repository.getCurrentConnectionState().isConnected ->
                "Thiết bị chưa kết nối WiFi sau khi gửi yêu cầu"
            rootFailed -> "Root lỗi, hệ thống cũng chưa chuyển sang mạng mục tiêu"
            else -> "Hệ thống chưa chuyển sang mạng mục tiêu"
        }
    }

    private suspend fun verifySwitchedToTarget(target: WifiApInfo): Boolean {
        repeat(8) {
            delay(1500)
            val latestState = repository.getCurrentConnectionState()
            if (latestState.isConnected &&
                (latestState.bssid.equals(target.bssid, ignoreCase = true) ||
                    repository.ssidsMatch(latestState.ssid, target.ssid))
            ) {
                return true
            }
        }
        return false
    }
}
