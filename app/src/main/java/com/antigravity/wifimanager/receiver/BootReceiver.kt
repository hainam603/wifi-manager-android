package com.antigravity.wifimanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.wifimanager.data.WifiRepository
import com.antigravity.wifimanager.util.MonitorServiceStarter
import com.antigravity.wifimanager.util.WifiScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val repository = WifiRepository(context.applicationContext)

        // Đặt lại lịch tự động cập nhật offline (AlarmManager bị xóa sau khi tắt máy)
        WifiScheduler.reschedule(context, repository.getAutoUpdateIntervalDays())

        if (!repository.isMonitoringEnabled()) return
        if (!MonitorServiceStarter.hasRequiredPermissions(context)) return

        MonitorServiceStarter.start(context)
    }
}
