package com.antigravity.wifimanager.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Toast nhiều dòng, rộng gần hết màn hình — tránh bị cắt 1–2 dòng như Toast hệ thống.
 */
object ToastHelper {

    enum class Duration {
        SHORT,
        LONG
    }

    fun show(
        context: Context,
        message: CharSequence,
        duration: Duration = Duration.LONG
    ) {
        val appContext = context.applicationContext
        val textView = TextView(appContext).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 15f
            val padH = dp(appContext, 20)
            val padV = dp(appContext, 14)
            setPadding(padH, padV, padH, padV)
            maxLines = 32
            isSingleLine = false
            ellipsize = null
            gravity = Gravity.CENTER
            maxWidth = (appContext.resources.displayMetrics.widthPixels * 0.92f).toInt()
            setLineSpacing(dp(appContext, 4).toFloat(), 1f)
            background = GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(appContext, 12).toFloat()
            }
        }

        val toast = Toast(appContext).apply {
            @Suppress("DEPRECATION")
            view = textView
            this.duration = if (duration == Duration.LONG) {
                Toast.LENGTH_LONG
            } else {
                Toast.LENGTH_SHORT
            }
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(appContext, 100))
        }
        toast.show()

        if (duration == Duration.LONG && message.length > 48) {
            Handler(Looper.getMainLooper()).postDelayed({ toast.show() }, 3_600)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
