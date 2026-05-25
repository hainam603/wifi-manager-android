package com.antigravity.wifimanager.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.antigravity.wifimanager.receiver.WifiAutoScheduleReceiver

/**
 * Tiện ích lên lịch tự động cập nhật dữ liệu WiFi offline bằng AlarmManager.
 * Dùng setInexactRepeating để tiết kiệm pin (không cần exact timing).
 */
object WifiScheduler {

    const val ACTION_AUTO_UPDATE_WIFI_DATA = "com.antigravity.wifimanager.ACTION_AUTO_UPDATE_WIFI_DATA"
    private const val REQUEST_CODE = 7001

    /**
     * Lên lịch tự động cập nhật dữ liệu WiFi offline.
     * @param context Context
     * @param intervalDays Chu kỳ tính bằng ngày (0 = tắt tự động)
     */
    fun schedule(context: Context, intervalDays: Int) {
        cancel(context)
        if (intervalDays <= 0) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context)

        val intervalMs = intervalDays * 24L * 60L * 60L * 1000L
        val triggerAtMs = System.currentTimeMillis() + intervalMs

        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            intervalMs,
            pendingIntent
        )
    }

    /**
     * Hủy lịch tự động cập nhật.
     */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
    }

    /**
     * Hủy rồi lên lại lịch — dùng khi người dùng thay đổi chu kỳ cập nhật.
     */
    fun reschedule(context: Context, intervalDays: Int) {
        cancel(context)
        schedule(context, intervalDays)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WifiAutoScheduleReceiver::class.java).apply {
            action = ACTION_AUTO_UPDATE_WIFI_DATA
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
