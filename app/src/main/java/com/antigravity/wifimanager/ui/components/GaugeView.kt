package com.antigravity.wifimanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.ui.theme.*

@Composable
fun GaugeView(
    signalPercent: Int,
    connected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 16.dp
) {
    // Tự động đổi màu động dựa vào chất lượng kết nối
    val activeColor = when {
        !connected -> WifiDisconnected
        signalPercent >= 70 -> WifiGood
        signalPercent >= 45 -> WifiMedium
        else -> WifiWeak
    }

    // Tạo hiệu ứng chuyển động mượt mà khi phần trăm sóng thay đổi
    val animatedPercent by animateFloatAsState(
        targetValue = if (connected) signalPercent.toFloat() else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "SignalAnimation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Vẽ cung tròn nền (240 độ từ góc 150)
            drawArc(
                color = Color(0x11FFFFFF),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // Vẽ cung tròn chỉ số sóng thực tế
            val sweep = (animatedPercent / 100f) * 240f
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.6f),
                            activeColor
                        )
                    ),
                    startAngle = 150f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Thông tin hiển thị ở tâm đồng hồ
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (connected) "${signalPercent}%" else "Ngoại tuyến",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor
            )
            Text(
                text = if (connected) "CƯỜNG ĐỘ SÓNG" else "CHƯA KẾT NỐI",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}
