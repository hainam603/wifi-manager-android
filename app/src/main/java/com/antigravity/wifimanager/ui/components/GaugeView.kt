package com.antigravity.wifimanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GaugeView(
    signalPercent: Int,
    connected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 12.dp
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
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "SignalAnimation"
    )

    // Tạo hiệu ứng thở phát sáng dạng xung radar ở tâm khi đã kết nối
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulsing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerOffset = Offset(this.size.width / 2, this.size.height / 2)
            val outerRadius = this.size.width / 2 - strokeWidth.toPx() - 4.dp.toPx()

            // 1. Vẽ vòng tròn xung phát sóng (Pulsing Radar Ripple) lan tỏa
            if (connected) {
                drawCircle(
                    color = activeColor.copy(alpha = pulseAlpha),
                    radius = outerRadius * pulseScale,
                    center = centerOffset
                )
            }

            // 2. Vẽ cung tròn nền (240 độ từ góc 150)
            drawArc(
                color = Color(0x0AFFFFFF),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // 3. Vẽ cung tròn chỉ số sóng thực tế với gradient sáng dần
            val sweep = (animatedPercent / 100f) * 240f
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.4f),
                            activeColor
                        )
                    ),
                    startAngle = 150f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Vẽ các vạch chia phong cách phi thuyền viễn tưởng (Technological Ticks)
            val totalTicks = 41
            val startAngle = 150f
            val totalSweep = 240f
            val tickLength = 5.dp.toPx()
            val tickRadius = outerRadius + strokeWidth.toPx() + 6.dp.toPx()

            for (i in 0 until totalTicks) {
                val tickAngle = startAngle + (i.toFloat() / (totalTicks - 1)) * totalSweep
                val tickAngleRad = Math.toRadians(tickAngle.toDouble())
                
                // Vạch sáng theo chỉ số % sóng
                val isTickActive = (i.toFloat() / (totalTicks - 1)) * 100 <= animatedPercent && connected
                val tickColor = if (isTickActive) activeColor else Color(0x12FFFFFF)
                val tickWidth = if (isTickActive) 2.dp.toPx() else 1.dp.toPx()

                val startX = centerOffset.x + (tickRadius - tickLength) * cos(tickAngleRad).toFloat()
                val startY = centerOffset.y + (tickRadius - tickLength) * sin(tickAngleRad).toFloat()
                val endX = centerOffset.x + tickRadius * cos(tickAngleRad).toFloat()
                val endY = centerOffset.y + tickRadius * sin(tickAngleRad).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // 5. Thông tin cường độ sóng ở tâm vòng quay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (connected) "${signalPercent}%" else "Ngoại tuyến",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = activeColor,
                letterSpacing = (-1).sp
            )
            Text(
                text = if (connected) "CƯỜNG ĐỘ SÓNG" else "CHƯA KẾT NỐI",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )
        }
    }
}

