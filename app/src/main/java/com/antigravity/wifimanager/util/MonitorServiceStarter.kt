package com.antigravity.wifimanager.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.antigravity.wifimanager.data.WifiRepository
import com.antigravity.wifimanager.service.WifiMonitorService

object MonitorServiceStarter {

    fun hasRequiredPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && nearby
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        val repository = WifiRepository(appContext)
        if (!hasRequiredPermissions(appContext)) return
        if (!hasNotificationPermission(appContext)) return

        repository.setMonitoringEnabled(true)
        val intent = Intent(appContext, WifiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        WifiRepository(appContext).setMonitoringEnabled(false)
        val intent = Intent(appContext, WifiMonitorService::class.java).apply {
            action = WifiMonitorService.ACTION_STOP
        }
        appContext.startService(intent)
    }
}
