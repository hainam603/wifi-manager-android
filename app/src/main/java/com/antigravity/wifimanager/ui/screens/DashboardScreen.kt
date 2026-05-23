package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiCredentialKeys
import com.antigravity.wifimanager.ui.components.GaugeView
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.components.WifiBandBadge
import com.antigravity.wifimanager.ui.theme.TextSecondary

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Tiêu đề đầu trang
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WiFi Auto-Switcher",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Giám sát sóng WiFi thông minh",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            
            // Icon trạng thái hoạt động dịch vụ nền
            IconButton(onClick = onToggleService, enabled = !isTogglingService) {
                if (isTogglingService) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Power else Icons.Default.PowerOff,
                        contentDescription = "Trạng thái Dịch vụ",
                        tint = if (isServiceRunning) MaterialTheme.colorScheme.primary else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vòng tròn đo sóng GaugeView vẽ Canvas sinh động
        GaugeView(
            signalPercent = state.signalPercent,
            connected = state.isConnected,
            size = 208.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Trạng thái hoạt động dạng chữ
        Text(
            text = if (state.isConnected) {
                "Trạng thái: Đang kết nối mạng an toàn"
            } else {
                "Trạng thái: Ngoại tuyến"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.isConnected) MaterialTheme.colorScheme.primary else TextSecondary
        )

        // Thẻ kính mờ hiển thị chi tiết kết nối WiFi hiện tại
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "KẾT NỐI HIỆN TẠI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (state.isConnected) state.ssid else "Chưa kết nối",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (state.isConnected && state.frequencyMhz > 0) {
                                WifiBandBadge(is5GHz = state.is5GHz)
                            }
                        }
                        Text(
                            text = when {
                                !state.isConnected -> "Vui lòng bật WiFi hoặc kết nối"
                                WifiCredentialKeys.isPlaceholderBssid(state.bssid) ->
                                    "BSSID: chưa đọc được — bấm Quét lại"
                                state.bssid.isBlank() -> "BSSID: —"
                                else -> "BSSID: ${state.bssid}"
                            },
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (state.isConnected) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Divider(color = Color(0x11FFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Độ bảo mật", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = if (state.isConnected) state.authType else "--",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Cường độ tín hiệu", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = if (state.isConnected) "${state.signalPercent}%" else "0%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (state.isConnected) {
                                if (state.signalPercent >= 70) Color(0xFF10B981)
                                else if (state.signalPercent >= 45) Color(0xFFF59E0B)
                                else Color(0xFFEF4444)
                            } else TextSecondary
                        )
                    }
                }

                if (state.isConnected) {
                    Divider(color = Color(0x11FFFFFF))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(text = "Địa chỉ IP", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = state.ipAddress.ifBlank { "—" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column {
                            Text(text = "DNS", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = state.dnsServers.ifBlank { "—" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp
                            )
                        }

                        Column {
                            Text(text = "Mật khẩu WiFi", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = wifiPassword?.takeIf { it.isNotBlank() } ?: "—",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Nút điều khiển nhanh bên dưới
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onManualScan,
                enabled = !isManualScanLoading && !isTogglingService,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (isManualScanLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Đang xử lý...")
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Quét lại")
                }
            }

            Button(
                onClick = onToggleService,
                enabled = !isManualScanLoading && !isTogglingService,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (isTogglingService) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Đang cập nhật...")
                } else {
                    Text(text = if (isServiceRunning) "Tắt giám sát" else "Bật giám sát")
                }
            }
        }
    }
}
