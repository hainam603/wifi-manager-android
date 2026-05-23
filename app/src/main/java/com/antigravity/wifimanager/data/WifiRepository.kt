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
    private val sharedWifiRepository = SharedWifiRepository(context)

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
        const val SCAN_CACHE_WINDOW_MS = 30_000L
        const val SYSTEM_PASSWORD_SYNC_WINDOW_MS = 120_000L
        const val SWITCH_COOLDOWN_MS = 60_000L
    }

    fun isMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_MONITORING_ENABLED, true)

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
        val activeNetwork = connectivityManager.activeNetwork ?: return WifiConnectionState()
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return WifiConnectionState()
        
        // Nếu hệ thống xác nhận đây là mạng kết nối qua WiFi thì chắc chắn đang online
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiConnectionState()
        }

        // Lấy thông tin WifiInfo từ transportInfo (Android 12+) hoặc connectionInfo (Android 11 trở xuống)
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        // Dự phòng: Nếu transportInfo bị null, sử dụng phương pháp connectionInfo cũ
        val finalWifiInfo = wifiInfo ?: @Suppress("DEPRECATION") wifiManager.connectionInfo

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

        val (ipAddress, dnsServers) = readActiveNetworkLinkInfo(activeNetwork)

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

    /** Xóa hoàn toàn mạng khỏi app (mật khẩu, tin cậy, đánh dấu thủ công). */
    fun forgetNetwork(ssid: String, bssid: String? = null) {
        removeSavedWifiPassword(ssid, bssid)
        val stillHasPassword = getSavedWifiPasswords().keys.any { key ->
            WifiCredentialKeys.parseStorageKey(key).first.equals(ssid, ignoreCase = true)
        }
        if (!stillHasPassword) {
            removeAllowedSsid(ssid)
            unmarkAppManaged(ssid)
        }
    }

    fun getSavedWifiPasswords(): Map<String, String> {
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
        val cleaned = passwords.filter { (key, value) ->
            val (_, storedBssid) = WifiCredentialKeys.parseStorageKey(key)
            WifiCredentialKeys.isPlausibleWifiPassword(value, storedBssid)
        }
        if (cleaned.size != passwords.size) {
            val obj = JSONObject()
            cleaned.forEach { (k, value) -> obj.put(k, value) }
            prefs.edit().putString(KEY_SAVED_PASSWORDS, obj.toString()).apply()
        }
        return cleaned
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

    fun hasStoredCredential(ssid: String): Boolean {
        return hasSavedWifiPassword(ssid) ||
            isSystemConnectedSsid(ssid) ||
            getSimilarSsidWithSavedPassword(ssid) != null ||
            !sharedWifiRepository.getSharedPassword(ssid).isNullOrBlank()
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

    suspend fun prefetchSharedWifiArea(onProgress: suspend (Int, Int) -> Unit = { _, _ -> }) =
        sharedWifiRepository.prefetchArea(onProgress)

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
        val obj = JSONObject()
        passwords.forEach { (k, value) -> obj.put(k, value) }
        prefs.edit().putString(KEY_SAVED_PASSWORDS, obj.toString()).apply()
        addAllowedSsid(ssid)
        markAppManaged(ssid)
    }

    fun removeSavedWifiPassword(ssid: String, bssid: String? = null) {
        val passwords = getSavedWifiPasswords().toMutableMap()
        var changed = false
        if (WifiCredentialKeys.isValidBssid(bssid)) {
            changed = passwords.remove(WifiCredentialKeys.storageKey(ssid, bssid)) != null
        } else {
            val keysToRemove = passwords.keys.filter { key ->
                val (storedSsid, _) = WifiCredentialKeys.parseStorageKey(key)
                storedSsid.equals(ssid, ignoreCase = true)
            }
            keysToRemove.forEach { if (passwords.remove(it) != null) changed = true }
        }
        if (changed) {
            val obj = JSONObject()
            passwords.forEach { (k, value) -> obj.put(k, value) }
            prefs.edit().putString(KEY_SAVED_PASSWORDS, obj.toString()).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun readSystemSavedSsids(): MutableSet<String> {
        val systemSsids = mutableSetOf<String>()
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
        return systemSsids
    }

    /** Gỡ dữ liệu app cho mạng đã bị xóa khỏi Cài đặt WiFi hệ thống. */
    private fun pruneStaleNetworkData(systemSsids: Set<String>) {
        if (systemSsids.isEmpty()) return

        val connectedSsid = getCurrentConnectionState().ssid.takeIf {
            getCurrentConnectionState().isConnected && it.isNotBlank() &&
                it != "Mạng WiFi" && it != "Đang kết nối WiFi" && it != "<unknown ssid>"
        }

        val passwords = getSavedWifiPasswords().toMutableMap()
        passwords.keys.toList().forEach { ssid ->
            if (ssid !in systemSsids && !isAppManaged(ssid) && ssid != connectedSsid) {
                passwords.remove(ssid)
            }
        }
        val passwordObj = JSONObject()
        passwords.forEach { (key, value) -> passwordObj.put(key, value) }

        val allowed = getAllowedSsids().toMutableSet()
        allowed.removeAll { ssid ->
            ssid !in systemSsids && !isAppManaged(ssid) && ssid != connectedSsid
        }

        val verifiedConnected = (prefs.getStringSet(KEY_SYSTEM_CONNECTED_SSIDS, emptySet()) ?: emptySet())
            .toMutableSet()
        verifiedConnected.removeAll { ssid ->
            ssid !in systemSsids && !isAppManaged(ssid) && ssid != connectedSsid
        }

        prefs.edit()
            .putString(KEY_SAVED_PASSWORDS, passwordObj.toString())
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
                val trustedBySystem = fromConfigured.isEmpty() || cfg.ssid in fromConfigured
                if (!trustedBySystem) return@forEach

                systemSsids.add(cfg.ssid)
                if (cfg.psk.isNotBlank()) {
                    mergedPasswords[cfg.ssid] = cfg.psk
                    addAllowedSsid(cfg.ssid)
                }
            }
            lastSystemPasswordSyncAtMs = now
        }

        val obj = JSONObject()
        mergedPasswords.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit()
            .putString(KEY_SAVED_PASSWORDS, obj.toString())
            .apply()

        // Chỉ dọn dữ liệu cũ khi đọc được danh sách hệ thống (tránh xóa nhầm khi API trả rỗng)
        if (fromConfigured.isNotEmpty() || (isRoot && shouldSyncRoot && systemSsids.isNotEmpty())) {
            pruneStaleNetworkData(systemSsids)
        }
    }

    fun getLastScanAtMs(): Long = lastScanAtMs

    fun wasLastScanServedFromCache(): Boolean = lastScanUsedCache

    // Quét tìm danh sách các mạng WiFi khả dụng xung quanh
    @SuppressLint("MissingPermission")
    fun scanNearbyNetworks(forceRefresh: Boolean = false): List<WifiApInfo> {
        try {
            val now = System.currentTimeMillis()
            if (!forceRefresh && lastScanResults.isNotEmpty() && (now - lastScanAtMs) < SCAN_CACHE_WINDOW_MS) {
                lastScanUsedCache = true
                return lastScanResults
            }

            // Đồng bộ hệ thống chỉ khi quét thật — tránh lag khi mở tab dùng cache
            syncPasswordsFromSystem(forceRefresh = forceRefresh)

            // Chỉ gọi startScan khi bắt buộc — giảm pin (Android giới hạn ~4 lần/2 phút)
            @Suppress("DEPRECATION")
            if (forceRefresh) {
                wifiManager.startScan()
            }
            val scanResults = wifiManager.scanResults ?: return emptyList()
            
            if (sharedWifiRepository.isEnabled()) {
                sharedWifiRepository.refresh(force = forceRefresh)
            }

            val processed = scanResults
                .filter { !it.SSID.isNullOrEmpty() }
                .map {
                    val percent = calculateSignalPercent(it.level)
                    WifiApInfo(
                        ssid = it.SSID,
                        bssid = it.BSSID,
                        signalPercent = percent,
                        frequencyMhz = it.frequency,
                        isSaved = isSsidAllowed(it.SSID),
                        hasStoredPassword = hasStoredCredential(it.SSID),
                        securityType = it.capabilities ?: "Open"
                    )
                }

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
        val explicitPassword = password?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
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
                if (exitCode == 0 && output == "0") {
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
        rootAvailabilityCache?.let { return it }
        return getRootStatus() == RootStatus.GRANTED
    }

    fun ssidsMatch(current: String, target: String): Boolean {
        return current.trim().equals(target.trim(), ignoreCase = true)
    }

    /** Gộp mật khẩu từ app, mạng tương tự, hoặc đồng bộ hệ thống — không nhầm BSSID/capabilities với mật khẩu. */
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
        sharedWifiRepository.resolvePassword(ssid, bssid, this)
            ?.trim()
            ?.takeIf { WifiCredentialKeys.isPlausibleWifiPassword(it, bssid) }
            ?.let { return it }
        return null
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

