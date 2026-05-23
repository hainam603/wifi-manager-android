package com.antigravity.wifimanager.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.antigravity.wifimanager.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.cos
import java.util.concurrent.TimeUnit

class SharedWifiRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("wifi_switcher_prefs", Context.MODE_PRIVATE)
    private val wifiMasterSource = WifiMasterSharedSource()
    private val offlineStore = SharedWifiOfflineStore(context)
    private val rejectedStore = SharedWifiRejectedStore(context)

    private var cachedCredentials: List<SharedWifiCredential> = emptyList()
    private var cacheLat: Double? = null
    private var cacheLng: Double? = null
    private var cacheAtMs: Long = 0L
    private val passwordBySsid = mutableMapOf<String, SharedWifiCredential>()

    companion object {
        private const val KEY_SHARED_WIFI_ENABLED = "shared_wifi_enabled"
        private const val KEY_WIFIMASTER_ENABLED = "wifimaster_enabled"
        private const val KEY_OFFLINE_CACHE_ENABLED = "shared_wifi_offline_enabled"
        private const val KEY_OFFLINE_RADIUS_KM = "shared_wifi_offline_radius_km"
        private const val KEY_OFFLINE_MAX_STORAGE_MB = "shared_wifi_offline_max_storage_mb"
        private const val KEY_SHARED_WIFI_URL = "shared_wifi_api_url"
        private const val KEY_SHARED_WIFI_API_KEY = "shared_wifi_api_key"
        private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10)
        private const val PREFETCH_DELAY_MS = 650L
        private const val MAX_PREFETCH_POINTS = 800
        const val MIN_OFFLINE_RADIUS_KM = 1
        const val MAX_OFFLINE_RADIUS_KM = 50
        const val MIN_OFFLINE_STORAGE_MB = 50
        const val MAX_OFFLINE_STORAGE_MB = 4096
        const val DEFAULT_OFFLINE_RADIUS_KM = 5
        const val DEFAULT_OFFLINE_STORAGE_MB = 512
    }

    fun getOfflineRadiusKm(): Int =
        prefs.getInt(KEY_OFFLINE_RADIUS_KM, DEFAULT_OFFLINE_RADIUS_KM)
            .coerceIn(MIN_OFFLINE_RADIUS_KM, MAX_OFFLINE_RADIUS_KM)

    fun setOfflineRadiusKm(km: Int) {
        prefs.edit()
            .putInt(KEY_OFFLINE_RADIUS_KM, km.coerceIn(MIN_OFFLINE_RADIUS_KM, MAX_OFFLINE_RADIUS_KM))
            .apply()
    }

    fun getOfflineMaxStorageMb(): Int =
        prefs.getInt(KEY_OFFLINE_MAX_STORAGE_MB, DEFAULT_OFFLINE_STORAGE_MB)
            .coerceIn(MIN_OFFLINE_STORAGE_MB, MAX_OFFLINE_STORAGE_MB)

    fun setOfflineMaxStorageMb(mb: Int) {
        prefs.edit()
            .putInt(KEY_OFFLINE_MAX_STORAGE_MB, mb.coerceIn(MIN_OFFLINE_STORAGE_MB, MAX_OFFLINE_STORAGE_MB))
            .apply()
    }

    fun getOfflineMaxNetworks(): Int =
        offlineStore.estimateMaxNetworksForStorageMb(getOfflineMaxStorageMb())

    private fun offlineLookupRadiusMeters(): Int = getOfflineRadiusKm() * 1000

    private fun apiFetchRadiusMeters(): Int =
        offlineLookupRadiusMeters().coerceAtMost(20_000)

    private fun offlineStorageLimitBytes(): Long =
        getOfflineMaxStorageMb().toLong() * 1024L * 1024L

    private fun upsertOffline(
        credentials: List<SharedWifiCredential>,
        lat: Double,
        lng: Double
    ) {
        rejectedStore.clearIfPasswordUpdated(credentials)
        offlineStore.upsert(
            credentials = credentials,
            fallbackLat = lat,
            fallbackLng = lng,
            maxEntries = getOfflineMaxNetworks(),
            maxStorageBytes = offlineStorageLimitBytes()
        )
    }

    fun getOfflineStorageMb(): Double = offlineStore.getStorageMb()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_SHARED_WIFI_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHARED_WIFI_ENABLED, value).apply()
        if (!value) clearMemoryCache()
    }

    fun isWifiMasterEnabled(): Boolean = prefs.getBoolean(KEY_WIFIMASTER_ENABLED, true)

    fun setWifiMasterEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFIMASTER_ENABLED, value).apply()
        clearMemoryCache()
    }

    fun isOfflineCacheEnabled(): Boolean = prefs.getBoolean(KEY_OFFLINE_CACHE_ENABLED, true)

    fun setOfflineCacheEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_CACHE_ENABLED, value).apply()
    }

    fun getOfflineCacheCount(): Int = offlineStore.count()

    fun clearOfflineCache() {
        offlineStore.clear()
        clearMemoryCache()
    }

    fun getApiUrl(): String = prefs.getString(KEY_SHARED_WIFI_URL, "").orEmpty()

    fun setApiUrl(url: String) {
        prefs.edit().putString(KEY_SHARED_WIFI_URL, url.trim()).apply()
        clearMemoryCache()
    }

    fun getApiKey(): String = prefs.getString(KEY_SHARED_WIFI_API_KEY, "").orEmpty()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_SHARED_WIFI_API_KEY, key.trim()).apply()
        clearMemoryCache()
    }

    fun clearMemoryCache() {
        cachedCredentials = emptyList()
        passwordBySsid.clear()
        cacheAtMs = 0L
        cacheLat = null
        cacheLng = null
    }

    fun getSharedPassword(ssid: String): String? = passwordBySsid[ssid]?.password

    fun getProviderName(ssid: String): String? = passwordBySsid[ssid]?.providerName

    /** Tra mật khẩu cộng đồng — khớp SSID/BSSID tương tự như lúc quét. */
    fun resolvePassword(ssid: String, bssid: String?, wifiRepository: WifiRepository): String? {
        if (passwordBySsid.isEmpty() && isOfflineCacheEnabled()) {
            val location = LocationHelper.getLastKnownLocation(context)
            if (location != null) {
                val offline = offlineStore.queryNearby(
                    location.latitude,
                    location.longitude,
                    offlineLookupRadiusMeters()
                )
                if (offline.isNotEmpty()) {
                    cachedCredentials = offline
                    rebuildPasswordIndex()
                }
            }
        }

        val ap = WifiApInfo(
            ssid = ssid,
            bssid = bssid ?: "02:00:00:00:00:00",
            signalPercent = 0,
            frequencyMhz = 0,
            securityType = ""
        )
        return findMatch(ap, wifiRepository)?.password?.takeUnless { password ->
            isPasswordRejected(ap.ssid, ap.bssid, password)
        } ?: getSharedPassword(ssid)?.takeUnless { password ->
            isPasswordRejected(ssid, bssid, password)
        }
    }

    fun isPasswordRejected(ssid: String, bssid: String?, password: String): Boolean =
        rejectedStore.isRejected(ssid, bssid, password)

    fun markPasswordRejected(ssid: String, bssid: String?, password: String) {
        rejectedStore.markRejected(ssid, bssid, password)
        // Cùng SSID (2.4G/5G) dùng chung mật khẩu API — đánh dấu luôn cấp SSID.
        rejectedStore.markRejected(ssid, null, password)
    }

    /** Mật khẩu vừa dùng có khớp bản ghi WiFi cộng đồng/API cho SSID này không. */
    fun matchesSharedApiPassword(
        ssid: String,
        bssid: String?,
        password: String,
        wifiRepository: WifiRepository
    ): Boolean {
        if (password.isBlank()) return false
        val ap = WifiApInfo(
            ssid = ssid,
            bssid = bssid ?: "02:00:00:00:00:00",
            signalPercent = 0,
            frequencyMhz = 0,
            securityType = ""
        )
        findMatch(ap, wifiRepository)?.password?.let { if (it == password) return true }
        resolvePassword(ssid, bssid, wifiRepository)?.let { if (it == password) return true }
        return passwordBySsid.values.any { cred ->
            cred.password == password && (
                cred.ssid.equals(ssid, ignoreCase = true) ||
                    wifiRepository.ssidsMatch(cred.ssid, ssid) ||
                    wifiRepository.areRelatedSsids(cred.ssid, ssid)
                )
        }
    }

    private fun enrichWithSharedMatch(
        ap: WifiApInfo,
        match: SharedWifiCredential?,
        wifiRepository: WifiRepository
    ): WifiApInfo {
        val manualPassword = wifiRepository.getSavedWifiPassword(ap.ssid)
        val hasManualOverride = !manualPassword.isNullOrBlank() &&
            !isPasswordRejected(ap.ssid, ap.bssid, manualPassword)

        if (match != null) {
            val rejected = isPasswordRejected(ap.ssid, ap.bssid, match.password)
            return ap.copy(
                isReadyToConnect = !rejected || hasManualOverride,
                isSharedPasswordRejected = rejected && !hasManualOverride,
                sharedPasswordFromApi = match.password,
                hasStoredPassword = if (rejected) hasManualOverride else true,
                sharedProviderName = if (match.cachedAtMs > 0L && !isNetworkAvailable()) {
                    "${match.providerName} (offline)"
                } else {
                    match.providerName
                }
            )
        }

        val ssidRejected = rejectedStore.findRejectedForSsid(ap.ssid)
        if (ssidRejected != null && !hasManualOverride) {
            return ap.copy(
                isReadyToConnect = false,
                isSharedPasswordRejected = true,
                sharedPasswordFromApi = ssidRejected.password,
                hasStoredPassword = false
            )
        }

        return ap.copy(isReadyToConnect = ap.hasStoredPassword)
    }

    fun findSharedCredential(ssid: String, bssid: String?, wifiRepository: WifiRepository): SharedWifiCredential? {
        val ap = WifiApInfo(
            ssid = ssid,
            bssid = bssid ?: "02:00:00:00:00:00",
            signalPercent = 0,
            frequencyMhz = 0,
            securityType = ""
        )
        return findMatch(ap, wifiRepository)
    }

    /**
     * Tải trước WiFi cộng đồng theo bán kính đã cấu hình.
     * Cần Internet + GPS; kết quả lưu vào bộ nhớ offline.
     */
    suspend fun prefetchArea(onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }): SharedWifiPrefetchResult {
        if (!isEnabled()) {
            return SharedWifiPrefetchResult(skipped = true, reason = "Đã tắt API chia sẻ")
        }
        if (!isOfflineCacheEnabled()) {
            return SharedWifiPrefetchResult(skipped = true, reason = "Hãy bật Lưu offline")
        }
        if (!isNetworkAvailable()) {
            return SharedWifiPrefetchResult(skipped = true, reason = "Cần kết nối Internet")
        }
        if (!isWifiMasterEnabled() && getApiUrl().isBlank()) {
            return SharedWifiPrefetchResult(skipped = true, reason = "Bật WifiMaster hoặc nhập URL API")
        }

        val location = LocationHelper.getLastKnownLocation(context)
            ?: return SharedWifiPrefetchResult(skipped = true, reason = "Chưa có vị trí GPS")

        val radiusKm = getOfflineRadiusKm()
        val grid = buildPrefetchGrid(location.latitude, location.longitude, radiusKm)
        val total = grid.size
        var newNetworks = 0
        val seenBefore = offlineStore.count()

        return withContext(Dispatchers.IO) {
            onProgress(0, total)
            grid.forEachIndexed { index, point ->
                val batch = fetchAllSources(point.first, point.second)
                if (batch.isNotEmpty()) {
                    upsertOffline(batch, point.first, point.second)
                    newNetworks += batch.size
                }
                onProgress(index + 1, total)
                if (index < grid.lastIndex) {
                    delay(PREFETCH_DELAY_MS)
                }
            }

            clearMemoryCache()
            refresh(force = true)

            SharedWifiPrefetchResult(
                success = true,
                pointsFetched = total,
                networksDownloaded = newNetworks,
                offlineTotalStored = offlineStore.count(),
                addedSinceStart = offlineStore.count() - seenBefore,
                radiusKm = radiusKm
            )
        }
    }

    private fun buildPrefetchGrid(centerLat: Double, centerLng: Double, radiusKm: Int): List<Pair<Double, Double>> {
        val radiusM = radiusKm * 1000.0
        val stepKm = when {
            radiusKm <= 5 -> 1.5
            radiusKm <= 15 -> 3.0
            radiusKm <= 30 -> 5.0
            else -> 8.0
        }
        val stepM = stepKm * 1000.0
        val latDegPerM = 1.0 / 111_320.0
        val lngDegPerM = 1.0 / (111_320.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.01))

        val points = mutableListOf<Pair<Double, Double>>()
        var northM = -radiusM
        while (northM <= radiusM) {
            var eastM = -radiusM
            while (eastM <= radiusM) {
                if (northM * northM + eastM * eastM <= radiusM * radiusM) {
                    val lat = centerLat + northM * latDegPerM
                    val lng = centerLng + eastM * lngDegPerM
                    points.add(lat to lng)
                }
                eastM += stepM
            }
            northM += stepM
        }

        if (points.size <= MAX_PREFETCH_POINTS) {
            return points
        }

        // Giảm số điểm nếu vùng quá rộng — lấy mẫu đều
        val stride = (points.size.toDouble() / MAX_PREFETCH_POINTS).coerceAtLeast(1.0)
        return points.filterIndexed { index, _ ->
            (index % stride.toInt().coerceAtLeast(1)) == 0
        }.take(MAX_PREFETCH_POINTS)
    }

    fun refresh(force: Boolean = false): SharedWifiFetchResult {
        if (!isEnabled()) {
            return SharedWifiFetchResult(skipped = true, reason = "Đã tắt API chia sẻ")
        }

        if (!isWifiMasterEnabled() && getApiUrl().isBlank() && !isOfflineCacheEnabled()) {
            return SharedWifiFetchResult(skipped = true, reason = "Chưa bật nguồn dữ liệu")
        }

        val location = LocationHelper.getLastKnownLocation(context)
            ?: return SharedWifiFetchResult(skipped = true, reason = "Chưa có vị trí GPS")

        val offlineNearby = if (isOfflineCacheEnabled()) {
            offlineStore.queryNearby(location.latitude, location.longitude, offlineLookupRadiusMeters())
        } else {
            emptyList()
        }

        val now = System.currentTimeMillis()
        if (!force && cachedCredentials.isNotEmpty() && now - cacheAtMs < CACHE_TTL_MS) {
            val movedM = haversineMeters(cacheLat ?: 0.0, cacheLng ?: 0.0, location.latitude, location.longitude)
            if (movedM < 200) {
                return buildResult(fromCache = true, offlineUsed = 0)
            }
        }

        val hasNetwork = isNetworkAvailable()
        val onlineFetched = if (hasNetwork && (isWifiMasterEnabled() || getApiUrl().isNotBlank())) {
            runBlocking(Dispatchers.IO) {
                fetchAllSources(location.latitude, location.longitude)
            }
        } else {
            emptyList()
        }

        if (onlineFetched.isNotEmpty()) {
            rejectedStore.clearIfPasswordUpdated(onlineFetched)
        }

        if (onlineFetched.isNotEmpty() && isOfflineCacheEnabled()) {
            upsertOffline(onlineFetched, location.latitude, location.longitude)
        }

        val merged = mergeCredentials(onlineFetched, offlineNearby)
        if (merged.isEmpty()) {
            val reason = when {
                !hasNetwork && offlineNearby.isEmpty() ->
                    "Không có mạng — chưa tải dữ liệu offline vùng này (hãy quét khi có Internet trước)"
                !hasNetwork -> "Không có mạng"
                else -> "Không có WiFi cộng đồng khớp vùng này"
            }
            return SharedWifiFetchResult(skipped = true, reason = reason)
        }

        cachedCredentials = merged
        cacheLat = location.latitude
        cacheLng = location.longitude
        cacheAtMs = now
        rebuildPasswordIndex()

        val offlineUsed = if (onlineFetched.isEmpty()) merged.size else offlineNearby.count { offline ->
            merged.any { it.ssid == offline.ssid && it.password == offline.password }
        }

        return buildResult(
            fromCache = false,
            offlineUsed = offlineUsed,
            usedOfflineOnly = onlineFetched.isEmpty()
        )
    }

    private fun buildResult(
        fromCache: Boolean,
        offlineUsed: Int = 0,
        usedOfflineOnly: Boolean = false
    ): SharedWifiFetchResult {
        return SharedWifiFetchResult(
            success = true,
            count = cachedCredentials.size,
            fromCache = fromCache,
            wifiMasterCount = countByProvider("WifiMaster"),
            customApiCount = cachedCredentials.count { it.providerName != "WifiMaster" && it.providerName.isNotBlank() },
            offlineUsedCount = offlineUsed,
            usedOfflineOnly = usedOfflineOnly,
            offlineTotalStored = offlineStore.count()
        )
    }

    private fun mergeCredentials(
        online: List<SharedWifiCredential>,
        offline: List<SharedWifiCredential>
    ): List<SharedWifiCredential> {
        val map = linkedMapOf<String, SharedWifiCredential>()
        offline.forEach { map[dedupeKey(it)] = it }
        online.forEach { map[dedupeKey(it)] = it } // online ghi đè offline
        return map.values.toList()
    }

    private fun dedupeKey(cred: SharedWifiCredential): String {
        return "${cred.ssid.lowercase()}|${cred.bssid?.lowercase().orEmpty()}"
    }

    private suspend fun fetchAllSources(latitude: Double, longitude: Double): List<SharedWifiCredential> {
        val merged = mutableListOf<SharedWifiCredential>()

        if (isWifiMasterEnabled()) {
            merged.addAll(wifiMasterSource.fetchNearby(latitude, longitude))
        }

        val apiUrl = getApiUrl()
        if (apiUrl.isNotBlank()) {
            val client = SharedWifiApiClient(apiUrl, getApiKey())
            merged.addAll(client.fetchNearby(latitude, longitude, apiFetchRadiusMeters()))
        }

        return merged.distinctBy { dedupeKey(it) }
    }

    fun enrichAccessPoints(aps: List<WifiApInfo>, wifiRepository: WifiRepository): List<WifiApInfo> {
        if (!isEnabled()) {
            return aps.map { it.copy(isReadyToConnect = it.hasStoredPassword) }
        }

        if (passwordBySsid.isEmpty() && isOfflineCacheEnabled()) {
            val location = LocationHelper.getLastKnownLocation(context)
            if (location != null) {
                val offline = offlineStore.queryNearby(
                    location.latitude,
                    location.longitude,
                    offlineLookupRadiusMeters()
                )
                if (offline.isNotEmpty()) {
                    cachedCredentials = offline
                    rebuildPasswordIndex()
                }
            }
        }

        if (passwordBySsid.isEmpty()) {
            return aps.map { enrichWithSharedMatch(it, null, wifiRepository) }
        }

        return aps.map { ap ->
            enrichWithSharedMatch(ap, findMatch(ap, wifiRepository), wifiRepository)
        }
    }

    private fun findMatch(ap: WifiApInfo, wifiRepository: WifiRepository): SharedWifiCredential? {
        passwordBySsid[ap.ssid]?.let { return it }

        for ((ssid, cred) in passwordBySsid) {
            if (wifiRepository.ssidsMatch(ssid, ap.ssid) ||
                wifiRepository.areRelatedSsids(ssid, ap.ssid)
            ) {
                return cred
            }
        }

        val apBssid = ap.bssid.lowercase()
        if (apBssid != "02:00:00:00:00:00") {
            return passwordBySsid.values.firstOrNull { cred ->
                !cred.bssid.isNullOrBlank() && cred.bssid.equals(apBssid, ignoreCase = true)
            }
        }
        return null
    }

    private fun rebuildPasswordIndex() {
        passwordBySsid.clear()
        cachedCredentials.forEach { cred ->
            passwordBySsid[cred.ssid] = cred
        }
    }

    private fun countByProvider(provider: String): Int =
        cachedCredentials.count { it.providerName == provider }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}

data class SharedWifiPrefetchResult(
    val success: Boolean = false,
    val skipped: Boolean = false,
    val pointsFetched: Int = 0,
    val networksDownloaded: Int = 0,
    val offlineTotalStored: Int = 0,
    val addedSinceStart: Int = 0,
    val radiusKm: Int = 0,
    val reason: String? = null
)

data class SharedWifiFetchResult(
    val success: Boolean = false,
    val skipped: Boolean = false,
    val count: Int = 0,
    val fromCache: Boolean = false,
    val wifiMasterCount: Int = 0,
    val customApiCount: Int = 0,
    val offlineUsedCount: Int = 0,
    val usedOfflineOnly: Boolean = false,
    val offlineTotalStored: Int = 0,
    val reason: String? = null
)
