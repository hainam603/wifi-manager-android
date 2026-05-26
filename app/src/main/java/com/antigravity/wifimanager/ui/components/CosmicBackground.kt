package com.antigravity.wifimanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.antigravity.wifimanager.ui.theme.CosmicBgEnd
import com.antigravity.wifimanager.ui.theme.CosmicBgStart

@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CosmicBgStart,
                        CosmicBgEnd
                    )
                )
            )
    ) {
        // Vẽ các đốm sáng mờ (ambient glow spots) phía sau để tạo chiều sâu kính mờ (Sci-Fi Glassmorphism)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Đốm sáng tím huyền ảo (CyberPurple Glow) ở góc trên bên phải
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1F8B5CF6), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.75f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.15f),
                radius = size.width * 0.75f
            )
            // Đốm sáng xanh ngọc (CyberCyan Glow) ở giữa bên trái
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1406B6D4), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.65f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.1f, size.height * 0.65f),
                radius = size.width * 0.65f
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
