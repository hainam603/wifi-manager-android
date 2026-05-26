package com.antigravity.wifimanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiCredentialKeys
import com.antigravity.wifimanager.ui.components.GaugeView
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.components.WifiBandBadge
import com.antigravity.wifimanager.ui.theme.*
import com.antigravity.wifimanager.util.ToastHelper

@Composable
fun DashboardScreen(
    state: WifiConnectionState,
    wifiPassword: String? = null,
    isServiceRunning: Boolean,
    isManualScanLoading: Boolean = false,
    isTogglingService: Boolean = false,
    onToggleService: () -> Unit,
    onManualScan: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tiêu đề đầu trang thiết kế phong cách Cyber Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isServiceRunning) CyberEmerald else WifiDisconnected,
                                CircleShape
                            )
                    )
                    Text(
                        text = "WiFi Auto-Switcher",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
                Text(
                    text = "Giám sát sóng WiFi thông minh & chuyển mạch nền",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            // Icon trạng thái hoạt động dịch vụ nền phát sáng nhẹ
            IconButton(
                onClick = onToggleService,
                enabled = !isTogglingService,
                modifier = Modifier
                    .background(
                        if (isServiceRunning) Color(0x1A10B981) else Color(0x0CFFFFFF),
                        CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (isServiceRunning) Color(0x3310B981) else Color(0x18FFFFFF),
                        shape = CircleShape
                    )
            ) {
                if (isTogglingService) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isServiceRunning) CyberEmerald else CyberPurple
                    )
                } else {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Power else Icons.Default.PowerOff,
                        contentDescription = "Trạng thái Dịch vụ",
                        tint = if (isServiceRunning) CyberEmerald else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Vòng tròn đo sóng GaugeView thiết kế radar sinh động
        GaugeView(
            signalPercent = state.signalPercent,
            connected = state.isConnected,
            size = 208.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Trạng thái kết nối chữ lớn sang trọng
        Text(
            text = if (state.isConnected) {
                "TRẠNG THÁI: KẾT NỐI AN TOÀN"
            } else {
                "TRẠNG THÁI: NGOẠI TUYẾN"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.isConnected) CyberCyan else TextSecondary,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .background(
                    if (state.isConnected) Color(0x1406B6D4) else Color(0x0AFFFFFF),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // 4. Khung tiêu đề "MẠNG KẾT NỐI HIỆN TẠI"
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color(0x0CFFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MẠNG KẾT NỐI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberIndigo,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = if (state.isConnected) state.ssid else "Chưa kết nối",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.isConnected && state.frequencyMhz > 0) {
                                WifiBandBadge(is5GHz = state.is5GHz)
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (state.isConnected) CyberCyan else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 5. Stat Grid 6 ô kính mờ thông minh, sang trọng
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dòng 1: Cường độ sóng & Băng tần
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val signalDesc = when {
                        !state.isConnected -> "Ngoại tuyến"
                        state.signalPercent >= 70 -> "Rất Khỏe"
                        state.signalPercent >= 45 -> "Khá Tốt"
                        else -> "Yếu"
                    }
                    val signalColor = when {
                        !state.isConnected -> WifiDisconnected
                        state.signalPercent >= 70 -> CyberEmerald
                        state.signalPercent >= 45 -> CyberAmber
                        else -> CyberRose
                    }
                    StatCell(
                        title = "TÍN HIỆU",
                        value = if (state.isConnected) "${state.signalPercent}% ($signalDesc)" else "0%",
                        icon = Icons.Default.Wifi,
                        iconColor = signalColor
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCell(
                        title = "BĂNG TẦN",
                        value = if (state.isConnected) "${state.frequencyMhz} MHz" else "Chưa có",
                        icon = Icons.Default.Router,
                        iconColor = CyberPurple
                    )
                }
            }

            // Dòng 2: IP Address & DNS Server
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCell(
                        title = "ĐỊA CHỈ IP",
                        value = if (state.isConnected) state.ipAddress else "—",
                        icon = Icons.Default.Info,
                        iconColor = CyberCyan
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCell(
                        title = "DNS SERVER",
                        value = if (state.isConnected) state.dnsServers.substringBefore(",") else "—",
                        icon = Icons.Default.Dns,
                        iconColor = CyberIndigo
                    )
                }
            }

            // Dòng 3: Bảo mật & Mật khẩu WiFi (Với nút Sao Chép)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val authDesc = if (state.isConnected) {
                        if (state.authType.contains("WPA", ignoreCase = true)) "WPA2/WPA3" else "Mạng Mở"
                    } else "Chưa rõ"
                    StatCell(
                        title = "BẢO MẬT",
                        value = authDesc,
                        icon = Icons.Default.Lock,
                        iconColor = if (state.isConnected && authDesc != "Mạng Mở") CyberEmerald else CyberAmber
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    val psk = wifiPassword ?: ""
                    val hasPassword = psk.isNotBlank()
                    StatCell(
                        title = "MẬT KHẨU",
                        value = if (hasPassword) psk else "Không có",
                        icon = Icons.Default.Lock,
                        iconColor = CyberPink,
                        action = if (hasPassword) {
                            {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("WiFi Password", psk)
                                        clipboard.setPrimaryClip(clip)
                                        ToastHelper.show(context, "Đã sao chép mật khẩu WiFi!")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Password",
                                        tint = CyberPink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Nút điều khiển nhanh bên dưới phong cách Gradient bo cong
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nút quét lại viền kính mờ
            OutlinedButton(
                onClick = onManualScan,
                enabled = !isManualScanLoading && !isTogglingService,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        color = Color(0x22FFFFFF),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary,
                    disabledContentColor = TextSecondary
                )
            ) {
                if (isManualScanLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Đang quét...", fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Quét lại", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Nút Bật/Tắt dịch vụ nền gradient Cyber
            Button(
                onClick = onToggleService,
                enabled = !isManualScanLoading && !isTogglingService,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isServiceRunning) {
                                listOf(CyberRose, Color(0xFFF43F5E))
                            } else {
                                listOf(CyberIndigo, CyberPurple)
                            }
                        )
                    ),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (isTogglingService) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Đang sửa...", fontSize = 14.sp, color = Color.White)
                } else {
                    Text(
                        text = if (isServiceRunning) "Tắt giám sát" else "Bật giám sát",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    action: (@Composable () -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0x0CFFFFFF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Biểu tượng tròn với nền phát sáng mờ
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, iconColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Nội dung văn bản
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Thao tác phụ nếu có
            if (action != null) {
                action()
            }
        }
    }
}

