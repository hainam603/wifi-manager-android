package com.antigravity.wifimanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antigravity.wifimanager.ui.theme.CardBackground
import com.antigravity.wifimanager.ui.theme.CardBorder

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = CardBackground,
    borderBrush: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val finalBorderBrush = borderBrush ?: Brush.verticalGradient(
        colors = listOf(
            CardBorder,
            Color(0x05FFFFFF)
        )
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                brush = finalBorderBrush,
                shape = RoundedCornerShape(24.dp)
            ),
        content = content
    )
}
