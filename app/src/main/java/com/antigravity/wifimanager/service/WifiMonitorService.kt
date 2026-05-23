package com.antigravity.wifimanager.service

import android.app.Notification
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
        private const val PERIODIC_CHECK_INTERVAL_MS = 45_000L
        private const val STATE_DEBOUNCE_MS = 12_000L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 45_000L
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
    private var periodicJob: Job? = null
    private var switchInProgress = false

    private var lastStateCheckMs = 0L
    private var lastNotificationMs = 0L
    private var lastNotifiedSignal = -1
    private var lastNotifiedSsid = ""

    /** v2: IMPORTANCE_LOW để hiện icon trên thanh trạng thái (v1 dùng MIN — gần như ẩn). */
    private val channelId = "wifi_monitor_channel_v2"
    private val notificationId = 1001
    private val alertNotificationId = 1002

    inner class LocalBinder : Binder() {
        fun getService(): WifiMonitorService = this@WifiMonitorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Chỉ lắng NETWORK_STATE_CHANGED — RSSI_CHANGED bắn quá dày, tốn pin
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                updateConnectionState(force = true)
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

        val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        ContextCompat.registerReceiver(
            this,
            wifiReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true

        updateConnectionState(force = true)
        startPeriodicMonitor()
    }

    private fun startPeriodicMonitor() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(PERIODIC_CHECK_INTERVAL_MS)
                updateConnectionState(force = true)
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

        if (state.isConnected && repository.isAutoSwitchEnabled() && !switchInProgress) {
            val threshold = repository.getThreshold()
            val signalWeak = state.signalPercent < threshold
            val shouldPrefer5G = repository.isPrefer5GhzEnabled() && signalWeak.not()
            if (signalWeak || shouldPrefer5G) {
                evaluateAndSwitch(state, fiveGhzUpgradeOnly = shouldPrefer5G && !signalWeak)
            }
        }
    }

    private fun maybeUpdateNotification(state: WifiConnectionState, now: Long, force: Boolean) {
        val signalDelta = kotlin.math.abs(state.signalPercent - lastNotifiedSignal)
        val ssidChanged = state.ssid != lastNotifiedSsid
        val shouldUpdate = force ||
            now - lastNotificationMs >= NOTIFICATION_MIN_INTERVAL_MS ||
            signalDelta >= SIGNAL_NOTIFY_DELTA ||
            ssidChanged

        if (!shouldUpdate) return

        lastNotificationMs = now
        lastNotifiedSignal = state.signalPercent
        lastNotifiedSsid = state.ssid

        val contentText = if (state.isConnected) {
            "Đang kết nối: ${state.ssid} (${state.signalPercent}%)"
        } else {
            "Ngoại tuyến - Chưa kết nối WiFi"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            notificationId,
            buildNotification(contentText, state.signalPercent)
        )
    }

    private fun evaluateAndSwitch(currentState: WifiConnectionState, fiveGhzUpgradeOnly: Boolean = false) {
        if (switchInProgress) return
        switchInProgress = true
        serviceScope.launch {
            try {
                if (fiveGhzUpgradeOnly) {
                    val scan = repository.scanNearbyNetworks(forceRefresh = false)
                    if (!autoSwitcher.canUpgradeTo5Ghz(currentState, scan)) {
                        return@launch
                    }
                }
                autoSwitcher.attemptSwitch(
                    currentState = currentState,
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

    override fun onDestroy() {
        periodicJob?.cancel()
        if (isRegistered) {
            unregisterReceiver(wifiReceiver)
            isRegistered = false
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}
