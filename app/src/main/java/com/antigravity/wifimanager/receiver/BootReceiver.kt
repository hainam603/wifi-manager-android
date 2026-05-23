package com.antigravity.wifimanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.wifimanager.data.WifiRepository
import com.antigravity.wifimanager.util.MonitorServiceStarter

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val repository = WifiRepository(context.applicationContext)
        if (!repository.isMonitoringEnabled()) return
        if (!MonitorServiceStarter.hasRequiredPermissions(context)) return

        MonitorServiceStarter.start(context)
    }
}
