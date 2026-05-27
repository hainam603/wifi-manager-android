package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.RootStatus
import com.antigravity.wifimanager.data.SharedWifiRepository
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.theme.TextSecondary
import com.antigravity.wifimanager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    threshold: Int,
    autoSwitchEnabled: Boolean,
    prefer5GhzEnabled: Boolean,
    sharedWifiEnabled: Boolean,
    wifiMasterEnabled: Boolean,
    sharedWifiOfflineEnabled: Boolean,
    sharedWifiOfflineCount: Int,
    sharedWifiOfflineRadiusKm: Int,
    sharedWifiOfflineStorageMb: Int,
    sharedWifiOfflineStorageUsedMb: Double,
    sharedWifiOfflineMaxNetworks: Int,
    sharedWifiPrefetching: Boolean,
    isClearingOffline: Boolean = false,
    isRequestingRoot: Boolean = false,
    sharedWifiPrefetchProgress: Pair<Int, Int>?,
    sharedWifiApiUrl: String,
    sharedWifiApiKey: String,
    rootStatus: RootStatus,
    isBatteryOptimized: Boolean,
    monitoringEnabled: Boolean,
    autoUpdateIntervalDays: Int,
    lastAutoUpdateMs: Long,
    isAutoUpdating: Boolean,
    geminiApiKey: String,
    geminiAiEnabled: Boolean,
    geminiAutoPilotEnabled: Boolean,
    isTestingGemini: Boolean,
    onThresholdChange: (Int) -> Unit,
    onAutoSwitchToggle: (Boolean) -> Unit,
    onPrefer5GhzToggle: (Boolean) -> Unit,
    onSharedWifiToggle: (Boolean) -> Unit,
    onWifiMasterToggle: (Boolean) -> Unit,
    onSharedWifiOfflineToggle: (Boolean) -> Unit,
    onOfflineRadiusKmChange: (Int) -> Unit,
    onOfflineStorageMbChange: (Int) -> Unit,
    onClearSharedWifiOffline: () -> Unit,
    onPrefetchSharedWifiArea: () -> Unit,
    onSharedWifiUrlChange: (String) -> Unit,
    onSharedWifiApiKeyChange: (String) -> Unit,
    onRequestRoot: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onAutoUpdateIntervalChange: (Int) -> Unit,
    onTriggerManualUpdate: () -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiAiToggle: (Boolean) -> Unit,
    onGeminiAutoPilotToggle: (Boolean) -> Unit,
    onTestGeminiConnection: () -> Unit,
    splitDnsEnabled: Boolean,
    onSplitDnsToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Cài đặt cấu hình",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Cá nhân hóa thuật toán chuyển đổi và dữ liệu offline",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberCyan.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "ĐỘNG CƠ TỰ ĐỘNG HÓA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tự động tối ưu sóng",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tìm và gợi ý kết nối WiFi mạnh hơn khi mạng hiện tại yếu dưới ngưỡng.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = autoSwitchEnabled,
                        onCheckedChange = onAutoSwitchToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ưu tiên WiFi băng tần 5 GHz",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Khi đang dùng 2.4 GHz, tự chuyển sang mạng 5 GHz tin cậy có sóng tương đương để đạt tốc độ tối đa.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = prefer5GhzEnabled,
                        onCheckedChange = onPrefer5GhzToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ngưỡng sóng kích hoạt tối ưu",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${threshold}%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberCyan
                        )
                    }
                    Text(
                        text = "Thiết bị sẽ tự động quét và đề xuất mạng khác khi sóng giảm dưới mức này.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { onThresholdChange(it.toInt()) },
                        valueRange = 10f..95f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = Color(0x15FFFFFF)
                        )
                    )
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberAmber.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "KHO DỮ LIỆU CỘNG ĐỒNG",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberAmber,
                        letterSpacing = 1.sp
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "API WiFi chia sẻ khu vực",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tự động tra cứu mật khẩu WiFi quanh GPS hiện tại từ WifiMaster và API bên thứ ba.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = sharedWifiEnabled,
                        onCheckedChange = onSharedWifiToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberAmber
                        )
                    )
                }

                if (sharedWifiEnabled) {
                    Divider(color = Color(0x0CFFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tích hợp sẵn WifiMaster",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tra cứu trực tiếp cơ sở dữ liệu mật khẩu WiFi cộng đồng toàn cầu.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = wifiMasterEnabled,
                            onCheckedChange = onWifiMasterToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberAmber
                            )
                        )
                    }

                    Divider(color = Color(0x0CFFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lưu trữ offline trên máy",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Lưu trữ mật khẩu cộng đồng tải về máy để tự động kết nối khi bạn mất mạng hoàn toàn.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                            Text(
                                text = "Đã lưu trữ: $sharedWifiOfflineCount mạng • ${"%.2f".format(sharedWifiOfflineStorageUsedMb)} MB / $sharedWifiOfflineStorageMb MB",
                                fontSize = 12.sp,
                                color = CyberEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = sharedWifiOfflineEnabled,
                            onCheckedChange = onSharedWifiOfflineToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberAmber
                            )
                        )
                    }

                    if (sharedWifiOfflineEnabled) {
                        Divider(color = Color(0x0CFFFFFF))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bán kính tra cứu & tải trước",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$sharedWifiOfflineRadiusKm km",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberAmber
                                )
                            }
                            Text(
                                text = "Quét online và tải dữ liệu lưới GPS offline trong bán kính này quanh vị trí của bạn.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                            Slider(
                                value = sharedWifiOfflineRadiusKm.toFloat(),
                                onValueChange = { onOfflineRadiusKmChange(it.toInt()) },
                                enabled = !sharedWifiPrefetching,
                                valueRange = SharedWifiRepository.MIN_OFFLINE_RADIUS_KM.toFloat()..
                                    SharedWifiRepository.MAX_OFFLINE_RADIUS_KM.toFloat(),
                                steps = SharedWifiRepository.MAX_OFFLINE_RADIUS_KM - SharedWifiRepository.MIN_OFFLINE_RADIUS_KM - 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyberAmber,
                                    activeTrackColor = CyberAmber,
                                    inactiveTrackColor = Color(0x15FFFFFF)
                                )
                            )
                        }

                        Divider(color = Color(0x0CFFFFFF))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val storageGb = sharedWifiOfflineStorageMb / 1024f
                            val maxNetLabel = when {
                                sharedWifiOfflineMaxNetworks >= 1_000_000 ->
                                    "${sharedWifiOfflineMaxNetworks / 1_000_000} triệu"
                                else -> "${sharedWifiOfflineMaxNetworks / 1000}k"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Giới hạn bộ nhớ WiFi offline",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (storageGb >= 1f) "${"%.1f".format(storageGb)} GB" else "$sharedWifiOfflineStorageMb MB",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberAmber
                                )
                            }
                            Text(
                                text = "Dung lượng tối đa cho phép lưu trữ (~$maxNetLabel điểm kết nối WiFi cộng đồng).",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Slider(
                                value = sharedWifiOfflineStorageMb.toFloat(),
                                onValueChange = { onOfflineStorageMbChange(it.toInt()) },
                                enabled = !sharedWifiPrefetching,
                                valueRange = SharedWifiRepository.MIN_OFFLINE_STORAGE_MB.toFloat()..
                                    SharedWifiRepository.MAX_OFFLINE_STORAGE_MB.toFloat(),
                                steps = 39,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyberAmber,
                                    activeTrackColor = CyberAmber,
                                    inactiveTrackColor = Color(0x15FFFFFF)
                                )
                            )
                        }

                        Divider(color = Color(0x0CFFFFFF))

                        Button(
                            onClick = onPrefetchSharedWifiArea,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !sharedWifiPrefetching
                        ) {
                            if (sharedWifiPrefetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val progress = sharedWifiPrefetchProgress
                                Text(
                                    text = if (progress != null) "Đang tải ${progress.first}/${progress.second}..." else "Đang prefetch...",
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tải trước vùng lưới ${sharedWifiOfflineRadiusKm} km",
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (sharedWifiOfflineCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = onClearSharedWifiOffline,
                                enabled = !sharedWifiPrefetching && !isClearingOffline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x22EF4444), RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRose)
                            ) {
                                if (isClearingOffline) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CyberRose
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Đang xóa bộ nhớ...", fontSize = 14.sp)
                                } else {
                                    Text("Xóa sạch bộ nhớ WiFi offline", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Divider(color = Color(0x0CFFFFFF))

                OutlinedTextField(
                    value = sharedWifiApiUrl,
                    onValueChange = onSharedWifiUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sharedWifiEnabled,
                    label = { Text("API URL bổ sung (tùy chọn)") },
                    placeholder = { Text("https://api.example.com/wifi/nearby") },
                    singleLine = false,
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberAmber,
                        focusedLabelColor = CyberAmber,
                        unfocusedBorderColor = Color(0x1AFFFFFF),
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = sharedWifiApiKey,
                    onValueChange = onSharedWifiApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sharedWifiEnabled,
                    label = { Text("API Key khóa bảo mật (tùy chọn)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberAmber,
                        focusedLabelColor = CyberAmber,
                        unfocusedBorderColor = Color(0x1AFFFFFF),
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Divider(color = Color(0x0CFFFFFF))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tự động cập nhật định kỳ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Tự cập nhật mật khẩu WiFi offline quanh GPS chạy nền. Cần thiết lập vị trí & Internet.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    val lastUpdateText = if (lastAutoUpdateMs <= 0L) {
                        "Cập nhật gần nhất: Chưa từng"
                    } else {
                        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        "Cập nhật gần nhất: ${fmt.format(Date(lastAutoUpdateMs))}"
                    }
                    Text(
                        text = lastUpdateText,
                        fontSize = 12.sp,
                        color = if (lastAutoUpdateMs > 0L) CyberEmerald else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    val intervalLabels = listOf("Tắt cập nhật", "1 ngày", "2 ngày", "3 ngày", "7 ngày")
                    val intervalValues = listOf(0, 1, 2, 3, 7)
                    val currentIndex = intervalValues.indexOf(autoUpdateIntervalDays).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chu kỳ hoạt động:",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = intervalLabels.getOrElse(currentIndex) { "1 ngày" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberAmber
                        )
                    }

                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { idx ->
                            val days = intervalValues.getOrElse(idx.toInt()) { 1 }
                            onAutoUpdateIntervalChange(days)
                        },
                        valueRange = 0f..(intervalValues.size - 1).toFloat(),
                        steps = intervalValues.size - 2,
                        enabled = !isAutoUpdating,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberAmber,
                            activeTrackColor = CyberAmber,
                            inactiveTrackColor = Color(0x15FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onTriggerManualUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sharedWifiEnabled && sharedWifiOfflineEnabled && !isAutoUpdating && !sharedWifiPrefetching
                    ) {
                        if (isAutoUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang đồng bộ cơ sở dữ liệu...", color = Color.Black, fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đồng bộ ngay bây giờ", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberEmerald.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "VƯỢT CHẶN TRANG WEB (SPLIT DNS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberEmerald,
                        letterSpacing = 1.sp
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bypass chặn Facebook/Messenger",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tự động phân tách DNS (Split DNS): Định tuyến Facebook/Messenger qua DNS sạch bên ngoài, trong khi vẫn giữ DNS của công ty để bạn làm việc và truy cập mạng nội bộ bình thường.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = splitDnsEnabled,
                        onCheckedChange = onSplitDnsToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberEmerald
                        )
                    )
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberRose.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberRose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "TRỢ LÝ TRÍ TUỆ NHÂN TẠO (AI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberRose,
                        letterSpacing = 1.sp
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trợ lý Chẩn đoán Lỗi AI",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tự động phân tích và đưa ra giải pháp khắc phục bằng tiếng Việt khi chuyển đổi WiFi thất bại.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = geminiAiEnabled,
                        onCheckedChange = onGeminiAiToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberRose
                        )
                    )
                }

                if (geminiAiEnabled) {
                    Divider(color = Color(0x0CFFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Sửa Lỗi Tự Động (Auto-Pilot)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Cho phép AI tự chẩn đoán ngầm và kích hoạt sửa lỗi tức thì (vượt cổng chào, thử mật khẩu phổ biến hoặc chuyển mạng dự phòng) khi gặp sự cố, không cần bạn bấm tay.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = geminiAutoPilotEnabled,
                            onCheckedChange = onGeminiAutoPilotToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberRose
                            )
                        )
                    }

                    Divider(color = Color(0x0CFFFFFF))

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = onGeminiApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Nhập API Key từ Google AI Studio...") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberRose,
                            focusedLabelColor = CyberRose,
                            unfocusedBorderColor = Color(0x1AFFFFFF),
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Text(
                        text = "Lưu ý: API Key được lưu an toàn trên máy của bạn. Bạn có thể lấy Key miễn phí tại Google AI Studio.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onTestGeminiConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberRose),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = geminiApiKey.isNotBlank() && !isTestingGemini
                    ) {
                        if (isTestingGemini) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang kiểm tra kết nối...", color = Color.White)
                        } else {
                            Text("Kiểm tra kết nối API Key", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberPurple.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CyberPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "QUYỀN HẠN HỆ THỐNG",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPurple,
                        letterSpacing = 1.sp
                    )
                }

                Divider(color = Color(0x0CFFFFFF))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (statusText, statusColor) = when (rootStatus) {
                        RootStatus.GRANTED -> "Đã được cấp — Sẵn sàng tối ưu tự động im lặng" to CyberEmerald
                        RootStatus.DENIED -> "Chưa cấp quyền — Nhấn yêu cầu bên dưới" to CyberRose
                        RootStatus.UNAVAILABLE -> "Thiết bị không hỗ trợ quyền Root" to TextSecondary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Liên kết quyền hệ thống Root",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (rootStatus == RootStatus.GRANTED) "CÓ ROOT" else "KHIẾM KHUYẾT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Trạng thái: $statusText",
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Root cho phép ứng dụng quên/chuyển mạng tự động ngầm không cần hộp thoại xác nhận phiền hà từ Android.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    if (rootStatus != RootStatus.UNAVAILABLE && rootStatus != RootStatus.GRANTED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onRequestRoot,
                            enabled = !isRequestingRoot,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRequestingRoot) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang kiểm tra quyền Root...", color = Color.White)
                            } else {
                                Text("Cấp quyền Root cho ứng dụng", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(color = Color(0x0CFFFFFF))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quản lý pin nền",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isBatteryOptimized) {
                            "Đang bị giới hạn: Chế độ tối ưu pin hệ thống có thể giết giám sát nền."
                        } else {
                            "Hoạt động tốt: Đã loại trừ khỏi tối ưu pin hệ thống — chạy ngầm ổn định."
                        },
                        fontSize = 12.sp,
                        color = if (isBatteryOptimized) CyberRose else CyberEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Giám sát sóng nền chạy ngầm chu kỳ khoảng 45 giây một lần để tìm sóng WiFi tối ưu.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                    if (isBatteryOptimized) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onRequestBatteryExemption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x22F59E0B), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberAmber)
                        ) {
                            Text("Cho phép chạy nền không giới hạn", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Card Ghi chú lưu ý Android 15
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )

                Column {
                    Text(
                        text = "LƯU Ý QUAN TRỌNG (ANDROID 15)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Bắt buộc bật Vị trí (GPS) và cho phép quyền Thiết bị lân cận để quét WiFi.\n" +
                            "• Giám sát nền hoạt động qua biểu tượng trạng thái để tránh bị hệ điều hành xóa bộ nhớ.\n" +
                            "• Hãy dùng nút quên mạng lân cận lỗi trong tab Quét WiFi để giải phóng rác kết nối.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

