package com.antigravity.wifimanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.antigravity.wifimanager.data.SharedWifiRepository
import com.antigravity.wifimanager.data.WifiRepository
import com.antigravity.wifimanager.util.WifiScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver nhận lịch AlarmManager để tự động prefetch dữ liệu WiFi offline.
 * Chỉ chạy khi có Internet + dữ liệu offline đang bật.
 * Nếu không đủ điều kiện, bỏ qua và chờ lịch kế tiếp.
 */
class WifiAutoScheduleReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WifiScheduler.ACTION_AUTO_UPDATE_WIFI_DATA) return

        val appContext = context.applicationContext
        val repository = WifiRepository(appContext)

        // Kiểm tra tính năng offline có bật không
        if (!repository.isSharedWifiOfflineEnabled()) return

        // Kiểm tra Internet
        if (!isNetworkAvailable(appContext)) return

        // Chạy prefetch trên background coroutine
        val pendingResult = goAsync()
        scope.launch {
            try {
                repository.runOfflinePrefetch()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
