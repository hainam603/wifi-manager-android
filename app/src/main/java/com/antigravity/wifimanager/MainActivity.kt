package com.antigravity.wifimanager

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import com.antigravity.wifimanager.util.ToastHelper
import com.antigravity.wifimanager.util.ToastHelper.Duration as ToastDuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.wifimanager.data.RootStatus
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiAutoSwitcher
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiRepository
import com.antigravity.wifimanager.service.WifiMonitorService
import com.antigravity.wifimanager.util.MonitorServiceStarter
import com.antigravity.wifimanager.util.WifiScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.antigravity.wifimanager.ui.screens.DashboardScreen
import com.antigravity.wifimanager.ui.screens.HistoryScreen
import com.antigravity.wifimanager.ui.screens.ScannerScreen
import com.antigravity.wifimanager.ui.screens.SettingsScreen
import com.antigravity.wifimanager.ui.screens.SystemScannerScreen
import com.antigravity.wifimanager.ui.screens.SystemScannerRowModel
import com.antigravity.wifimanager.ui.scanner.ScannerDisplayState
import com.antigravity.wifimanager.ui.scanner.ScannerUiMapper
import com.antigravity.wifimanager.ui.theme.Slate950
import com.antigravity.wifimanager.ui.theme.WifiManagerTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var repository: WifiRepository
    private lateinit var autoSwitcher: WifiAutoSwitcher
    private var wifiService by mutableStateOf<WifiMonitorService?>(null)
    private var isServiceBound by mutableStateOf(false)


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WifiMonitorService.LocalBinder
            wifiService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            wifiService = null
            isServiceBound = false
        }
    }

    // Launcher xin cấp quyền người dùng động
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val nearbyWifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: false
        } else {
            true
        }
        
        if (fineLocationGranted && nearbyWifiGranted) {
            if (repository.isMonitoringEnabled()) {
                startWifiMonitorService()
            }
        } else {
            ToastHelper.show(this, "Ứng dụng bắt buộc quyền Vị trí để quét tìm mạng WiFi!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WifiRepository(this)
        autoSwitcher = WifiAutoSwitcher(repository)

        checkAndRequestPermissions()

        // Đặt lại lịch tự động cập nhật dữ liệu offline (cần gọi mỗi lần app khởi động)
        WifiScheduler.reschedule(this, repository.getAutoUpdateIntervalDays())

        setContent {
            WifiManagerTheme {
                MainAppLayout()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions)
        } else if (repository.isMonitoringEnabled()) {
            startWifiMonitorService()
        }
    }

    private fun startWifiMonitorService() {
        MonitorServiceStarter.start(this)
        val intent = Intent(this, WifiMonitorService::class.java)
        if (!isServiceBound) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopWifiMonitorService() {
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
        MonitorServiceStarter.stop(this)
        wifiService = null
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (isIgnoringBatteryOptimizations()) {
            ToastHelper.show(this, "App đã được phép chạy nền không giới hạn pin", ToastDuration.SHORT)
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    @Composable
    fun MainAppLayout() {
        val coroutineScope = rememberCoroutineScope()
        var currentTab by remember { mutableStateOf(0) }
        
        // Biến trạng thái liên kết
        var connectionState by remember { mutableStateOf(WifiConnectionState()) }
        var scannedList by remember { mutableStateOf(listOf<WifiApInfo>()) }
        var threshold by remember { mutableStateOf(repository.getThreshold()) }
        var autoSwitchEnabled by remember { mutableStateOf(repository.isAutoSwitchEnabled()) }
        var prefer5GhzEnabled by remember { mutableStateOf(repository.isPrefer5GhzEnabled()) }
        var sharedWifiEnabled by remember { mutableStateOf(repository.isSharedWifiEnabled()) }
        var wifiMasterEnabled by remember { mutableStateOf(repository.isWifiMasterEnabled()) }
        var sharedWifiOfflineEnabled by remember { mutableStateOf(repository.isSharedWifiOfflineEnabled()) }
        var sharedWifiOfflineCount by remember { mutableStateOf(repository.getSharedWifiOfflineCount()) }
        var sharedWifiOfflineRadiusKm by remember { mutableStateOf(repository.getSharedWifiOfflineRadiusKm()) }
        var sharedWifiOfflineStorageMb by remember { mutableStateOf(repository.getSharedWifiOfflineMaxStorageMb()) }
        var sharedWifiOfflineStorageUsedMb by remember { mutableStateOf(repository.getSharedWifiOfflineStorageMb()) }
        var sharedWifiOfflineMaxNetworks by remember { mutableStateOf(repository.getSharedWifiOfflineMaxNetworks()) }
        var sharedWifiPrefetching by remember { mutableStateOf(false) }
        var sharedWifiPrefetchProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var sharedWifiApiUrl by remember { mutableStateOf(repository.getSharedWifiApiUrl()) }
        var sharedWifiApiKey by remember { mutableStateOf(repository.getSharedWifiApiKey()) }
        var historyLogs by remember { mutableStateOf(repository.getHistoryLogs()) }
        var isServiceRunning by remember { mutableStateOf(repository.isMonitoringEnabled()) }
        var scanStatusText by remember { mutableStateOf("Chưa quét") }
        var isScanning by remember { mutableStateOf(false) }
        var connectingApKey by remember { mutableStateOf<String?>(null) }
        var connectFailedApKeys by remember { mutableStateOf(setOf<String>()) }
        var rootStatus by remember { mutableStateOf(RootStatus.UNAVAILABLE) }
        var batteryOptimized by remember { mutableStateOf(!isIgnoringBatteryOptimizations()) }
        var loadingMessage by remember { mutableStateOf<String?>(null) }
        var isRequestingRoot by remember { mutableStateOf(false) }
        var isClearingOffline by remember { mutableStateOf(false) }
        var isClearingHistory by remember { mutableStateOf(false) }
        var isTogglingService by remember { mutableStateOf(false) }
        var isSavingPassword by remember { mutableStateOf(false) }
        var refreshingPasswordApKey by remember { mutableStateOf<String?>(null) }
        var scannerDisplay by remember { mutableStateOf(ScannerDisplayState.Empty) }
        var scannerConnectedSignal by remember { mutableIntStateOf(0) }
        var scanJob by remember { mutableStateOf<Job?>(null) }
        var scanCounter by remember { mutableIntStateOf(0) }
        val scannerDisplayMutex = remember { Mutex() }
        var autoUpdateIntervalDays by remember { mutableIntStateOf(repository.getAutoUpdateIntervalDays()) }
        var lastAutoUpdateMs by remember { mutableLongStateOf(repository.getLastAutoUpdateMs()) }
        var isAutoUpdating by remember { mutableStateOf(false) }

        var systemScannedList by remember { mutableStateOf(listOf<WifiApInfo>()) }
        var systemScannerRows by remember { mutableStateOf(listOf<SystemScannerRowModel>()) }
        var isSystemScanning by remember { mutableStateOf(false) }
        var systemScanStatusText by remember { mutableStateOf("Chưa quét") }
        var systemScanJob by remember { mutableStateOf<Job?>(null) }
        var systemConnectingApKey by remember { mutableStateOf<String?>(null) }
        var systemConnectFailedApKeys by remember { mutableStateOf(setOf<String>()) }

        fun updateSystemScanStatusText() {
            val lastScanMs = repository.getLastScanAtMs()
            if (lastScanMs <= 0L) {
                systemScanStatusText = "Chưa quét"
                return
            }
            val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastScanMs))
            val rawCount = systemScannedList.size
            val savedCount = systemScannedList.count { it.isSaved }
            systemScanStatusText = "Quét lúc $timeText · Tìm thấy $rawCount mạng ($savedCount đã lưu)"
        }

        suspend fun refreshSystemNetworksAndWait(forceRefresh: Boolean = false) {
            if (isSystemScanning || systemConnectingApKey != null) return
            isSystemScanning = true
            if (forceRefresh) {
                systemScanStatusText = "Đang quét WiFi hệ thống..."
            }
            try {
                systemScannedList = withContext(Dispatchers.IO) {
                    repository.scanSystemOnly(forceRefresh = forceRefresh)
                }
                updateSystemScanStatusText()
            } finally {
                isSystemScanning = false
            }
        }

        fun startSystemScanFromButton() {
            if (systemConnectingApKey != null) return
            if (systemScanJob?.isActive == true) return
            systemScanJob = coroutineScope.launch {
                try {
                    refreshSystemNetworksAndWait(forceRefresh = true)
                    connectionState = withContext(Dispatchers.IO) {
                        repository.getCurrentConnectionState()
                    }
                } finally {
                    systemScanJob = null
                }
            }
        }

        suspend fun rebuildScannerDisplay() {
            val snapshot = connectionState
            scannerDisplay = withContext(Dispatchers.IO) {
                ScannerUiMapper.build(
                    networks = scannedList,
                    connection = snapshot,
                    resolvePassword = { ssid, bssid ->
                        repository.resolveConnectionPassword(ssid, bssid = bssid)
                    },
                    resolveSimilarPassword = { ssid ->
                        repository.getSimilarSsidWithSavedPassword(ssid)
                    }
                )
            }
        }

        LaunchedEffect(scannedList, connectionState) {
            scannerDisplayMutex.withLock {
                rebuildScannerDisplay()
            }
        }

        LaunchedEffect(systemScannedList, connectionState) {
            val snapshot = connectionState
            val rawList = systemScannedList
            systemScannerRows = withContext(Dispatchers.IO) {
                rawList.map { ap ->
                    val savedPassword = repository.resolveConnectionPassword(ap.ssid, bssid = ap.bssid)
                    val hasCred = repository.hasStoredCredential(ap.ssid)
                    val isConnected = snapshot.isConnected && repository.ssidsMatch(snapshot.ssid, ap.ssid)
                    SystemScannerRowModel(
                        ap = ap,
                        savedPassword = savedPassword,
                        hasSystemCredential = hasCred,
                        isConnected = isConnected
                    )
                }
            }
        }


        fun updateScanStatusText() {
            val lastScanMs = repository.getLastScanAtMs()
            if (lastScanMs <= 0L) {
                scanStatusText = "Chưa quét"
                return
            }

            val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastScanMs))
            val sourceText = if (repository.wasLastScanServedFromCache()) "cache" else "mới"
            val readyCount = scannedList.count { it.isReadyToConnect }
            val sharedFromApi = scannedList.count { !it.sharedProviderName.isNullOrBlank() }
            val sharedHint = when {
                !repository.isSharedWifiEnabled() -> ""
                sharedFromApi > 0 -> {
                    val offlineStored = repository.getSharedWifiOfflineCount()
                    " · $sharedFromApi từ cộng đồng · offline: $offlineStored"
                }
                repository.isWifiMasterEnabled() || repository.getSharedWifiApiUrl().isNotBlank() ||
                    repository.isSharedWifiOfflineEnabled() ->
                    " · chưa khớp · offline: ${repository.getSharedWifiOfflineCount()}"
                else -> " · chưa bật nguồn"
            }
            scanStatusText = "Quét lúc $timeText ($sourceText) · $readyCount sẵn sàng$sharedHint"
            sharedWifiOfflineCount = repository.getSharedWifiOfflineCount()
            sharedWifiOfflineStorageUsedMb = repository.getSharedWifiOfflineStorageMb()
        }

        suspend fun reloadScannerFromCache() {
            scannedList = withContext(Dispatchers.IO) {
                repository.scanNearbyNetworks(forceRefresh = false)
            }
            scannerConnectedSignal = withContext(Dispatchers.IO) {
                val s = repository.getCurrentConnectionState()
                if (s.isConnected) s.signalPercent else 0
            }
            scannerDisplayMutex.withLock {
                rebuildScannerDisplay()
            }
            updateScanStatusText()
        }

        suspend fun refreshScannedNetworksAndWait(forceRefresh: Boolean = false) {
            if (isScanning || connectingApKey != null) return
            isScanning = true
            if (forceRefresh) {
                scanStatusText = "Đang quét WiFi (phần cứng)..."
            }
            try {
                scannedList = withContext(Dispatchers.IO) {
                    repository.scanNearbyNetworks(forceRefresh = forceRefresh)
                }
                scannerDisplayMutex.withLock {
                    rebuildScannerDisplay()
                }
                scanCounter++
                updateScanStatusText()
            } finally {
                isScanning = false
            }
        }

        /** Chỉ gọi từ nút quét (FAB) — một luồng quét tại một thời điểm. */
        fun startScanFromButton() {
            if (connectingApKey != null) return
            if (scanJob?.isActive == true) return
            scanJob = coroutineScope.launch {
                try {
                    refreshScannedNetworksAndWait(forceRefresh = true)
                    connectionState = withContext(Dispatchers.IO) {
                        repository.getCurrentConnectionState()
                    }
                    scannerConnectedSignal = if (connectionState.isConnected) connectionState.signalPercent else 0
                } finally {
                    scanJob = null
                }
            }
        }


        LaunchedEffect(Unit) {
            startScanFromButton()
        }

        // Đồng bộ dữ liệu động từ Dịch vụ chạy ngầm khi được bind thành công
        LaunchedEffect(wifiService, isServiceBound) {
            isServiceRunning = wifiService != null
            wifiService?.let { service ->
                service.connectionState.collectLatest { state ->
                    connectionState = state
                }
            }
        }

        // Tự động xóa trạng thái lỗi kết nối khi kết nối thành công mạng bất kỳ
        LaunchedEffect(connectionState) {
            if (connectionState.isConnected && connectionState.ssid.isNotEmpty()) {
                val ssidClean = connectionState.ssid.replace("\"", "").lowercase().trim()
                connectFailedApKeys = connectFailedApKeys.filterTo(mutableSetOf()) { key ->
                    val (storedSsid, _) = com.antigravity.wifimanager.data.WifiCredentialKeys.parseStorageKey(key)
                    !storedSsid.lowercase().trim().equals(ssidClean, ignoreCase = true)
                }
            }
        }

        LaunchedEffect(currentTab) {
            when (currentTab) {
                0 -> {
                    connectionState = withContext(Dispatchers.IO) {
                        repository.refreshCurrentConnectionFromEnvironment(forceScan = false)
                    }
                }
                1 -> {
                    rootStatus = withContext(Dispatchers.IO) { repository.getRootStatus() }
                    connectionState = withContext(Dispatchers.IO) {
                        repository.getCurrentConnectionState()
                    }
                    if (scannedList.isEmpty()) {
                        val cached = withContext(Dispatchers.IO) { repository.getCachedScanResults() }
                        if (cached.isNotEmpty()) {
                            scannedList = cached
                        }
                    }
                    if (scannedList.isNotEmpty()) {
                        scannerDisplayMutex.withLock {
                            rebuildScannerDisplay()
                        }
                        updateScanStatusText()
                    }
                    scannerConnectedSignal = if (connectionState.isConnected) connectionState.signalPercent else 0
                }
                2 -> {
                    rootStatus = withContext(Dispatchers.IO) { repository.getRootStatus() }
                    connectionState = withContext(Dispatchers.IO) {
                        repository.getCurrentConnectionState()
                    }
                    if (systemScannedList.isEmpty()) {
                        refreshSystemNetworksAndWait(forceRefresh = false)
                    } else {
                        updateSystemScanStatusText()
                    }
                }
                3 -> historyLogs = repository.getHistoryLogs()
                4 -> {
                    rootStatus = withContext(Dispatchers.IO) { repository.getRootStatus() }
                    batteryOptimized = !isIgnoringBatteryOptimizations()
                    sharedWifiOfflineCount = repository.getSharedWifiOfflineCount()
                    sharedWifiOfflineStorageUsedMb = repository.getSharedWifiOfflineStorageMb()
                    sharedWifiOfflineMaxNetworks = repository.getSharedWifiOfflineMaxNetworks()
                    autoUpdateIntervalDays = repository.getAutoUpdateIntervalDays()
                    lastAutoUpdateMs = repository.getLastAutoUpdateMs()
                }
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x0AFFFFFF)),
                    containerColor = Color(0x1F0F172A),
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(imageVector = Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("Trang chủ", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(imageVector = Icons.Default.Radar, contentDescription = null) },
                        label = { Text("Quét WiFi", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(imageVector = Icons.Default.Wifi, contentDescription = null) },
                        label = { Text("Hệ thống", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(imageVector = Icons.Default.History, contentDescription = null) },
                        label = { Text("Nhật ký", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Cấu hình", fontSize = 11.sp) }
                    )
                }
            },
            containerColor = Slate950
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> DashboardScreen(
                        state = connectionState,
                        wifiPassword = if (connectionState.isConnected) {
                            repository.resolveConnectionPassword(
                                connectionState.ssid,
                                connectionState.bssid.takeIf { it.isNotBlank() }
                            )
                        } else {
                            null
                        },
                        isServiceRunning = isServiceRunning,
                        isManualScanLoading = loadingMessage != null,
                        isTogglingService = isTogglingService,
                        onToggleService = {
                            if (isTogglingService) return@DashboardScreen
                            coroutineScope.launch {
                                isTogglingService = true
                                try {
                                    if (isServiceRunning) {
                                        stopWifiMonitorService()
                                        isServiceRunning = false
                                        connectionState = WifiConnectionState()
                                        ToastHelper.show(
                                            this@MainActivity,
                                            "Đã tắt giám sát WiFi nền",
                                            ToastDuration.SHORT
                                        )
                                    } else {
                                        if (!MonitorServiceStarter.hasNotificationPermission(this@MainActivity)) {
                                            ToastHelper.show(
                                                this@MainActivity,
                                                "Cần quyền Thông báo để hiện icon giám sát trên thanh trạng thái.\n" +
                                                    "Vào Cài đặt hệ thống → Ứng dụng → cho phép thông báo."
                                            )
                                            requestPermissionLauncher.launch(
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                            )
                                            return@launch
                                        }
                                        startWifiMonitorService()
                                        isServiceRunning = repository.isMonitoringEnabled()
                                        ToastHelper.show(
                                            this@MainActivity,
                                            "Đã bật giám sát — xem icon WiFi Auto-Switcher trên thanh trạng thái",
                                            ToastDuration.SHORT
                                        )
                                    }
                                } finally {
                                    isTogglingService = false
                                }
                            }
                        },
                        onManualScan = {
                            if (loadingMessage != null) return@DashboardScreen
                            coroutineScope.launch {
                                try {
                                    loadingMessage = "Đang quét WiFi (phần cứng)..."
                                    if (scanJob?.isActive != true) {
                                        refreshScannedNetworksAndWait(forceRefresh = true)
                                    }
                                    connectionState = withContext(Dispatchers.IO) {
                                        repository.getCurrentConnectionState()
                                    }

                                    if (!connectionState.isConnected) {
                                        ToastHelper.show(
                                            this@MainActivity,
                                            "Chưa kết nối WiFi",
                                            ToastDuration.SHORT
                                        )
                                        return@launch
                                    }

                                    val currentThreshold = repository.getThreshold()
                                    val signalWeak = connectionState.signalPercent < currentThreshold

                                    if (!signalWeak && !prefer5GhzEnabled) {
                                        ToastHelper.show(
                                            this@MainActivity,
                                            "Sóng ${connectionState.signalPercent}% — trên ngưỡng ${currentThreshold}%, không cần chuyển"
                                        )
                                        return@launch
                                    }

                                    loadingMessage = "Đang tìm và chuyển mạng WiFi..."
                                    val result = autoSwitcher.attemptSwitch(
                                        currentState = connectionState,
                                        enforceCooldown = false
                                    )
                                    connectionState = withContext(Dispatchers.IO) {
                                        repository.getCurrentConnectionState()
                                    }
                                    historyLogs = repository.getHistoryLogs()

                                    ToastHelper.show(
                                        this@MainActivity,
                                        result.userMessage ?: if (result.attempted) "Hoàn tất" else "Không có mạng phù hợp"
                                    )
                                } finally {
                                    loadingMessage = null
                                }
                            }
                        }
                    )
                    1 -> {
                        ScannerScreen(
                        networks = scannedList,
                        connectionState = connectionState,
                        displayState = scannerDisplay,
                        networkCount = scannedList.size,
                        scanStatusText = scanStatusText,
                        isScanning = isScanning,
                        connectingApKey = connectingApKey,
                        connectFailedApKeys = connectFailedApKeys,
                        rootConnectAvailable = rootStatus == RootStatus.GRANTED,
                        isPasswordBusy = isSavingPassword,
                        refreshingPasswordApKey = refreshingPasswordApKey,
                        connectedSignalPercent = scannerConnectedSignal,
                        scanCounter = scanCounter,
                        onRefreshScan = { startScanFromButton() },
                         onConnectNetwork = { ssid, bssid, password ->
                            if (connectingApKey != null || isScanning) return@ScannerScreen
                            if (rootStatus != RootStatus.GRANTED) {
                                ToastHelper.show(
                                    this@MainActivity,
                                    "Cần quyền Root để kết nối im lặng.\nVào Cấu hình → Yêu cầu Root."
                                )
                                return@ScannerScreen
                            }
                            val key = "${ssid.lowercase()}|${bssid.lowercase()}"
                            coroutineScope.launch {
                                connectingApKey = key
                                val result = try {
                                    val ap = scannedList.find { it.ssid == ssid && it.bssid == bssid }
                                        ?: scannedList.find { it.ssid == ssid }
                                    val psk = password?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: repository.getSavedWifiPassword(ssid, ap?.bssid ?: bssid)
                                    val preferApiPassword = ap != null &&
                                        !ap.isSharedPasswordRejected &&
                                        !ap.sharedPasswordFromApi.isNullOrBlank() &&
                                        !ap.sharedProviderName.isNullOrBlank() &&
                                        (psk.isNullOrBlank() || psk == ap.sharedPasswordFromApi)
                                    withContext(Dispatchers.IO) {
                                        repository.connectToNetwork(
                                            ssid = ssid,
                                            password = if (preferApiPassword) ap?.sharedPasswordFromApi else psk,
                                            securityHint = ap?.securityType,
                                            bssid = ap?.bssid ?: bssid
                                        )
                                    }
                                } finally {
                                    connectingApKey = null
                                }
                                connectionState = repository.getCurrentConnectionState()
                                scannerConnectedSignal = if (connectionState.isConnected) {
                                    connectionState.signalPercent
                                } else {
                                    0
                                }
                                 if (result.success) {
                                     connectFailedApKeys = connectFailedApKeys - key
                                     reloadScannerFromCache()
                                     kotlinx.coroutines.delay(1000)
                                     connectionState = repository.getCurrentConnectionState()
                                     scannerConnectedSignal = if (connectionState.isConnected) {
                                         connectionState.signalPercent
                                     } else {
                                         0
                                     }
                                     refreshScannedNetworksAndWait(forceRefresh = true)
                                 } else {
                                     connectFailedApKeys = connectFailedApKeys + key
                                     reloadScannerFromCache()
                                 }
                                val updatedAp = scannedList.find {
                                    it.ssid == ssid && it.bssid.equals(bssid, ignoreCase = true)
                                } ?: scannedList.find { it.ssid == ssid }
                                if (updatedAp?.isSharedPasswordRejected == true) {
                                    connectFailedApKeys = connectFailedApKeys - key
                                }
                                ToastHelper.show(this@MainActivity, result.message)
                            }
                        },
                        onHasSystemCredential = { ssid ->
                            repository.hasStoredCredential(ssid)
                        },
                        onGetSavedPassword = { ssid, bssid ->
                            repository.getSavedWifiPassword(ssid, bssid)
                        },
                        onSavePassword = { ssid, password, bssid ->
                            if (isSavingPassword) return@ScannerScreen
                            coroutineScope.launch {
                                isSavingPassword = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.saveWifiPassword(ssid, password, bssid)
                                        repository.suggestNetworks(repository.getAllowedSsids().toList())
                                    }
                                    reloadScannerFromCache()
                                } finally {
                                    isSavingPassword = false
                                }
                            }
                        },
                        onRemovePassword = { ssid, bssid ->
                            android.util.Log.e("MainActivity", "onRemovePassword called: ssid='$ssid', bssid='$bssid', isSavingPassword=$isSavingPassword")
                            if (isSavingPassword) return@ScannerScreen
                            coroutineScope.launch {
                                isSavingPassword = true
                                // Dọn sạch tất cả các key lỗi kết nối liên quan đến SSID này (bao gồm cả dạng quote hay khoảng trắng)
                                connectFailedApKeys = connectFailedApKeys.filter { key ->
                                    val (storedSsid, _) = com.antigravity.wifimanager.data.WifiCredentialKeys.parseStorageKey(key)
                                    val cleanStored = storedSsid.replace("\"", "").trim()
                                    val cleanTarget = ssid.replace("\"", "").trim()
                                    !cleanStored.equals(cleanTarget, ignoreCase = true)
                                }.toSet()
                                try {
                                    val isRoot = withContext(Dispatchers.IO) { repository.isRootAvailable() }
                                    android.util.Log.e("MainActivity", "onRemovePassword coroutine: isRoot=$isRoot")
                                    if (isRoot) {
                                         val isCurrent = connectionState.isConnected && connectionState.ssid.equals(ssid, ignoreCase = true)
                                         if (isCurrent) {
                                             connectionState = WifiConnectionState()
                                             scannerConnectedSignal = 0
                                         }
                                         withContext(Dispatchers.IO) {
                                             repository.forgetNetwork(ssid, bssid)
                                         }
                                         if (isCurrent) {
                                             // Đợi WiFi bật lại hoàn toàn sau chu kỳ tắt/bật
                                             withContext(Dispatchers.IO) {
                                                 val deadline = System.currentTimeMillis() + 6000L
                                                 while (System.currentTimeMillis() < deadline) {
                                                     if (repository.isWifiEnabled()) break
                                                     Thread.sleep(150)
                                                 }
                                             }
                                             kotlinx.coroutines.delay(1200) // Chờ card WiFi ổn định tín hiệu
                                             refreshScannedNetworksAndWait(forceRefresh = true) // Quét mới trực tiếp từ phần cứng
                                         } else {
                                             reloadScannerFromCache()
                                         }
                                         ToastHelper.show(this@MainActivity, "Đã xóa và quên mạng '$ssid'")
                                    } else {
                                         // Thiết bị CHƯA ROOT: Xóa mật khẩu trong app + hướng dẫn và tự động mở Cài đặt WiFi hệ thống
                                         withContext(Dispatchers.IO) {
                                             repository.forgetNetwork(ssid, bssid) // Xóa dữ liệu cục bộ trong app
                                         }
                                         ToastHelper.show(
                                             this@MainActivity,
                                             "Điện thoại chưa Root. Để xóa sạch khỏi hệ thống, hãy chọn mạng '$ssid' và nhấn 'Quên mạng' trong Cài đặt sắp mở.",
                                             ToastDuration.LONG
                                         )
                                         kotlinx.coroutines.delay(2000) // Chờ người dùng đọc thông tin hướng dẫn
                                         
                                         // Mở cài đặt WiFi hệ thống
                                         try {
                                             val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                                 flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                             }
                                             startActivity(intent)
                                         } catch (e: Exception) {
                                             e.printStackTrace()
                                         }
                                         reloadScannerFromCache()
                                    }
                                } finally {
                                    isSavingPassword = false
                                }
                            }
                        },


                        onResolvePassword = { ssid, bssid ->
                            repository.resolveConnectionPassword(ssid, bssid = bssid)
                        },
                        onRefreshPasswordForBssid = { ssid, bssid ->
                            val key = "${ssid.lowercase()}|${bssid.lowercase()}"
                            refreshingPasswordApKey = key
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    repository.refreshSharedPasswordForBssid(ssid, bssid)
                                }
                                reloadScannerFromCache()
                                result
                            } finally {
                                refreshingPasswordApKey = null
                            }
                        }
                        )
                    }
                    2 -> {
                        SystemScannerScreen(
                            networks = systemScannerRows,
                            connectionState = connectionState,
                            scanStatusText = systemScanStatusText,
                            isScanning = isSystemScanning,
                            connectingApKey = systemConnectingApKey,
                            connectFailedApKeys = systemConnectFailedApKeys,
                            rootConnectAvailable = rootStatus == RootStatus.GRANTED,
                            isPasswordBusy = isSavingPassword,
                            onRefreshScan = { startSystemScanFromButton() },
                            onConnectNetwork = { ssid, bssid, password ->
                                if (systemConnectingApKey != null || isSystemScanning) return@SystemScannerScreen
                                val key = "${ssid.lowercase()}|${bssid.lowercase()}"
                                coroutineScope.launch {
                                    systemConnectingApKey = key
                                    try {
                                        val isRoot = withContext(Dispatchers.IO) { repository.isRootAvailable() }
                                        if (isRoot) {
                                            val ap = systemScannedList.find { it.ssid == ssid && it.bssid == bssid }
                                                ?: systemScannedList.find { it.ssid == ssid }
                                            val psk = password?.trim()?.takeIf { it.isNotEmpty() }
                                                ?: repository.getSavedWifiPassword(ssid, ap?.bssid ?: bssid)
                                            val result = withContext(Dispatchers.IO) {
                                                repository.connectToNetwork(
                                                    ssid = ssid,
                                                    password = psk,
                                                    securityHint = ap?.securityType,
                                                    bssid = ap?.bssid ?: bssid
                                                )
                                            }
                                            ToastHelper.show(this@MainActivity, result.message)
                                            if (result.success) {
                                                systemConnectFailedApKeys = systemConnectFailedApKeys - key
                                            } else {
                                                systemConnectFailedApKeys = systemConnectFailedApKeys + key
                                            }
                                        } else {
                                            val ap = systemScannedList.find { it.ssid == ssid && it.bssid == bssid }
                                                ?: systemScannedList.find { it.ssid == ssid }
                                            val psk = password?.trim()?.takeIf { it.isNotEmpty() }
                                                ?: repository.getSavedWifiPassword(ssid, ap?.bssid ?: bssid)
                                            ToastHelper.show(this@MainActivity, "Đang yêu cầu hệ thống kết nối tới '$ssid'...")
                                            withContext(Dispatchers.IO) {
                                                repository.connectToSsidViaSpecifier(
                                                    ssid = ssid,
                                                    password = psk,
                                                    securityHint = ap?.securityType
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        ToastHelper.show(this@MainActivity, "Lỗi kết nối: ${e.localizedMessage}")
                                    } finally {
                                        systemConnectingApKey = null
                                        refreshSystemNetworksAndWait(forceRefresh = false)
                                        connectionState = repository.getCurrentConnectionState()
                                    }
                                }
                            },
                            onHasSystemCredential = { ssid ->
                                repository.hasStoredCredential(ssid)
                            },
                            onGetSavedPassword = { ssid, bssid ->
                                repository.getSavedWifiPassword(ssid, bssid)
                            },
                            onSavePassword = { ssid, password, bssid ->
                                if (isSavingPassword) return@SystemScannerScreen
                                coroutineScope.launch {
                                    isSavingPassword = true
                                    try {
                                        withContext(Dispatchers.IO) {
                                            repository.saveWifiPassword(ssid, password, bssid)
                                            repository.suggestNetworks(repository.getAllowedSsids().toList())
                                        }
                                        refreshSystemNetworksAndWait(forceRefresh = false)
                                    } finally {
                                        isSavingPassword = false
                                    }
                                }
                            },
                            onRemovePassword = { ssid, bssid ->
                                if (isSavingPassword) return@SystemScannerScreen
                                coroutineScope.launch {
                                    isSavingPassword = true
                                    systemConnectFailedApKeys = systemConnectFailedApKeys.filter { key ->
                                        val (storedSsid, _) = com.antigravity.wifimanager.data.WifiCredentialKeys.parseStorageKey(key)
                                        !storedSsid.trim().equals(ssid.trim(), ignoreCase = true)
                                    }.toSet()
                                    try {
                                        val isRoot = withContext(Dispatchers.IO) { repository.isRootAvailable() }
                                        if (isRoot) {
                                            val isCurrent = connectionState.isConnected && connectionState.ssid.equals(ssid, ignoreCase = true)
                                            if (isCurrent) {
                                                connectionState = WifiConnectionState()
                                            }
                                            withContext(Dispatchers.IO) {
                                                repository.forgetNetwork(ssid, bssid)
                                            }
                                            if (isCurrent) {
                                                withContext(Dispatchers.IO) {
                                                    val deadline = System.currentTimeMillis() + 6000L
                                                    while (System.currentTimeMillis() < deadline) {
                                                        if (repository.isWifiEnabled()) break
                                                        Thread.sleep(150)
                                                    }
                                                }
                                                kotlinx.coroutines.delay(1200)
                                                refreshSystemNetworksAndWait(forceRefresh = true)
                                            } else {
                                                refreshSystemNetworksAndWait(forceRefresh = false)
                                            }
                                            ToastHelper.show(this@MainActivity, "Đã xóa và quên mạng '$ssid'")
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                repository.forgetNetwork(ssid, bssid)
                                            }
                                            ToastHelper.show(
                                                this@MainActivity,
                                                "Vui lòng chọn mạng '$ssid' và nhấn 'Quên mạng' trong Cài đặt sắp mở.",
                                                ToastDuration.LONG
                                            )
                                            kotlinx.coroutines.delay(2000)
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            refreshSystemNetworksAndWait(forceRefresh = false)
                                        }
                                    } finally {
                                        isSavingPassword = false
                                    }
                                }
                            },
                            onOpenWifiSettings = {
                                repository.openSystemWifiSettings()
                            },
                            onOpenWifiPanel = {
                                repository.openSystemWifiPanel()
                            }
                        )
                    }
                    3 -> HistoryScreen(
                        historyLogs = historyLogs,
                        isClearingHistory = isClearingHistory,
                        onClearHistory = {
                            if (isClearingHistory) return@HistoryScreen
                            coroutineScope.launch {
                                isClearingHistory = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.clearHistory()
                                    }
                                    historyLogs = emptyList()
                                    ToastHelper.show(
                                        this@MainActivity,
                                        "Đã xóa toàn bộ lịch sử",
                                        ToastDuration.SHORT
                                    )
                                } finally {
                                    isClearingHistory = false
                                }
                            }
                        }
                    )
                    4 -> SettingsScreen(
                        threshold = threshold,
                        autoSwitchEnabled = autoSwitchEnabled,
                        prefer5GhzEnabled = prefer5GhzEnabled,
                        sharedWifiEnabled = sharedWifiEnabled,
                        wifiMasterEnabled = wifiMasterEnabled,
                        sharedWifiOfflineEnabled = sharedWifiOfflineEnabled,
                        sharedWifiOfflineCount = sharedWifiOfflineCount,
                        sharedWifiOfflineRadiusKm = sharedWifiOfflineRadiusKm,
                        sharedWifiOfflineStorageMb = sharedWifiOfflineStorageMb,
                        sharedWifiOfflineStorageUsedMb = sharedWifiOfflineStorageUsedMb,
                        sharedWifiOfflineMaxNetworks = sharedWifiOfflineMaxNetworks,
                        sharedWifiPrefetching = sharedWifiPrefetching,
                        isClearingOffline = isClearingOffline,
                        isRequestingRoot = isRequestingRoot,
                        sharedWifiPrefetchProgress = sharedWifiPrefetchProgress,
                        sharedWifiApiUrl = sharedWifiApiUrl,
                        sharedWifiApiKey = sharedWifiApiKey,
                        rootStatus = rootStatus,
                        isBatteryOptimized = batteryOptimized,
                        monitoringEnabled = repository.isMonitoringEnabled(),
                        onThresholdChange = { valNew ->
                            repository.setThreshold(valNew)
                            threshold = valNew
                        },
                        onAutoSwitchToggle = { valNew ->
                            repository.setAutoSwitchEnabled(valNew)
                            autoSwitchEnabled = valNew
                        },
                        onPrefer5GhzToggle = { valNew ->
                            repository.setPrefer5GhzEnabled(valNew)
                            prefer5GhzEnabled = valNew
                        },
                        onSharedWifiToggle = { enabled ->
                            repository.setSharedWifiEnabled(enabled)
                            sharedWifiEnabled = enabled
                        },
                        onWifiMasterToggle = { enabled ->
                            repository.setWifiMasterEnabled(enabled)
                            wifiMasterEnabled = enabled
                        },
                        onSharedWifiOfflineToggle = { enabled ->
                            repository.setSharedWifiOfflineEnabled(enabled)
                            sharedWifiOfflineEnabled = enabled
                        },
                        onOfflineRadiusKmChange = { km ->
                            repository.setSharedWifiOfflineRadiusKm(km)
                            sharedWifiOfflineRadiusKm = km
                        },
                        onOfflineStorageMbChange = { mb ->
                            repository.setSharedWifiOfflineMaxStorageMb(mb)
                            sharedWifiOfflineStorageMb = mb
                            sharedWifiOfflineMaxNetworks = repository.getSharedWifiOfflineMaxNetworks()
                        },
                        onClearSharedWifiOffline = {
                            if (isClearingOffline) return@SettingsScreen
                            coroutineScope.launch {
                                isClearingOffline = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.clearSharedWifiOfflineCache()
                                    }
                                    sharedWifiOfflineCount = 0
                                    sharedWifiOfflineStorageUsedMb = 0.0
                                    if (currentTab == 1) reloadScannerFromCache()
                                    ToastHelper.show(
                                        this@MainActivity,
                                        "Đã xóa bộ nhớ WiFi offline",
                                        ToastDuration.SHORT
                                    )
                                } finally {
                                    isClearingOffline = false
                                }
                            }
                        },
                        onPrefetchSharedWifiArea = {
                            if (sharedWifiPrefetching) return@SettingsScreen
                            coroutineScope.launch {
                                sharedWifiPrefetching = true
                                sharedWifiPrefetchProgress = 0 to 9
                                val result = withContext(Dispatchers.IO) {
                                    repository.prefetchSharedWifiArea { done, total ->
                                        withContext(Dispatchers.Main) {
                                            sharedWifiPrefetchProgress = done to total
                                        }
                                    }
                                }
                                sharedWifiPrefetching = false
                                sharedWifiPrefetchProgress = null
                                sharedWifiOfflineCount = repository.getSharedWifiOfflineCount()
                                sharedWifiOfflineStorageUsedMb = repository.getSharedWifiOfflineStorageMb()
                                if (currentTab == 1) {
                                    reloadScannerFromCache()
                                }
                                val msg = when {
                                    result.skipped -> result.reason ?: "Không tải được"
                                    result.success -> "Đã tải ${result.pointsFetched} điểm (${result.radiusKm} km)\n" +
                                        "+${result.addedSinceStart.coerceAtLeast(0)} mạng mới\n" +
                                        "Tổng offline: ${result.offlineTotalStored} (" +
                                        "${"%.1f".format(repository.getSharedWifiOfflineStorageMb())} MB)"
                                    else -> "Tải xong"
                                }
                                ToastHelper.show(this@MainActivity, msg)
                            }
                        },
                        onSharedWifiUrlChange = { url ->
                            repository.setSharedWifiApiUrl(url)
                            sharedWifiApiUrl = url
                        },
                        onSharedWifiApiKeyChange = { key ->
                            repository.setSharedWifiApiKey(key)
                            sharedWifiApiKey = key
                        },
                        onRequestRoot = {
                            if (isRequestingRoot) return@SettingsScreen
                            coroutineScope.launch {
                                isRequestingRoot = true
                                try {
                                    val granted = withContext(Dispatchers.IO) {
                                        repository.requestRootAccess()
                                    }
                                    rootStatus = if (granted) RootStatus.GRANTED else repository.getRootStatus()
                                    if (granted && currentTab == 1) {
                                        reloadScannerFromCache()
                                    }
                                    val msg = when (rootStatus) {
                                        RootStatus.GRANTED -> "Root đã được cấp!"
                                        RootStatus.DENIED -> "Chưa cấp Root — hãy cho phép trong Magisk/SuperSU"
                                        RootStatus.UNAVAILABLE -> "Thiết bị không hỗ trợ Root"
                                    }
                                    ToastHelper.show(this@MainActivity, msg)
                                } finally {
                                    isRequestingRoot = false
                                }
                            }
                        },
                        onRequestBatteryExemption = {
                            requestIgnoreBatteryOptimizations()
                            batteryOptimized = !isIgnoringBatteryOptimizations()
                        },
                        autoUpdateIntervalDays = autoUpdateIntervalDays,
                        lastAutoUpdateMs = lastAutoUpdateMs,
                        isAutoUpdating = isAutoUpdating,
                        onAutoUpdateIntervalChange = { days ->
                            repository.setAutoUpdateIntervalDays(days)
                            autoUpdateIntervalDays = days
                            WifiScheduler.reschedule(this@MainActivity, days)
                        },
                        onTriggerManualUpdate = {
                            if (isAutoUpdating || sharedWifiPrefetching) return@SettingsScreen
                            coroutineScope.launch {
                                isAutoUpdating = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        repository.prefetchSharedWifiArea { done, total ->
                                            withContext(Dispatchers.Main) {
                                                sharedWifiPrefetchProgress = done to total
                                            }
                                        }
                                    }
                                    lastAutoUpdateMs = repository.getLastAutoUpdateMs()
                                    sharedWifiOfflineCount = repository.getSharedWifiOfflineCount()
                                    sharedWifiOfflineStorageUsedMb = repository.getSharedWifiOfflineStorageMb()
                                    if (currentTab == 1) reloadScannerFromCache()
                                    val msg = when {
                                        result.skipped -> result.reason ?: "Không tải được (cần Internet + GPS)"
                                        result.success -> "Đã cập nhật ${result.pointsFetched} điểm (${result.radiusKm} km)\n" +
                                            "+${result.addedSinceStart.coerceAtLeast(0)} mạng mới • Tổng: ${result.offlineTotalStored}"
                                        else -> "Hoàn tất"
                                    }
                                    ToastHelper.show(this@MainActivity, msg)
                                } finally {
                                    isAutoUpdating = false
                                    sharedWifiPrefetchProgress = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
        repository.releaseSpecifierConnection()
        super.onDestroy()
    }
}
