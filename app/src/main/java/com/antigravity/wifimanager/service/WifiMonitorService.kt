package com.antigravity.wifimanager.service

import android.app.Notification
import android.util.Log
import com.antigravity.wifimanager.data.CaptivePortalBypasser
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antigravity.wifimanager.MainActivity
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiAutoSwitcher
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiMonitorService : Service() {

    companion object {
        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_SCAN_NOW = "SCAN_NOW"
        private const val PERIODIC_CHECK_INTERVAL_MS = 60_000L   // Tăng từ 20s -> 60s để siêu tiết kiệm pin
        private const val STATE_DEBOUNCE_MS = 6_000L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 30_000L
        private const val SIGNAL_NOTIFY_DELTA = 5
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var repository: WifiRepository
    private lateinit var autoSwitcher: WifiAutoSwitcher
    private val _connectionState = MutableStateFlow(WifiConnectionState())
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val binder = LocalBinder()
    private var isRegistered = false
    private var lastBypassAttemptSsid = ""
    private var lastBypassAttemptTimeMs = 0L
    private var periodicJob: Job? = null
    private var switchInProgress = false
    private var isScreenReceiverRegistered = false
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d("WifiMonitorService", "📱 Màn hình BẬT: Khôi phục giám sát WiFi.")
                    startPeriodicMonitor()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d("WifiMonitorService", "📱 Màn hình TẮT: Tạm dừng quét WiFi định kỳ để siêu tiết kiệm pin.")
                    periodicJob?.cancel() // Dừng quét định kỳ hoàn toàn
                }
            }
        }
    }

    private var lastStateCheckMs = 0L
    private var lastNotificationMs = 0L
    private var lastNotifiedSignal = -1
    private var lastNotifiedSsid = ""
    private var lastNotifiedConnected = false
    private var pendingNotificationJob: Job? = null

    /** v2: IMPORTANCE_LOW để hiện icon trên thanh trạng thái (v1 dùng MIN — gần như ẩn). */
    private val channelId = "wifi_monitor_channel_v2"
    private val notificationId = 1001
    private val alertNotificationId = 1002

    inner class LocalBinder : Binder() {
        fun getService(): WifiMonitorService = this@WifiMonitorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Receiver lắng nghe 2 sự kiện:
     * - SCAN_RESULTS_AVAILABLE_ACTION: OS vừa hoàn thành quét WiFi → cập nhật cache + kiểm tra auto-switch ngay
     * - NETWORK_STATE_CHANGED_ACTION: trạng thái kết nối thay đổi → cập nhật connection state
     */
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    // OS vừa có kết quả scan mới — cập nhật ngay, không chờ
                    onNewScanResultsAvailable()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    updateConnectionState(force = true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = WifiRepository(this)
        autoSwitcher = WifiAutoSwitcher(repository)
        createNotificationChannel()

        val monitoringOn = repository.isMonitoringEnabled()
        startForeground(
            notificationId,
            buildNotification(
                if (monitoringOn) "Đang khởi động giám sát..." else "Giám sát WiFi đã tắt",
                0
            )
        )
        if (!monitoringOn) {
            stopSelf()
            return
        }

        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)  // realtime scan results
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)   // connection state changes
        }
        ContextCompat.registerReceiver(
            this,
            wifiReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true

        // Đăng ký Screen On/Off receiver để siêu tiết kiệm pin
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, screenFilter)
        isScreenReceiverRegistered = true

        // Xác định trạng thái màn hình hiện tại khi khởi chạy dịch vụ
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isScreenOn = pm.isInteractive

        updateConnectionState(force = true)
        startPeriodicMonitor()
    }

    /**
     * Được gọi mỗi khi OS broadcast SCAN_RESULTS_AVAILABLE_ACTION.
     * Cập nhật cached scan + kiểm tra điều kiện auto-switch ngay lập tức.
     */
    private fun onNewScanResultsAvailable() {
        serviceScope.launch {
            // Đọc kết quả scan mới từ OS vào cache của repository (không block)
            val freshScan = repository.scanNearbyNetworks(forceRefresh = false)

            val state = repository.getCurrentConnectionState()
            _connectionState.value = state

            val now = System.currentTimeMillis()
            maybeUpdateNotification(state, now, force = false)

            if (state.isConnected) {
                checkAndTriggerCaptivePortalBypass(state)
            }

            // Kiểm tra và thực hiện auto-switch nếu đủ điều kiện
            if (state.isConnected && repository.isAutoSwitchEnabled() && !switchInProgress) {
                val threshold = repository.getThreshold()
                val signalWeak = state.signalPercent < threshold
                val shouldPrefer5G = repository.isPrefer5GhzEnabled() && !signalWeak
                if (signalWeak || shouldPrefer5G) {
                    evaluateAndSwitch(state, freshScan, fiveGhzUpgradeOnly = shouldPrefer5G && !signalWeak)
                }
            }
        }
    }

    private fun startPeriodicMonitor() {
        periodicJob?.cancel()
        if (!isScreenOn) return // Nếu màn hình tắt thì không quét định kỳ

        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(PERIODIC_CHECK_INTERVAL_MS)
                
                val state = repository.getCurrentConnectionState()
                val threshold = repository.getThreshold()

                // CHỈ quét WiFi khi: Màn hình đang bật AND (chưa kết nối WiFi HOẶC sóng hiện tại yếu hơn ngưỡng yếu)
                if (isScreenOn && (!state.isConnected || state.signalPercent < threshold)) {
                    Log.d("WifiMonitorService", "⚡ Sóng WiFi yếu (${state.signalPercent}%) hoặc chưa kết nối. Khởi chạy quét tìm mạng mạnh hơn...")
                    @Suppress("DEPRECATION")
                    try {
                        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        wm.startScan()
                    } catch (_: Exception) {}
                } else {
                    Log.d("WifiMonitorService", "🔋 Mạng hiện tại đang rất tốt (${state.signalPercent}%). Bỏ qua quét WiFi để siêu tiết kiệm pin.")
                }

                // Cập nhật trạng thái kết nối độc lập (không phụ thuộc scan)
                updateConnectionState(force = false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                repository.setMonitoringEnabled(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCAN_NOW -> {
                serviceScope.launch {
                    // Trigger scan mới, đọc ngay kết quả hiện tại + cập nhật cache
                    repository.scanNearbyNetworks(forceRefresh = true)
                    updateConnectionState(force = true)
                }
            }
        }
        return START_STICKY
    }

    private fun updateConnectionState(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStateCheckMs < STATE_DEBOUNCE_MS) return
        lastStateCheckMs = now

        val state = repository.getCurrentConnectionState()
        _connectionState.value = state

        maybeUpdateNotification(state, now, force)

        if (state.isConnected) {
            checkAndTriggerCaptivePortalBypass(state)
        }

        if (state.isConnected && repository.isAutoSwitchEnabled() && !switchInProgress) {
            val threshold = repository.getThreshold()
            val signalWeak = state.signalPercent < threshold
            val shouldPrefer5G = repository.isPrefer5GhzEnabled() && signalWeak.not()
            if (signalWeak || shouldPrefer5G) {
                // Dùng cache scan có sẵn thay vì scan mới (tránh double-scan)
                val cachedScan = repository.getCachedScanResults()
                evaluateAndSwitch(state, cachedScan, fiveGhzUpgradeOnly = shouldPrefer5G && !signalWeak)
            }
        }
    }

    private fun maybeUpdateNotification(state: WifiConnectionState, now: Long, force: Boolean) {
        val signalDelta = kotlin.math.abs(state.signalPercent - lastNotifiedSignal)
        val ssidChanged = state.ssid != lastNotifiedSsid
        val connectionChanged = state.isConnected != lastNotifiedConnected
        val isImportantChange = ssidChanged || connectionChanged

        val shouldUpdate = isImportantChange || force ||
            (now - lastNotificationMs >= NOTIFICATION_MIN_INTERVAL_MS) ||
            (signalDelta >= SIGNAL_NOTIFY_DELTA && now - lastNotificationMs >= 5000L)

        if (!shouldUpdate) return

        // Hủy bất kỳ tác vụ cập nhật thông báo đang chờ nào
        pendingNotificationJob?.cancel()

        val timeSinceLastMs = now - lastNotificationMs
        val minIntervalMs = 1500L

        if (timeSinceLastMs >= minIntervalMs) {
            // Đã qua thời gian cooldown, cập nhật thông báo ngay lập tức
            performNotificationUpdate(state, now)
        } else {
            // Trong thời gian cooldown, hoãn tác vụ cập nhật để tránh spam/rate limit
            val delayMs = minIntervalMs - timeSinceLastMs
            pendingNotificationJob = serviceScope.launch {
                delay(delayMs)
                val currentState = repository.getCurrentConnectionState()
                performNotificationUpdate(currentState, System.currentTimeMillis())
            }
        }
    }

    private fun performNotificationUpdate(state: WifiConnectionState, now: Long) {
        lastNotificationMs = now
        lastNotifiedSignal = state.signalPercent
        lastNotifiedSsid = state.ssid
        lastNotifiedConnected = state.isConnected

        val contentText = if (state.isConnected) {
            "Đang kết nối: ${state.ssid} (${state.signalPercent}%)"
        } else {
            "Ngoại tuyến - Chưa kết nối WiFi"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(
                notificationId,
                buildNotification(contentText, state.signalPercent)
            )
        } catch (e: Exception) {
            android.util.Log.e("WifiMonitorService", "Error posting notification", e)
        }
    }

    /**
     * Kiểm tra và thực hiện chuyển WiFi dùng cachedScan sẵn có.
     * Không gọi scanNearbyNetworks thêm — tránh double-scan làm chậm.
     */
    private fun evaluateAndSwitch(
        currentState: WifiConnectionState,
        cachedScan: List<WifiApInfo>,
        fiveGhzUpgradeOnly: Boolean = false
    ) {
        if (switchInProgress) return
        switchInProgress = true
        serviceScope.launch {
            try {
                if (fiveGhzUpgradeOnly && !autoSwitcher.canUpgradeTo5Ghz(currentState, cachedScan)) {
                    return@launch
                }
                autoSwitcher.attemptSwitch(
                    currentState = currentState,
                    cachedScanResults = cachedScan,
                    enforceCooldown = true,
                    onNotifyUser = { from, target ->
                        if (!repository.isRootAvailable()) {
                            sendSwitchAlertNotification(from, target)
                        }
                    }
                )
            } finally {
                switchInProgress = false
                updateConnectionState(force = true)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Giám sát WiFi nền",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Icon trên thanh trạng thái khi giám sát WiFi đang bật — không phát âm thanh"
                setShowBadge(true)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, percent: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WifiMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val scanIntent = PendingIntent.getService(
            this, 2,
            Intent(this, WifiMonitorService::class.java).apply { action = ACTION_SCAN_NOW },
            PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = when {
            percent >= 70 -> android.R.drawable.presence_online
            percent >= 45 -> android.R.drawable.presence_away
            else -> android.R.drawable.presence_busy
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("WiFi Auto-Switcher")
            .setContentText(content)
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_search, "Quét mạng", scanIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng giám sát", stopIntent)
            .build()
    }

    private fun sendSwitchAlertNotification(fromState: WifiConnectionState, targetAp: WifiApInfo) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                "wifi_alert_channel",
                "Cảnh báo sóng yếu",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nhắc khi cần xác nhận chuyển WiFi (không root)"
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }

        val settingsIntent = PendingIntent.getActivity(
            this, 3, Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "wifi_alert_channel")
            .setContentTitle("WiFi mạnh hơn khả dụng")
            .setContentText("'${fromState.ssid}' yếu (${fromState.signalPercent}%). Có thể chuyển sang '${targetAp.ssid}'.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setAutoCancel(true)
            .setContentIntent(settingsIntent)
            .build()

        notificationManager.notify(alertNotificationId, notification)
    }

    private fun checkAndTriggerCaptivePortalBypass(state: WifiConnectionState) {
        if (!state.isConnected) return
        
        val now = System.currentTimeMillis()
        if (state.ssid == lastBypassAttemptSsid && now - lastBypassAttemptTimeMs < 15_000L) {
            return
        }
        
        lastBypassAttemptSsid = state.ssid
        lastBypassAttemptTimeMs = now
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val redirectUrl = CaptivePortalBypasser.detectRedirectUrl(this@WifiMonitorService)
                if (!redirectUrl.isNullOrBlank()) {
                    lastBypassAttemptSsid = state.ssid
                    lastBypassAttemptTimeMs = System.currentTimeMillis()
                    
                    updateNotificationMessage("Đang tự vượt cổng chào WiFi '${state.ssid}'...")
                    
                    val result = CaptivePortalBypasser.attemptAutoBypass(this@WifiMonitorService, redirectUrl)
                    
                    if (result.success) {
                        Log.d("WifiMonitorService", "Captive portal auto-bypass success: ${result.message}")
                        sendBypassSuccessNotification(state.ssid, result.portalName ?: "WiFi")
                    } else {
                        Log.d("WifiMonitorService", "Captive portal auto-bypass failed: ${result.message}")
                    }
                    
                    updateConnectionState(force = true)
                }
            } catch (e: Exception) {
                Log.e("WifiMonitorService", "Error in captive portal bypass trigger", e)
            }
        }
    }

    private fun updateNotificationMessage(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(
                notificationId,
                buildNotification(message, 0)
            )
        } catch (_: Exception) {}
    }

    private fun sendBypassSuccessNotification(ssid: String, portalName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                "wifi_alert_channel",
                "Cảnh báo & Thông báo",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(alertChannel)
        }
        
        val notification = NotificationCompat.Builder(this, "wifi_alert_channel")
            .setContentTitle("Đã tự động kết nối Internet")
            .setContentText("Đã vượt qua trang xác thực '$portalName' trên WiFi '$ssid' thành công!")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(alertNotificationId, notification)
    }

    override fun onDestroy() {
        periodicJob?.cancel()
        if (isRegistered) {
            unregisterReceiver(wifiReceiver)
            isRegistered = false
        }
        if (isScreenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            isScreenReceiverRegistered = false
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}

