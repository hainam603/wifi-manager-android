package com.antigravity.wifimanager.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WifiRepository(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_switcher_prefs", Context.MODE_PRIVATE)
    private val activeSuggestions = mutableListOf<WifiNetworkSuggestion>()
    private var lastScanAtMs = 0L
    private var lastScanResults: List<WifiApInfo> = emptyList()
    private var lastScanUsedCache = false
    private var lastSystemPasswordSyncAtMs = 0L
    private var rootAvailabilityCache: Boolean? = null
    private var cachedSavedPasswords: Map<String, String>? = null
    private val sharedWifiRepository = SharedWifiRepository(context)
    private val recentlyForgottenSsids = java.util.concurrent.ConcurrentHashMap<String, Long>()

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_AUTO_SWITCH = "auto_switch"
        private const val KEY_PREFER_5GHZ = "prefer_5ghz"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_HISTORY = "history_logs"
        private const val KEY_SAVED_PASSWORDS = "saved_wifi_passwords"
        private const val KEY_SYSTEM_CONNECTED_SSIDS = "system_connected_ssids"
        private const val KEY_APP_MANAGED_SSIDS = "app_managed_ssids"
        private const val KEY_LAST_SWITCH_AT_MS = "last_switch_at_ms"
        private const val KEY_AUTO_UPDATE_INTERVAL_DAYS = "offline_auto_update_interval_days"
        private const val KEY_LAST_AUTO_UPDATE_MS = "offline_last_auto_update_ms"
        const val SCAN_CACHE_WINDOW_MS = 30_000L
        const val SYSTEM_PASSWORD_SYNC_WINDOW_MS = 120_000L
        const val SWITCH_COOLDOWN_MS = 60_000L
        const val DEFAULT_AUTO_UPDATE_INTERVAL_DAYS = 1
    }

    fun isMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_MONITORING_ENABLED, true)

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun getLastSwitchAtMs(): Long = prefs.getLong(KEY_LAST_SWITCH_AT_MS, 0L)

    fun setLastSwitchAtMs(atMs: Long) {
        prefs.edit().putLong(KEY_LAST_SWITCH_AT_MS, atMs).apply()
    }

    private data class SystemWifiConfig(
        val ssid: String,
        val psk: String,
        val hasEverConnected: Boolean
    )

    // Lấy/Ghi ngưỡng tín hiệu yếu (%)
    fun getThreshold(): Int = prefs.getInt(KEY_THRESHOLD, 80)
    fun setThreshold(value: Int) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    // Bật/Tắt chế độ tự động chuyển mạng
    fun isAutoSwitchEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SWITCH, true)
    fun setAutoSwitchEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SWITCH, value).apply()

    fun isPrefer5GhzEnabled(): Boolean = prefs.getBoolean(KEY_PREFER_5GHZ, true)
    fun setPrefer5GhzEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_PREFER_5GHZ, value).apply()

    // --- Cấu hình tự động cập nhật dữ liệu WiFi offline ---

    /** Chu kỳ tự động cập nhật (ngày). 0 = tắt tự động. */
    fun getAutoUpdateIntervalDays(): Int =
        prefs.getInt(KEY_AUTO_UPDATE_INTERVAL_DAYS, DEFAULT_AUTO_UPDATE_INTERVAL_DAYS)

    fun setAutoUpdateIntervalDays(days: Int) {
        prefs.edit().putInt(KEY_AUTO_UPDATE_INTERVAL_DAYS, days.coerceAtLeast(0)).apply()
    }

    fun getLastAutoUpdateMs(): Long = prefs.getLong(KEY_LAST_AUTO_UPDATE_MS, 0L)

    fun setLastAutoUpdateMs(atMs: Long) {
        prefs.edit().putLong(KEY_LAST_AUTO_UPDATE_MS, atMs).apply()
    }

    /**
     * Chạy prefetch offline vùng địa lý hiện tại.
     * Dùng bởi WifiAutoScheduleReceiver (AlarmManager) và nút cập nhật thủ công.
     */
    suspend fun runOfflinePrefetch() {
        sharedWifiRepository.prefetchArea()
        setLastAutoUpdateMs(System.currentTimeMillis())
    }

    private fun calculateSignalPercent(rssi: Int): Int {
        val minRssi = -100
        val maxRssi = -55

        if (rssi <= minRssi) return 0
        if (rssi >= maxRssi) return 100

        val percent = ((rssi - minRssi) * 100f / (maxRssi - minRssi)).toInt()
        return percent.coerceIn(0, 100)
    }

    // Lấy thông tin trạng thái mạng WiFi hiện đang kết nối
    @SuppressLint("HardwareIds")
    fun getCurrentConnectionState(): WifiConnectionState {
        // Tìm bất kỳ mạng WiFi nào đang hoạt động trong hệ thống (kể cả không có Internet)
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { net ->
            val caps = connectivityManager.getNetworkCapabilities(net)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: connectivityManager.activeNetwork ?: return WifiConnectionState()

        val capabilities = connectivityManager.getNetworkCapabilities(wifiNetwork) ?: return WifiConnectionState()
        
        // Nếu hệ thống xác nhận đây là mạng kết nối qua WiFi thì chắc chắn đang kết nối
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiConnectionState()
        }


        // Lấy thông tin WifiInfo từ transportInfo (Android 12+) hoặc connectionInfo (Android 11 trở xuống)
        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
        val finalWifiInfo = if (transportWifiInfo != null && !WifiCredentialKeys.isPlaceholderBssid(transportWifiInfo.bssid)) {
            transportWifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        if (finalWifiInfo == null) {
            return WifiConnectionState(
                ssid = "Mạng WiFi",
                signalPercent = 85, // Sóng dự phòng hợp lý khi kết nối hoạt động
                authType = "WPA2/WPA3 Personal",
                isConnected = true
            )
        }

        // Loại bỏ dấu nháy kép thừa trong SSID
        var ssid = finalWifiInfo.ssid ?: ""
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }

        val bssid = finalWifiInfo.bssid ?: ""
        val rssi = finalWifiInfo.rssi
        var percent = calculateSignalPercent(rssi)

        // Dự phòng khi giá trị RSSI bị ẩn/không hợp lệ (-127) từ hệ thống
        if (percent <= 0 || rssi == -127) {
            @Suppress("DEPRECATION")
            val fallbackWifiInfo = wifiManager.connectionInfo
            if (fallbackWifiInfo != null && fallbackWifiInfo.rssi != -127) {
                percent = calculateSignalPercent(fallbackWifiInfo.rssi)
            } else {
                percent = 90 // Sóng mặc định khỏe nếu trạng thái hệ thống đang kết nối WiFi
            }
        }

        // Sửa lỗi SSID bị ẩn thành <unknown ssid>
        if (ssid == "<unknown ssid>" || ssid.isEmpty()) {
            @Suppress("DEPRECATION")
            val fallbackWifiInfo = wifiManager.connectionInfo
            if (fallbackWifiInfo != null && fallbackWifiInfo.ssid != null && fallbackWifiInfo.ssid != "<unknown ssid>") {
                ssid = fallbackWifiInfo.ssid
                if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length - 1)
                }
            } else {
                ssid = "Đang kết nối WiFi"
            }
        }

        // Tự động thêm mạng đang kết nối thành công vào danh sách mạng tin cậy để đề xuất
        addAllowedSsid(ssid)

        val frequencyMhz = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finalWifiInfo.frequency
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }

        val (ipAddress, dnsServers) = readActiveNetworkLinkInfo(wifiNetwork)

        return enrichConnectionFromScan(
            WifiConnectionState(
                ssid = ssid,
                bssid = bssid,
                signalPercent = percent,
                authType = formatSecurityLabel(scanResultsSecurityForSsid(ssid)).ifBlank { "WPA2/WPA3" },
                isConnected = true,
                frequencyMhz = frequencyMhz,
                ipAddress = ipAddress,
                dnsServers = dnsServers
            )
        )
    }

    private fun readActiveNetworkLinkInfo(activeNetwork: android.net.Network): Pair<String, String> {
        return try {
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return "" to ""
            val ip = linkProperties.linkAddresses
                .firstOrNull { it.address is Inet4Address }
                ?.address?.hostAddress
                ?: linkProperties.linkAddresses.firstOrNull()?.address?.hostAddress
                ?: ""
            val dns = linkProperties.dnsServers
                .mapNotNull { it.hostAddress?.takeIf { addr -> addr.isNotBlank() } }
                .joinToString("\n")
            ip to dns
        } catch (_: Exception) {
            "" to ""
        }
    }

    /**
     * Cập nhật trạng thái kết nối hiện tại; tùy chọn quét trước để lấy BSSID/RSSI thật từ scanResults.
     */
    @SuppressLint("MissingPermission")
    fun refreshCurrentConnectionFromEnvironment(forceScan: Boolean = false): WifiConnectionState {
        if (forceScan || lastScanResults.isEmpty() ||
            System.currentTimeMillis() - lastScanAtMs > SCAN_CACHE_WINDOW_MS
        ) {
            scanNearbyNetworks(forceRefresh = forceScan)
        }
        return getCurrentConnectionState()
    }

    private fun enrichConnectionFromScan(state: WifiConnectionState): WifiConnectionState {
        if (!state.isConnected || state.ssid.isBlank()) return state

        val candidates = lastScanResults.filter { ssidsMatch(it.ssid, state.ssid) }
        if (candidates.isEmpty()) return state

        val best = selectConnectedApCandidate(candidates, state.frequencyMhz) ?: return state

        var updated = state
        if (WifiCredentialKeys.isPlaceholderBssid(state.bssid) &&
            WifiCredentialKeys.isValidBssid(best.bssid)
        ) {
            updated = updated.copy(bssid = best.bssid)
        }
        if (best.signalPercent > 0) {
            updated = updated.copy(signalPercent = best.signalPercent)
        }
        if (state.frequencyMhz <= 0 && best.frequencyMhz > 0) {
            updated = updated.copy(frequencyMhz = best.frequencyMhz)
        }
        val securityLabel = formatSecurityLabel(best.securityType)
        if (securityLabel.isNotBlank()) {
            updated = updated.copy(authType = securityLabel)
        }
        return updated
    }

    private fun selectConnectedApCandidate(
        candidates: List<WifiApInfo>,
        connectedFrequencyMhz: Int
    ): WifiApInfo? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val on5Ghz = connectedFrequencyMhz >= 4900
        val on24Ghz = connectedFrequencyMhz in 2400..2500

        val bandFiltered = when {
            on5Ghz -> candidates.filter { it.is5GHz }.ifEmpty { candidates }
            on24Ghz -> candidates.filter { !it.is5GHz }.ifEmpty { candidates }
            else -> candidates
        }
        return bandFiltered.maxByOrNull { it.signalPercent }
    }

    private fun formatSecurityLabel(capabilities: String): String {
        val cap = capabilities.trim()
        if (cap.isBlank() || cap.equals("Open", ignoreCase = true)) return "Mở"
        return when {
            cap.contains("SAE", ignoreCase = true) || cap.contains("WPA3", ignoreCase = true) ->
                "WPA3"
            cap.contains("WPA2", ignoreCase = true) -> "WPA2"
            cap.contains("WPA", ignoreCase = true) -> "WPA/WPA2"
            else -> cap.take(40)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanResultsSecurityForSsid(ssid: String?): String {
        if (ssid.isNullOrBlank()) return ""
        return wifiManager.scanResults
            ?.firstOrNull { it.SSID == ssid }
            ?.capabilities
            .orEmpty()
    }

    fun normalizeSsidKey(ssid: String): String {
        return ssid.lowercase()
            .replace(Regex("[\\s\\-_]"), "")
            .replace("5g", "")
            .replace("2.4g", "")
            .replace("24g", "")
    }

    /** Cùng nhà mạng / router (vd. HaiNam ↔ HAINAM 5G LAU 1). */
    fun areRelatedSsids(first: String, second: String): Boolean {
        if (ssidsMatch(first, second)) return true
        val a = normalizeSsidKey(first)
        val b = normalizeSsidKey(second)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val minLen = 4
        return a.length >= minLen && b.length >= minLen && (a.startsWith(b) || b.startsWith(a))
    }

    // --- QUẢN LÝ DANH SÁCH MẠNG ĐÃ LƯU / TIN CẬY ĐỂ ĐỀ XUẤT ---
    fun getAllowedSsids(): Set<String> {
        return prefs.getStringSet("allowed_ssids", emptySet()) ?: emptySet()
    }

    fun addAllowedSsid(ssid: String) {
        if (ssid.isEmpty() || ssid == "<unknown ssid>" || ssid == "Đang kết nối WiFi" || ssid == "Mạng WiFi") return
        val set = getAllowedSsids().toMutableSet()
        if (set.add(ssid)) {
            prefs.edit().putStringSet("allowed_ssids", set).apply()
        }
    }

    fun removeAllowedSsid(ssid: String) {
        val set = getAllowedSsids().toMutableSet()
        if (set.remove(ssid)) {
            prefs.edit().putStringSet("allowed_ssids", set).apply()
        }
    }

    fun isSsidAllowed(ssid: String): Boolean {
        return getAllowedSsids().contains(ssid)
    }

    fun toggleAllowedSsid(ssid: String) {
        if (isSsidAllowed(ssid)) {
            removeAllowedSsid(ssid)
            if (!hasSavedWifiPassword(ssid)) {
                unmarkAppManaged(ssid)
            }
        } else {
            addAllowedSsid(ssid)
            markAppManaged(ssid)
        }
    }

    private fun markAppManaged(ssid: String) {
        if (ssid.isBlank()) return
        val set = (prefs.getStringSet(KEY_APP_MANAGED_SSIDS, emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(ssid)) {
            prefs.edit().putStringSet(KEY_APP_MANAGED_SSIDS, set).apply()
        }
    }

    private fun unmarkAppManaged(ssid: String) {
        val set = (prefs.getStringSet(KEY_APP_MANAGED_SSIDS, emptySet()) ?: emptySet()).toMutableSet()
        if (set.remove(ssid)) {
            prefs.edit().putStringSet(KEY_APP_MANAGED_SSIDS, set).apply()
        }
    }

    private fun isAppManaged(ssid: String): Boolean {
        return prefs.getStringSet(KEY_APP_MANAGED_SSIDS, emptySet())?.contains(ssid) == true
    }

    fun removeSuggestionForSsid(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.util.Log.e("WifiRepository", "removeSuggestionForSsid: target SSID='$ssid'")
                
                // 1. Dùng API chính thức của WifiManager để xóa suggestions thuộc về ứng dụng
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val currentSuggestions = wifiManager.networkSuggestions
                    android.util.Log.e("WifiRepository", "removeSuggestionForSsid: current suggestions size=${currentSuggestions.size}")
                    val targets = currentSuggestions.filter { suggestion ->
                        val cleanSsid = suggestion.ssid?.replace("\"", "").orEmpty()
                        cleanSsid.equals(ssid, ignoreCase = true)
                    }
                    if (targets.isNotEmpty()) {
                        val status = wifiManager.removeNetworkSuggestions(targets)
                        android.util.Log.e("WifiRepository", "removeSuggestionForSsid (API 30+): matched ${targets.size} suggestions, remove status=$status")
                    }
                }
                
                // 2. Dự phòng brute force bằng API (xây dựng lại các cấu hình suggestion có thể có và gửi lệnh xóa)
                val fallbackList = mutableListOf<WifiNetworkSuggestion>()
                val psk = resolveConnectionPassword(ssid)
                
                // Bản WPA2
                try {
                    val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
                    if (!psk.isNullOrBlank()) b.setWpa2Passphrase(psk)
                    fallbackList.add(b.build())
                } catch (_: Exception) {}
                
                // Bản WPA3
                try {
                    val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
                    if (!psk.isNullOrBlank()) b.setWpa3Passphrase(psk)
                    fallbackList.add(b.build())
                } catch (_: Exception) {}
                
                // Bản Open
                try {
                    fallbackList.add(WifiNetworkSuggestion.Builder().setSsid(ssid).build())
                } catch (_: Exception) {}
                
                if (fallbackList.isNotEmpty()) {
                    val statusFallback = wifiManager.removeNetworkSuggestions(fallbackList)
                    android.util.Log.e("WifiRepository", "removeSuggestionForSsid fallback API status=$statusFallback")
                }
            } catch (e: Exception) {
                android.util.Log.e("WifiRepository", "removeSuggestionForSsid API error", e)
            }
            
            // 3. Nếu thiết bị đã Root, chạy thêm lệnh shell để xóa suggestion được thêm bởi shell/hệ thống
            if (isRootAvailable()) {
                try {
                    val qSsid = shellQuote(ssid)
                    val cmd = "cmd wifi remove-suggestion $qSsid; cmd wifi remove-suggestion ${shellQuote("\"" + ssid + "\"")};"
                    android.util.Log.e("WifiRepository", "removeSuggestionForSsid (Root): executing '$cmd'")
                    val res = execRootShellScript(cmd)
                    android.util.Log.e("WifiRepository", "removeSuggestionForSsid (Root) result=$res")
                } catch (e: Exception) {
                    android.util.Log.e("WifiRepository", "removeSuggestionForSsid Root error", e)
                }
            }
        }
    }

    /** Xóa hoàn toàn mạng khỏi app và khỏi hệ thống qua Root (nếu có). */
    fun forgetNetwork(ssid: String, bssid: String? = null) {
        android.util.Log.e("WifiRepository", "forgetNetwork entry: ssid='$ssid', bssid='$bssid'")
        if (ssid.isNotBlank()) {
            recentlyForgottenSsids[ssid.lowercase(Locale.getDefault())] = System.currentTimeMillis()
        }
        removeSavedWifiPassword(ssid, bssid)
        removeSuggestionForSsid(ssid)
        val stillHasPassword = getSavedWifiPasswords().keys.any { key ->
            WifiCredentialKeys.parseStorageKey(key).first.equals(ssid, ignoreCase = true)
        }
        android.util.Log.e("WifiRepository", "forgetNetwork: stillHasPassword=$stillHasPassword")
        if (!stillHasPassword) {
            removeAllowedSsid(ssid)
            unmarkAppManaged(ssid)
            
            // Xóa khỏi danh sách đã từng kết nối thành công của hệ thống lưu trong app
            val verifiedConnected = (prefs.getStringSet(KEY_SYSTEM_CONNECTED_SSIDS, emptySet()) ?: emptySet()).toMutableSet()
            if (verifiedConnected.remove(ssid)) {
                android.util.Log.e("WifiRepository", "forgetNetwork: removed '$ssid' from KEY_SYSTEM_CONNECTED_SSIDS")
                prefs.edit().putStringSet(KEY_SYSTEM_CONNECTED_SSIDS, verifiedConnected).apply()
            }
        }

        val hasRoot = isRootAvailable()
        android.util.Log.e("WifiRepository", "forgetNetwork: hasRoot=$hasRoot")
        
        // Luôn kiểm tra xem mạng bị xóa có phải mạng đang kết nối hiện tại không
        val currentState = getCurrentConnectionState()
        val isCurrentNetwork = currentState.isConnected && ssidsMatch(currentState.ssid, ssid)
        android.util.Log.e("WifiRepository", "forgetNetwork: isCurrentNetwork=$isCurrentNetwork, currentSsid='${currentState.ssid}'")

        if (hasRoot && ssid.isNotBlank()) {
            try {
                // Lấy danh sách mạng lưu trong hệ thống
                android.util.Log.e("WifiRepository", "forgetNetwork: executing 'cmd wifi list-networks'")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd wifi list-networks"))
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                process.waitFor()
                val exitValue = process.exitValue()
                process.destroy()

                android.util.Log.e("WifiRepository", "forgetNetwork: 'cmd wifi list-networks' exitValue=$exitValue, stdout length=${stdout.length}, stderr='$stderr'")

                val lines = stdout.split("\n")
                for (line in lines) {
                    val parsed = parseSystemNetworkLine(line) ?: continue
                    val parsedSsid = parsed.second
                    val networkId = parsed.first
                    
                    if (parsedSsid.equals(ssid, ignoreCase = true) && networkId != null) {
                        val cmd = "cmd wifi forget-network $networkId"
                        android.util.Log.e("WifiRepository", "forgetNetwork: MATCH! networkId=$networkId, executing '$cmd'")
                        val cmdResult = execRootShellScript(cmd)
                        android.util.Log.e("WifiRepository", "forgetNetwork: '$cmd' result=$cmdResult")
                    }
                }
                
                // Nếu là mạng đang kết nối, chúng ta ngắt kết nối và tắt/bật WiFi để làm mới cache hệ thống
                if (isCurrentNetwork) {
                    android.util.Log.e("WifiRepository", "forgetNetwork: Connected to target network. Forcing root disconnect cycle.")
                    try {
                        wifiManager.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("WifiRepository", "forgetNetwork: wifiManager.disconnect exception", e)
                    }
                    val cycleCmd = "cmd wifi set-wifi-enabled disabled; sleep 0.8; cmd wifi set-wifi-enabled enabled"
                    android.util.Log.e("WifiRepository", "forgetNetwork: executing wifi cycle: '$cycleCmd'")
                    val cycleResult = execRootShellScript(cycleCmd)
                    android.util.Log.e("WifiRepository", "forgetNetwork: cycleResult=$cycleResult")
                }
            } catch (e: Exception) {
                android.util.Log.e("WifiRepository", "forgetNetwork exception during root delete", e)
            }
        } else {
            // Không có root, nhưng nếu là mạng đang kết nối, thử ngắt kết nối thông thường
            if (isCurrentNetwork) {
                try {
                    android.util.Log.e("WifiRepository", "forgetNetwork: No root, trying normal disconnect")
                    wifiManager.disconnect()
                } catch (e: Exception) {
                    android.util.Log.e("WifiRepository", "forgetNetwork: wifiManager.disconnect exception (no root)", e)
                }
            }
        }
    }


    private fun invalidateSavedPasswordCache() {
        cachedSavedPasswords = null
    }

    private fun pruneCorruptedSavedPasswords(): Map<String, String> {
        val loaded = loadSavedWifiPasswordsFromPrefs()
        val cleaned = loaded.filter { (key, value) ->
            val (_, storedBssid) = WifiCredentialKeys.parseStorageKey(key)
            WifiCredentialKeys.isPlausibleWifiPassword(value, storedBssid)
        }
        if (cleaned.size != loaded.size) {
            persistSavedWifiPasswords(cleaned)
        }
        cachedSavedPasswords = cleaned
        return cleaned
    }

    private fun loadSavedWifiPasswordsFromPrefs(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_SAVED_PASSWORDS, "{}") ?: "{}"
        val passwords = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                passwords[key] = obj.optString(key, "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return passwords
    }

    private fun persistSavedWifiPasswords(passwords: Map<String, String>) {
        val obj = JSONObject()
        passwords.forEach { (k, value) -> obj.put(k, value) }
        prefs.edit().putString(KEY_SAVED_PASSWORDS, obj.toString()).apply()
        invalidateSavedPasswordCache()
    }

    fun getSavedWifiPasswords(): Map<String, String> {
        cachedSavedPasswords?.let { return it }
        cachedSavedPasswords = loadSavedWifiPasswordsFromPrefs()
        return cachedSavedPasswords!!
    }

    fun getSavedWifiPassword(ssid: String, bssid: String? = null): String? {
        val passwords = getSavedWifiPasswords()
        if (WifiCredentialKeys.isValidBssid(bssid)) {
            passwords[WifiCredentialKeys.storageKey(ssid, bssid)]
                ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
                ?.let { return it }
        }
        passwords[ssid]
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.let { return it }
        return passwords.entries.firstOrNull { (key, value) ->
            val (storedSsid, storedBssid) = WifiCredentialKeys.parseStorageKey(key)
            storedSsid.equals(ssid, ignoreCase = true) &&
                WifiCredentialKeys.normalizeBssid(storedBssid).isEmpty() &&
                WifiCredentialKeys.isPlausibleWifiPassword(value, bssid)
        }?.value
    }

    fun hasSavedWifiPassword(ssid: String, bssid: String? = null): Boolean {
        return !getSavedWifiPassword(ssid, bssid).isNullOrEmpty()
    }

    fun hasStoredCredential(
        ssid: String,
        currentConnectedSsid: String?,
        isCurrentConnected: Boolean,
        systemSavedSsids: Set<String>? = null
    ): Boolean {
        val isCurrentlyConnected = isCurrentConnected && 
            currentConnectedSsid != null &&
            ssidsMatch(currentConnectedSsid, ssid)

        val allowed = isSsidAllowed(ssid)
        val inSystemSaved = allowed && (systemSavedSsids?.contains(ssid) ?: readSystemSavedSsids().contains(ssid))
        val inSystemConnected = allowed && isSystemConnectedSsid(ssid)

        return hasSavedWifiPassword(ssid) ||
            isCurrentlyConnected ||
            inSystemConnected ||
            inSystemSaved
    }

    fun hasStoredCredential(ssid: String): Boolean {
        val current = getCurrentConnectionState()
        return hasStoredCredential(
            ssid = ssid,
            currentConnectedSsid = current.ssid,
            isCurrentConnected = current.isConnected,
            systemSavedSsids = readSystemSavedSsids()
        )
    }


    fun hasConnectableCredential(ssid: String): Boolean = hasStoredCredential(ssid)

    fun getSharedWifiRepository(): SharedWifiRepository = sharedWifiRepository

    fun refreshSharedWifiFromApi(force: Boolean = false): SharedWifiFetchResult {
        return sharedWifiRepository.refresh(force)
    }

    fun isSharedWifiEnabled(): Boolean = sharedWifiRepository.isEnabled()

    fun setSharedWifiEnabled(enabled: Boolean) = sharedWifiRepository.setEnabled(enabled)

    fun getSharedWifiApiUrl(): String = sharedWifiRepository.getApiUrl()

    fun setSharedWifiApiUrl(url: String) = sharedWifiRepository.setApiUrl(url)

    fun getSharedWifiApiKey(): String = sharedWifiRepository.getApiKey()

    fun setSharedWifiApiKey(key: String) = sharedWifiRepository.setApiKey(key)

    fun isWifiMasterEnabled(): Boolean = sharedWifiRepository.isWifiMasterEnabled()

    fun setWifiMasterEnabled(enabled: Boolean) = sharedWifiRepository.setWifiMasterEnabled(enabled)

    fun isSharedWifiOfflineEnabled(): Boolean = sharedWifiRepository.isOfflineCacheEnabled()

    fun setSharedWifiOfflineEnabled(enabled: Boolean) = sharedWifiRepository.setOfflineCacheEnabled(enabled)

    fun getSharedWifiOfflineCount(): Int = sharedWifiRepository.getOfflineCacheCount()

    fun clearSharedWifiOfflineCache() = sharedWifiRepository.clearOfflineCache()

    fun getSharedWifiOfflineRadiusKm(): Int = sharedWifiRepository.getOfflineRadiusKm()

    fun setSharedWifiOfflineRadiusKm(km: Int) = sharedWifiRepository.setOfflineRadiusKm(km)

    fun getSharedWifiOfflineMaxStorageMb(): Int = sharedWifiRepository.getOfflineMaxStorageMb()

    fun setSharedWifiOfflineMaxStorageMb(mb: Int) = sharedWifiRepository.setOfflineMaxStorageMb(mb)

    fun getSharedWifiOfflineStorageMb(): Double = sharedWifiRepository.getOfflineStorageMb()

    fun getSharedWifiOfflineMaxNetworks(): Int = sharedWifiRepository.getOfflineMaxNetworks()

    suspend fun prefetchSharedWifiArea(onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): SharedWifiPrefetchResult {
        val result = sharedWifiRepository.prefetchArea(onProgress)
        if (result.success) {
            setLastAutoUpdateMs(System.currentTimeMillis())
        }
        return result
    }

    suspend fun lookupPasswordByWifiMasterId(wifiMasterId: Long): WifiIdLookupResult =
        sharedWifiRepository.lookupByWifiMasterId(wifiMasterId)

    suspend fun refreshSharedPasswordForBssid(ssid: String, bssid: String): WifiCredentialRefreshResult =
        sharedWifiRepository.refreshPasswordForBssid(ssid, bssid, this)

    fun isSystemConnectedSsid(ssid: String): Boolean {
        val set = prefs.getStringSet(KEY_SYSTEM_CONNECTED_SSIDS, emptySet()) ?: emptySet()
        return set.contains(ssid)
    }

    fun saveWifiPassword(ssid: String, password: String, bssid: String? = null) {
        if (ssid.isBlank()) return
        if (!WifiCredentialKeys.isPlausibleWifiPassword(password, bssid)) return
        val passwords = getSavedWifiPasswords().toMutableMap()
        val key = WifiCredentialKeys.storageKey(ssid, bssid)
        passwords[key] = password
        if (WifiCredentialKeys.isValidBssid(bssid)) {
            passwords.remove(ssid)
        }
        persistSavedWifiPasswords(passwords)
        addAllowedSsid(ssid)
        markAppManaged(ssid)
    }

    fun removeSavedWifiPassword(ssid: String, bssid: String? = null) {
        val passwords = getSavedWifiPasswords().toMutableMap()
        var changed = false
        
        // 1. Luôn xóa key dạng trần (plain SSID)
        if (passwords.remove(ssid) != null) {
            changed = true
            android.util.Log.e("WifiRepository", "removeSavedWifiPassword: removed plain key '$ssid'")
        }
        
        // 2. Luôn xóa key dạng storageKey(ssid, bssid) nếu bssid hợp lệ
        if (WifiCredentialKeys.isValidBssid(bssid)) {
            val storageKey = WifiCredentialKeys.storageKey(ssid, bssid)
            if (passwords.remove(storageKey) != null) {
                changed = true
                android.util.Log.e("WifiRepository", "removeSavedWifiPassword: removed storageKey '$storageKey'")
            }
        }
        
        // 3. Xóa tất cả các key có SSID khớp (bất kể BSSID nào) để sạch sẽ hoàn toàn
        val keysToRemove = passwords.keys.filter { key ->
            val (storedSsid, _) = WifiCredentialKeys.parseStorageKey(key)
            storedSsid.equals(ssid, ignoreCase = true)
        }
        keysToRemove.forEach { key ->
            if (passwords.remove(key) != null) {
                changed = true
                android.util.Log.e("WifiRepository", "removeSavedWifiPassword: removed matched key '$key'")
            }
        }
        
        if (changed) {
            persistSavedWifiPasswords(passwords)
        }
    }

    private fun parseSystemNetworkLine(line: String): Pair<Int?, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || 
            trimmed.startsWith("Network Id", ignoreCase = true) || 
            trimmed.startsWith("NetworkID", ignoreCase = true) ||
            trimmed.startsWith("Network ID", ignoreCase = true)
        ) {
            return null
        }
        
        // Cắt cột theo chỉ số cố định (fixed width):
        // Network Id: từ đầu đến trước index 13
        // SSID: từ index 13 đến trước index 46 (hoặc hết nếu không đủ dài)
        if (line.length < 14) return null
        
        val idStr = line.substring(0, 13.coerceAtMost(line.length)).trim()
        val networkId = idStr.toIntOrNull()
        
        val ssidStr = if (line.length > 46) {
            line.substring(13, 46)
        } else {
            line.substring(13)
        }.trim()
        
        // Loại bỏ dấu nháy kép bọc ngoài SSID nếu có
        var ssid = ssidStr
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        
        return Pair(networkId, ssid.trim())
    }

    private fun getActiveRecentlyForgottenSsids(): Set<String> {
        val now = System.currentTimeMillis()
        recentlyForgottenSsids.entries.removeIf { now - it.value > 5000L }
        return recentlyForgottenSsids.keys
    }

    private fun readSystemSavedSsidsFromRootCmd(): Set<String> {
        val ssids = mutableSetOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd wifi list-networks"))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            process.destroy()

            val lines = stdout.split("\n")
            for (line in lines) {
                val parsed = parseSystemNetworkLine(line) ?: continue
                if (parsed.second.isNotEmpty()) {
                    ssids.add(parsed.second)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WifiRepository", "readSystemSavedSsidsFromRootCmd exception", e)
        }
        return ssids
    }

    @SuppressLint("MissingPermission")
    private fun readSystemSavedSsids(): MutableSet<String> {
        val systemSsids = mutableSetOf<String>()
        if (isRootAvailable()) {
            val rootSsids = readSystemSavedSsidsFromRootCmd()
            if (rootSsids.isNotEmpty()) {
                systemSsids.addAll(rootSsids)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val configured = wifiManager.configuredNetworks
                if (configured != null) {
                    for (config in configured) {
                        var s = config.SSID ?: continue
                        if (s.startsWith("\"") && s.endsWith("\"")) {
                            s = s.substring(1, s.length - 1)
                        }
                        if (s.isNotEmpty() && s != "<unknown ssid>") {
                            systemSsids.add(s)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Lọc bỏ các SSID vừa bị xóa để tránh bị re-sync do OS lag
        val forgotten = getActiveRecentlyForgottenSsids()
        if (forgotten.isNotEmpty()) {
            systemSsids.removeAll { ssid ->
                forgotten.contains(ssid.lowercase(Locale.getDefault()))
            }
        }
        return systemSsids
    }

    /** Gỡ dữ liệu app cho mạng đã bị xóa khỏi Cài đặt WiFi hệ thống. */
    private fun pruneStaleNetworkData(systemSsids: Set<String>) {
        if (systemSsids.isEmpty()) return

        val connectedSsid = getCurrentConnectionState().ssid.takeIf {
            getCurrentConnectionState().isConnected && it.isNotBlank() &&
                it != "Mạng WiFi" && it != "Đang kết nối WiFi" && it != "<unknown ssid>"
        }

        val verifiedConnected = (prefs.getStringSet(KEY_SYSTEM_CONNECTED_SSIDS, emptySet()) ?: emptySet())
            .toMutableSet()

        val passwords = getSavedWifiPasswords().toMutableMap()
        passwords.keys.toList().forEach { ssid ->
            // Bị xóa khỏi OS (trước đó đã từng kết nối thành công/đã ở trong OS)
            val userDeletedFromOS = ssid !in systemSsids && verifiedConnected.contains(ssid)
            // OS cũ không có quản lý bởi app
            val staleNotManaged = ssid !in systemSsids && !isAppManaged(ssid)

            if ((userDeletedFromOS || staleNotManaged) && ssid != connectedSsid) {
                passwords.remove(ssid)
                unmarkAppManaged(ssid)
            }
        }
        persistSavedWifiPasswords(passwords)

        val allowed = getAllowedSsids().toMutableSet()
        allowed.removeAll { ssid ->
            val userDeletedFromOS = ssid !in systemSsids && verifiedConnected.contains(ssid)
            val staleNotManaged = ssid !in systemSsids && !isAppManaged(ssid)
            (userDeletedFromOS || staleNotManaged) && ssid != connectedSsid
        }

        verifiedConnected.removeAll { ssid ->
            val userDeletedFromOS = ssid !in systemSsids && verifiedConnected.contains(ssid)
            val staleNotManaged = ssid !in systemSsids && !isAppManaged(ssid)
            (userDeletedFromOS || staleNotManaged) && ssid != connectedSsid
        }

        prefs.edit()
            .putStringSet("allowed_ssids", allowed)
            .putStringSet(KEY_SYSTEM_CONNECTED_SSIDS, verifiedConnected)
            .apply()
    }

    private fun parseWifiConfigStore(xmlContent: String): Map<String, SystemWifiConfig> {
        val result = mutableMapOf<String, SystemWifiConfig>()

        val networkBlocks = Regex("<Network>(.*?)</Network>", setOf(RegexOption.DOT_MATCHES_ALL))
        val ssidRegex = Regex("name=\\\"SSID\\\">(?:&quot;|\\\")?(.+?)(?:&quot;|\\\")?</string>")
        val pskRegex = Regex("name=\\\"PreSharedKey\\\">(?:&quot;|\\\")?(.+?)(?:&quot;|\\\")?</string>")
        val connectedRegex = Regex("name=\\\"HasEverConnected\\\" value=\\\"(true|false)\\\"")

        for (match in networkBlocks.findAll(xmlContent)) {
            val block = match.groupValues[1]
            val ssid = ssidRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val psk = pskRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val connectedFlag = connectedRegex.find(block)?.groupValues?.getOrNull(1)
            val hasEverConnected = when (connectedFlag) {
                "true" -> true
                "false" -> false
                else -> psk.isNotBlank()
            }

            if (ssid.isNotBlank()) {
                result[ssid] = SystemWifiConfig(
                    ssid = ssid,
                    psk = psk,
                    hasEverConnected = hasEverConnected
                )
            }
        }

        return result
    }

    private fun readSystemWifiConfigsFromRoot(): Map<String, SystemWifiConfig> {
        try {
            val findProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -la /data/misc/apexdata/com.android.wifi/"))
            val findOut = findProc.inputStream.bufferedReader().use { it.readText() }
            findProc.waitFor()
            android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: ls -la wifi apexdata:\n$findOut")
        } catch (e: Exception) {
            android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: find exception", e)
        }

        val paths = listOf(
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/WifiConfigStore.xml"
        )

        for (path in paths) {
            try {
                android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: trying path $path")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
                val xml = process.inputStream.bufferedReader().use { it.readText() }
                val errorMsg = process.errorStream.bufferedReader().use { it.readText() }
                process.waitFor()
                val exitCode = process.exitValue()
                process.destroy()

                android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: path $path finished with exitCode=$exitCode, xml.length=${xml.length}, error='$errorMsg'")

                if (xml.isNotBlank() && xml.contains("<Network>")) {
                    val parsed = parseWifiConfigStore(xml)
                    android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: parsed ${parsed.size} configs from $path")
                    parsed.forEach { (ssid, cfg) ->
                        android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot parsed SSID: '$ssid', psk.length=${cfg.psk.length}, hasEverConnected=${cfg.hasEverConnected}")
                    }
                    if (parsed.isNotEmpty()) {
                        return parsed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WifiRepository", "readSystemWifiConfigsFromRoot: exception on path $path", e)
            }
        }

        return emptyMap()
    }

    /** Chỉ tin mạng hệ thống đã từng kết nối thành công (hoặc do app quản lý). */
    private fun shouldTrustSystemSavedSsid(ssid: String, configs: Map<String, SystemWifiConfig>): Boolean {
        if (hasSavedWifiPassword(ssid) || isAppManaged(ssid) || isSystemConnectedSsid(ssid)) return true
        val cfg = configs[ssid] ?: return false
        return cfg.hasEverConnected
    }

    fun syncPasswordsFromSystem(forceRefresh: Boolean = false) {
        if (!wifiManager.isWifiEnabled) {
            return
        }

        if (forceRefresh) {
            rootAvailabilityCache = null
        }

        val fromConfigured = readSystemSavedSsids()
        val systemSsids = fromConfigured.toMutableSet()

        val mergedPasswords = getSavedWifiPasswords().toMutableMap()
        val isRoot = isRootAvailable()

        val now = System.currentTimeMillis()
        val shouldSyncRoot = forceRefresh || (now - lastSystemPasswordSyncAtMs >= SYSTEM_PASSWORD_SYNC_WINDOW_MS)

        val rootConfigs = if (isRoot && shouldSyncRoot) {
            readSystemWifiConfigsFromRoot()
        } else {
            emptyMap()
        }

        fromConfigured.forEach { ssid ->
            if (shouldTrustSystemSavedSsid(ssid, rootConfigs)) {
                addAllowedSsid(ssid)
            } else if (!hasSavedWifiPassword(ssid) && !isAppManaged(ssid)) {
                removeAllowedSsid(ssid)
            }
        }

        if (isRoot && shouldSyncRoot) {
            rootConfigs.values.forEach { cfg ->
                if (!cfg.hasEverConnected) return@forEach
                val trustedBySystem = if (isRoot) {
                    cfg.ssid in fromConfigured
                } else {
                    fromConfigured.isEmpty() || cfg.ssid in fromConfigured
                }
                if (!trustedBySystem) return@forEach

                systemSsids.add(cfg.ssid)
                if (cfg.psk.isNotBlank()) {
                    mergedPasswords[cfg.ssid] = cfg.psk
                    addAllowedSsid(cfg.ssid)
                }
            }
            lastSystemPasswordSyncAtMs = now
        }

        persistSavedWifiPasswords(mergedPasswords)
        pruneCorruptedSavedPasswords()

        // Chỉ dọn dữ liệu cũ khi đọc được danh sách hệ thống (tránh xóa nhầm khi API trả rỗng)
        if (fromConfigured.isNotEmpty() || (isRoot && shouldSyncRoot && systemSsids.isNotEmpty())) {
            pruneStaleNetworkData(systemSsids)
        }
    }

    fun getLastScanAtMs(): Long = lastScanAtMs

    fun wasLastScanServedFromCache(): Boolean = lastScanUsedCache

    fun getCachedScanResults(): List<WifiApInfo> = lastScanResults

    // Quét tìm danh sách các mạng WiFi khả dụng xung quanh.
    // Đọc ngay từ wifiManager.scanResults (OS cache) — không blocking, không delay.
    // forceRefresh=true: trigger startScan() để OS làm mới lần sau, nhưng trả về ngay kết quả hiện tại.
    // Kết quả mới nhất sẽ được cập nhật qua SCAN_RESULTS_AVAILABLE_ACTION broadcast trong WifiMonitorService.
    @SuppressLint("MissingPermission")
    fun scanNearbyNetworks(forceRefresh: Boolean = false): List<WifiApInfo> {
        try {
            val now = System.currentTimeMillis()
            // Dùng app cache nếu còn hiệu lực và không force
            if (!forceRefresh && lastScanResults.isNotEmpty() && (now - lastScanAtMs) < SCAN_CACHE_WINDOW_MS) {
                lastScanUsedCache = true
                val current = getCurrentConnectionState()
                val systemSaved = readSystemSavedSsids()
                val updated = lastScanResults.map { ap ->
                    ap.copy(
                        isSaved = isSsidAllowed(ap.ssid),
                        hasStoredPassword = hasStoredCredential(
                            ssid = ap.ssid,
                            currentConnectedSsid = current.ssid,
                            isCurrentConnected = current.isConnected,
                            systemSavedSsids = systemSaved
                        )
                    )
                }
                val enriched = sharedWifiRepository.enrichAccessPoints(updated, this)
                    .sortedWith(
                        compareByDescending<WifiApInfo> { it.isReadyToConnect }
                            .thenByDescending { it.isSaved }
                            .thenByDescending { it.signalPercent }
                            .thenByDescending { it.is5GHz }
                            .thenByDescending { it.hasStoredPassword }
                            .thenBy { it.ssid.lowercase(Locale.getDefault()) }
                    )
                lastScanResults = enriched
                return enriched
            }

            // Đồng bộ nhẹ — không đọc WifiConfigStore.xml mỗi lần quét (rất chậm trên root)
            syncPasswordsFromSystem(forceRefresh = false)

            // Nếu forceRefresh: yêu cầu OS làm mới scan (không chờ kết quả — tránh block UI)
            // OS sẽ broadcast SCAN_RESULTS_AVAILABLE_ACTION khi có kết quả mới
            if (forceRefresh) {
                @Suppress("DEPRECATION")
                wifiManager.startScan() // fire-and-forget, kết quả đến qua broadcast
            }

            // Đọc kết quả hiện tại từ OS ngay lập tức (không block)
            @Suppress("DEPRECATION")
            val rawResults = wifiManager.scanResults.orEmpty()

            if (rawResults.isEmpty()) {
                lastScanUsedCache = true
                return if (lastScanResults.isNotEmpty()) lastScanResults else emptyList()
            }

            // refreshForScan() đã là no-op — mật khẩu chỉ lấy từ offline store (xem enrichAccessPoints)

            val current = getCurrentConnectionState()
            val systemSaved = readSystemSavedSsids()
            val processed = rawResults
                .filter { !it.SSID.isNullOrEmpty() }
                .map {
                    val percent = calculateSignalPercent(it.level)
                    WifiApInfo(
                        ssid = it.SSID,
                        bssid = it.BSSID,
                        signalPercent = percent,
                        frequencyMhz = it.frequency,
                        isSaved = isSsidAllowed(it.SSID),
                        hasStoredPassword = hasStoredCredential(
                            ssid = it.SSID,
                            currentConnectedSsid = current.ssid,
                            isCurrentConnected = current.isConnected,
                            systemSavedSsids = systemSaved
                        ),
                        securityType = it.capabilities ?: "Open"
                    )
                }
            // enrichAccessPoints chỉ dùng offline store — không gọi API online
            val enriched = sharedWifiRepository.enrichAccessPoints(processed, this)
                .sortedWith(
                    compareByDescending<WifiApInfo> { it.isReadyToConnect }
                        .thenByDescending { it.isSaved }
                        .thenByDescending { it.signalPercent }
                        .thenByDescending { it.is5GHz }
                        .thenByDescending { it.hasStoredPassword }
                        .thenBy { it.ssid.lowercase(Locale.getDefault()) }
                )
            lastScanAtMs = now
            lastScanUsedCache = false
            lastScanResults = enriched
            return enriched
        } catch (e: Exception) {
            e.printStackTrace()
            lastScanUsedCache = true
            return lastScanResults
        }
    }

    /**
     * Quét WiFi hệ thống thuần tuý, không kết hợp dữ liệu chia sẻ cộng đồng/API.
     * Trả về danh sách các mạng chỉ với thông tin từ hệ thống.
     */
    @SuppressLint("MissingPermission")
    fun scanSystemOnly(forceRefresh: Boolean = false): List<WifiApInfo> {
        try {
            if (forceRefresh) {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            }

            @Suppress("DEPRECATION")
            val rawResults = wifiManager.scanResults.orEmpty()
            val current = getCurrentConnectionState()
            val systemSaved = readSystemSavedSsids()

            return rawResults
                .filter { !it.SSID.isNullOrEmpty() }
                .map {
                    val percent = calculateSignalPercent(it.level)
                    val isSaves = systemSaved.contains(it.SSID) || isSsidAllowed(it.SSID)
                    val hasStored = hasSavedWifiPassword(it.SSID, it.BSSID) || systemSaved.contains(it.SSID)
                    val isSecured = it.capabilities.contains("WPA", ignoreCase = true) ||
                            it.capabilities.contains("SAE", ignoreCase = true) ||
                            it.capabilities.contains("PSK", ignoreCase = true)
                    
                    WifiApInfo(
                        ssid = it.SSID,
                        bssid = it.BSSID,
                        signalPercent = percent,
                        frequencyMhz = it.frequency,
                        isSaved = isSaves,
                        hasStoredPassword = hasStored,
                        securityType = it.capabilities ?: "Open",
                        isReadyToConnect = hasStored || !isSecured
                    )
                }
                .sortedWith(
                    compareByDescending<WifiApInfo> { current.isConnected && ssidsMatch(current.ssid, it.ssid) }
                        .thenByDescending { it.isSaved }
                        .thenByDescending { it.signalPercent }
                        .thenByDescending { it.is5GHz }
                        .thenBy { it.ssid.lowercase(Locale.getDefault()) }
                )
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun openSystemWifiSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openSystemWifiPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val intent = android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openSystemWifiSettings()
            }
        } else {
            openSystemWifiSettings()
        }
    }



    // Đăng ký danh sách gợi ý mạng với Android để tự động kết nối khi sóng mạng hiện tại yếu
    fun suggestNetworks(ssids: List<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val normalizedSsids = ssids
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                if (normalizedSsids.isEmpty()) {
                    clearNetworkSuggestions()
                    return true
                }

                val suggestions = normalizedSsids.map { ssid ->
                    WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        .setIsAppInteractionRequired(false) // Cho phép hệ thống tự kết nối không cần hỏi ý kiến
                        .build()
                }
                
                // Giải phóng các gợi ý cũ tránh bị trùng lặp/giới hạn
                clearNetworkSuggestions()
                val status = wifiManager.addNetworkSuggestions(suggestions)
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    activeSuggestions.clear()
                    activeSuggestions.addAll(suggestions)
                }
                return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun clearNetworkSuggestions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeSuggestions.isNotEmpty()) {
            try {
                wifiManager.removeNetworkSuggestions(activeSuggestions.toList())
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                activeSuggestions.clear()
            }
        }
    }

    // Quản lý Nhật ký Lịch sử chuyển mạng trong SharedPreferences (lưu trữ JSON gọn nhẹ)
    fun getHistoryLogs(): List<SwitchLog> {
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<SwitchLog>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SwitchLog(
                        timestamp = obj.getString("time"),
                        fromSsid = obj.getString("from_ssid"),
                        fromSignal = obj.getInt("from_signal"),
                        toSsid = obj.getString("to_ssid"),
                        toSignal = obj.getInt("to_signal"),
                        isSuccess = obj.getBoolean("success"),
                        failureReason = obj.optString("failure_reason", "").ifBlank { null },
                        connectionStatus = obj.optString("connection_status", "").ifBlank { null }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistoryLog(log: SwitchLog) {
        val list = getHistoryLogs().toMutableList()
        list.add(0, log) // Đưa lên đầu danh sách
        val truncated = list.take(50) // Giới hạn tối đa 50 dòng log
        
        val arr = JSONArray()
        for (item in truncated) {
            val obj = JSONObject()
            obj.put("time", item.timestamp)
            obj.put("from_ssid", item.fromSsid)
            obj.put("from_signal", item.fromSignal)
            obj.put("to_ssid", item.toSsid)
            obj.put("to_signal", item.toSignal)
            obj.put("success", item.isSuccess)
            obj.put("failure_reason", item.failureReason ?: "")
            obj.put("connection_status", item.connectionStatus ?: "")
            arr.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply()
    }

    // --- KÍCH HOẠT HỘP THOẠI YÊU CẦU KẾT NỐI NHANH (Android 10+) ---
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun connectToSsidViaSpecifier(ssid: String, password: String? = null, securityHint: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                releaseSpecifierConnection()

                val builder = android.net.wifi.WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)

                val psk = resolveConnectionPassword(ssid, password)
                if (!psk.isNullOrBlank()) {
                    applyPassphraseToSpecifier(builder, psk, securityHint)
                }

                val specifier = builder.build()

                val request = android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        super.onAvailable(network)
                        connectivityManager.bindProcessToNetwork(network)
                    }
                }
                activeNetworkCallback = callback
                connectivityManager.requestNetwork(request, callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyPassphraseToSpecifier(
        builder: android.net.wifi.WifiNetworkSpecifier.Builder,
        password: String,
        securityHint: String?
    ) {
        val types = securityTypesFromHint(securityHint)
        if (types.contains("wpa3") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                builder.setWpa3Passphrase(password)
                return
            } catch (_: Exception) {
                // Thiết bị không hỗ trợ WPA3 specifier — thử WPA2
            }
        }
        builder.setWpa2Passphrase(password)
    }

    fun suggestNetworkWithPassword(ssid: String, password: String?, securityHint: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val normalized = ssid.trim()
            if (normalized.isEmpty()) return false

            val builder = WifiNetworkSuggestion.Builder()
                .setSsid(normalized)
                .setIsAppInteractionRequired(false)

            val psk = resolveConnectionPassword(normalized, password)
            if (!psk.isNullOrBlank()) {
                val types = securityTypesFromHint(securityHint)
                if (types.contains("wpa3") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        builder.setWpa3Passphrase(psk)
                    } catch (_: Exception) {
                        builder.setWpa2Passphrase(psk)
                    }
                } else {
                    builder.setWpa2Passphrase(psk)
                }
            }

            clearNetworkSuggestions()
            val suggestion = builder.build()
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                activeSuggestions.clear()
                activeSuggestions.add(suggestion)
            }
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class ConnectResult(
        val success: Boolean,
        val message: String
    )

    /** Kết nối im lặng (không hộp thoại): chỉ ROOT + xác minh kết quả. */
    fun connectToNetwork(
        ssid: String,
        password: String? = null,
        securityHint: String? = null,
        bssid: String? = null
    ): ConnectResult {
        val requiresPassword = requiresPasswordFromHint(securityHint)
        val psk = resolveConnectionPassword(ssid, password, bssid)
        val hasSystemProfile = isSystemConnectedSsid(ssid)
        val sharedCred = sharedWifiRepository.findSharedCredential(ssid, bssid, this)
        val sharedApiPassword = sharedCred?.password
            ?: sharedWifiRepository.resolvePassword(ssid, bssid, this)

        if (requiresPassword && psk.isNullOrBlank() && !hasSystemProfile) {
            if (sharedCred != null &&
                sharedWifiRepository.isPasswordRejected(ssid, bssid, sharedCred.password)
            ) {
                return ConnectResult(
                    success = false,
                    message = "Mật khẩu API cho '$ssid' đã thử thất bại.\nChờ API cập nhật mật khẩu mới."
                )
            }
            return ConnectResult(
                success = false,
                message = "Không có mật khẩu cho '$ssid'.\nQuét lại hoặc nhập thủ công (icon bút chỉnh)."
            )
        }

        if (!isRootAvailable()) {
            return ConnectResult(
                success = false,
                message = "Cần quyền Root để kết nối im lặng.\nVào Cấu hình → Yêu cầu Root."
            )
        }

        val existedInSystemBefore = readSystemSavedSsids().contains(ssid)
        connectToSsidViaRoot(ssid, psk, securityHint)

        return if (waitUntilConnectedTo(ssid, timeoutMs = 12_000)) {
            if (!psk.isNullOrBlank()) {
                saveWifiPassword(ssid, psk, bssid)
            }
            markSystemConnectedSsid(ssid)
            suggestNetworkWithPassword(ssid, psk, securityHint)
            ConnectResult(
                success = true,
                message = "Kết nối thành công: $ssid"
            )
        } else {
            if (!existedInSystemBefore) {
                forgetNetworkFromSystemViaRoot(ssid)
            }
            val attemptedPassword = psk.orEmpty()
            val fromSharedApi = attemptedPassword.isNotBlank() &&
                sharedWifiRepository.matchesSharedApiPassword(
                    ssid,
                    bssid ?: sharedCred?.bssid,
                    attemptedPassword,
                    this
                )
            if (fromSharedApi) {
                val rejectPassword = sharedCred?.password ?: sharedApiPassword ?: attemptedPassword
                sharedWifiRepository.markPasswordRejected(
                    ssid,
                    bssid ?: sharedCred?.bssid,
                    rejectPassword
                )
                if (getSavedWifiPassword(ssid, bssid ?: sharedCred?.bssid) == attemptedPassword) {
                    removeSavedWifiPassword(ssid, bssid ?: sharedCred?.bssid)
                }
            }
            ConnectResult(
                success = false,
                message = if (fromSharedApi) {
                    "Kết nối thất bại: $ssid\n" +
                        "Mật khẩu API không đúng — sẽ không thử lại cho đến khi API cập nhật mật khẩu mới."
                } else {
                    "Kết nối thất bại: $ssid\n" +
                        "Có thể mật khẩu đã đổi hoặc sóng quá yếu. Thử lại hoặc sửa mật khẩu (icon bút)."
                }
            )
        }
    }

    private fun requiresPasswordFromHint(securityHint: String?): Boolean {
        val hint = securityHint.orEmpty()
        return hint.contains("WPA", ignoreCase = true) ||
            hint.contains("SAE", ignoreCase = true) ||
            hint.contains("PSK", ignoreCase = true)
    }

    private fun waitUntilConnectedTo(ssid: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = getCurrentConnectionState()
            if (state.isConnected && ssidsMatch(state.ssid, ssid)) {
                return true
            }
            Thread.sleep(400)
        }
        return false
    }

    private fun markSystemConnectedSsid(ssid: String) {
        val set = prefs.getStringSet(KEY_SYSTEM_CONNECTED_SSIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(ssid)
        prefs.edit().putStringSet(KEY_SYSTEM_CONNECTED_SSIDS, set).apply()
    }

    /** Gỡ profile WiFi hệ thống do lần thử kết nối vừa tạo (khi thất bại). */
    private fun forgetNetworkFromSystemViaRoot(ssid: String) {
        if (!isRootAvailable() || ssid.isBlank()) return
        try {
            val qSsid = shellQuote(ssid.trim())
            execRootShellScript("cmd wifi forget-network $qSsid;")
        } catch (_: Exception) {
            // Bỏ qua nếu thiết bị không hỗ trợ forget-network
        }
    }

    fun releaseSpecifierConnection() {
        activeNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            connectivityManager.bindProcessToNetwork(null)
            activeNetworkCallback = null
        }
    }

    // --- HỖ TRỢ CHẾ ĐỘ ROOT (TỰ ĐỘNG CHUYỂN MẠNG IM LẶNG 100%) ---
    private fun rootProbeCommands(): List<Array<String>> = listOf(
        arrayOf("su", "-c", "id -u"),
        arrayOf("/system/bin/su", "-c", "id -u"),
        arrayOf("/system/xbin/su", "-c", "id -u"),
        arrayOf("/sbin/su", "-c", "id -u")
    )

    fun getRootStatus(): RootStatus {
        rootAvailabilityCache = null
        var suResponded = false
        for (cmd in rootProbeCommands()) {
            try {
                val process = Runtime.getRuntime().exec(cmd)
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                process.destroy()
                suResponded = true
                if (exitCode == 0 && (output == "0" || output.contains("root") || output.contains("uid=0"))) {
                    rootAvailabilityCache = true
                    return RootStatus.GRANTED
                }
            } catch (_: Exception) {
                // Thử đường dẫn su tiếp theo
            }
        }
        rootAvailabilityCache = false
        return if (suResponded) RootStatus.DENIED else RootStatus.UNAVAILABLE
    }

    /** Kích hoạt hộp thoại Magisk/SuperSU cấp quyền cho app. */
    fun requestRootAccess(): Boolean {
        rootAvailabilityCache = null
        return getRootStatus() == RootStatus.GRANTED
    }

    fun isRootAvailable(): Boolean {
        if (rootAvailabilityCache == true) return true
        val status = getRootStatus()
        if (status == RootStatus.GRANTED) {
            rootAvailabilityCache = true
            return true
        }
        return false
    }


    fun ssidsMatch(current: String, target: String): Boolean {
        return current.trim().equals(target.trim(), ignoreCase = true)
    }

    /** Gộp mật khẩu từ app, mạng tương tự, hoặc offline store — không gọi API online. */
    fun resolveConnectionPassword(
        ssid: String,
        explicitPassword: String? = null,
        bssid: String? = null
    ): String? {
        explicitPassword?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.let { return it }
        getSavedWifiPassword(ssid, bssid)?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.takeUnless { sharedWifiRepository.isPasswordRejected(ssid, bssid, it) }
            ?.let { return it }
        getSimilarSsidWithSavedPassword(ssid)?.second?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.let { return it }
        // Chỉ tra từ offline store — không gọi API online
        sharedWifiRepository.resolvePasswordOfflineOnly(ssid, bssid, this)
            ?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.let { return it }
        return null
    }

    /**
     * Resolve mật khẩu để HIỂN THỊ (UI):
     * - Ưu tiên mật khẩu user đã lưu trong app (kể cả dạng key SSID|BSSID)
     * - Sau đó mật khẩu "mạng tương tự" đã lưu trong app
     * - Cuối cùng là mật khẩu từ offline store WiFi cộng đồng
     *
     * Khác với resolveConnectionPassword(): hàm này KHÔNG lọc rejected/plausible để tránh "có mà không show".
     * (Logic kết nối vẫn dùng resolveConnectionPassword()).
     */
    fun resolvePasswordForDisplay(ssid: String, bssid: String? = null): String? {
        if (ssid.isBlank()) return null

        // 1) Password user lưu trong app (không filter plausible)
        val saved = getSavedWifiPasswordUnsafe(ssid, bssid)?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved

        // 2) Password từ mạng tương tự (đã lưu trong app)
        val similar = getSimilarSsidWithSavedPassword(ssid)?.second?.trim().orEmpty()
        if (similar.isNotEmpty()) return similar

        // 3) Password từ offline store WiFi cộng đồng (không filter rejected)
        val shared = sharedWifiRepository.getSharedPassword(ssid, bssid)?.trim().orEmpty()
        if (shared.isNotEmpty()) return shared

        return null
    }

    private fun getSavedWifiPasswordUnsafe(ssid: String, bssid: String? = null): String? {
        val passwords = getSavedWifiPasswords()
        if (WifiCredentialKeys.isValidBssid(bssid)) {
            passwords[WifiCredentialKeys.storageKey(ssid, bssid)]?.let { return it }
        }
        passwords[ssid]?.let { return it }
        return passwords.entries.firstOrNull { (key, _) ->
            val (storedSsid, storedBssid) = WifiCredentialKeys.parseStorageKey(key)
            storedSsid.equals(ssid, ignoreCase = true) &&
                (WifiCredentialKeys.normalizeBssid(storedBssid).isEmpty() || !WifiCredentialKeys.isValidBssid(bssid))
        }?.value
    }

    private fun securityTypesFromHint(securityHint: String?): List<String> {
        val hint = securityHint.orEmpty()
        val types = mutableListOf<String>()
        if (hint.contains("SAE", ignoreCase = true) || hint.contains("WPA3", ignoreCase = true)) {
            types.add("wpa3")
        }
        if (hint.contains("WPA", ignoreCase = true) || hint.contains("PSK", ignoreCase = true)) {
            types.add("wpa2")
        }
        if (types.isEmpty()) types.add("wpa2")
        return types.distinct()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun execRootWifiCommand(command: String, checkOutput: Boolean = true): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            process.destroy()

            if (exitCode != 0) return false
            if (!checkOutput) return true

            val combined = (stdout + stderr).lowercase(Locale.getDefault())
            val hasFailureKeyword = listOf(
                "failure", "failed", "error", "unable", "not found",
                "invalid", "denied", "rejected", "unknown network"
            ).any { combined.contains(it) }
            !hasFailureKeyword
        } catch (_: Exception) {
            false
        }
    }

    private fun execRootShellScript(script: String): Boolean {
        return execRootWifiCommand(script, checkOutput = false)
    }

    // Ép hệ điều hành Android tự kết nối qua lệnh shell của root (không hộp thoại)
    fun connectToSsidViaRoot(ssid: String, password: String? = null, securityHint: String? = null): Boolean {
        return try {
            val psk = resolveConnectionPassword(ssid, password).orEmpty()
            val requiresPassword = requiresPasswordFromHint(securityHint)

            if (requiresPassword && psk.isEmpty()) {
                return false
            }

            val qSsid = shellQuote(ssid)
            val securityTypes = securityTypesFromHint(securityHint)
            val script = buildString {
                append("cmd wifi set-wifi-enabled enabled; ")
                append("cmd wifi disconnect; ")
                append("sleep 1; ")
                for (type in securityTypes) {
                    if (psk.isNotEmpty()) {
                        append("cmd wifi connect-network $qSsid $type ${shellQuote(psk)}; ")
                    }
                    append("cmd wifi connect-network $qSsid $type; ")
                }
                if (!requiresPassword) {
                    append("cmd wifi connect-network $qSsid open; ")
                }
            }

            execRootShellScript(script)
        } catch (_: Exception) {
            false
        }
    }

    // Tự động tìm kiếm mạng có cùng tiền tố/tên tương tự đã lưu mật khẩu
    fun getSimilarSsidWithSavedPassword(ssid: String): Pair<String, String>? {
        val passwords = getSavedWifiPasswords()
        if (passwords.isEmpty()) return null
        
        // Chuẩn hóa tên mạng đích (bỏ khoảng trắng, dấu gạch, chữ thường, bỏ hậu tố 5G/2.4G)
        val normalizedTarget = ssid.lowercase()
            .replace(Regex("[\\s-_]"), "")
            .replace("5g", "")
            .replace("2.4g", "")
        
        if (normalizedTarget.isEmpty()) return null

        for ((key, value) in passwords) {
            if (!WifiCredentialKeys.isPlausibleWifiPassword(value)) continue
            val (storedSsid, _) = WifiCredentialKeys.parseStorageKey(key)
            if (storedSsid.equals(ssid, ignoreCase = true)) continue
            val normalizedKey = storedSsid.lowercase()
                .replace(Regex("[\\s-_]"), "")
                .replace("5g", "")
                .replace("2.4g", "")

            if (normalizedKey == normalizedTarget) {
                return Pair(storedSsid, value)
            }

            // Cùng tiền tố (vd. HaiNam-5G → HAINAM 5G LAU 1) — thường dùng chung mật khẩu
            val minPrefixLen = 5
            if (normalizedKey.length >= minPrefixLen && normalizedTarget.length >= minPrefixLen) {
                if (normalizedTarget.startsWith(normalizedKey) || normalizedKey.startsWith(normalizedTarget)) {
                    return Pair(storedSsid, value)
                }
            }
        }
        return null
    }
}

