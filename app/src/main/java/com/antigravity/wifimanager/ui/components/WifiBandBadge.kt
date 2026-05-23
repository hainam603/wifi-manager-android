package com.antigravity.wifimanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WifiBandBadge(
    is5GHz: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val bg = when {
        highlighted && is5GHz -> Color(0x44FFFFFF)
        highlighted && !is5GHz -> Color(0x33000000)
        is5GHz -> Color(0x3306B6D4)
        else -> Color(0x33F59E0B)
    }
    val textColor = when {
        highlighted -> Color.White
        is5GHz -> Color(0xFF22D3EE)
        else -> Color(0xFFFBBF24)
    }

    Text(
        text = if (is5GHz) "5G" else "2.4G",
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
}
