package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
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
import com.antigravity.wifimanager.ui.theme.WifiGood
import com.antigravity.wifimanager.ui.theme.WifiWeak

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
    onRequestBatteryExemption: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Cài đặt cấu hình",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tùy chỉnh ngưỡng tín hiệu và cơ chế chuyển đổi",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        // Root
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Quyền Root",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val (statusText, statusColor) = when (rootStatus) {
                    RootStatus.GRANTED -> "Đã cấp — chuyển WiFi im lặng" to WifiGood
                    RootStatus.DENIED -> "Chưa cấp — nhấn nút bên dưới" to WifiWeak
                    RootStatus.UNAVAILABLE -> "Thiết bị không có Root" to TextSecondary
                }

                Text(
                    text = "Trạng thái: $statusText",
                    fontSize = 13.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = when (rootStatus) {
                        RootStatus.GRANTED ->
                            "App dùng lệnh hệ thống để chuyển mạng tự động, không cần hộp thoại."
                        RootStatus.DENIED ->
                            "Magisk/SuperSU sẽ hỏi quyền khi bạn nhấn \"Yêu cầu quyền Root\"."
                        RootStatus.UNAVAILABLE ->
                            "Vẫn dùng được qua gợi ý WiFi của Android (có thể cần xác nhận thủ công)."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                if (rootStatus != RootStatus.UNAVAILABLE) {
                    Button(
                        onClick = onRequestRoot,
                        enabled = rootStatus != RootStatus.GRANTED && !isRequestingRoot,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRequestingRoot) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Đang kiểm tra Root...")
                        } else {
                            Text(
                                text = if (rootStatus == RootStatus.GRANTED) {
                                    "Root đã được cấp"
                                } else {
                                    "Yêu cầu quyền Root"
                                }
                            )
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tự động chuyển mạng",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tự động tìm kiếm và gợi ý kết nối WiFi mạnh hơn khi mạng hiện tại yếu",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = autoSwitchEnabled,
                    onCheckedChange = onAutoSwitchToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ưu tiên WiFi 5 GHz",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Khi đang dùng 2.4 GHz, tự chuyển sang mạng 5 GHz tin cậy có sóng tương đương (vd. cùng 100%)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = prefer5GhzEnabled,
                    onCheckedChange = onPrefer5GhzToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "API WiFi chia sẻ khu vực",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tự lấy mật khẩu WiFi cộng đồng quanh GPS (WifiMaster + API tuỳ chọn)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = sharedWifiEnabled,
                        onCheckedChange = onSharedWifiToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WifiMaster (tích hợp sẵn)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cơ sở dữ liệu WiFi cộng đồng toàn cầu — không cần URL",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = wifiMasterEnabled,
                        onCheckedChange = onWifiMasterToggle,
                        enabled = sharedWifiEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lưu offline (tự tích lũy)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lưu WiFi cộng đồng trên máy — tra cứu khi mất mạng (có thể dùng tới vài GB)",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                        Text(
                            text = "Đã lưu: $sharedWifiOfflineCount mạng · ${"%.2f".format(sharedWifiOfflineStorageUsedMb)} MB / $sharedWifiOfflineStorageMb MB",
                            fontSize = 12.sp,
                            color = WifiGood,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = sharedWifiOfflineEnabled,
                        onCheckedChange = onSharedWifiOfflineToggle,
                        enabled = sharedWifiEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Text(
                    text = "Bán kính tra cứu & tải trước: $sharedWifiOfflineRadiusKm km",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = sharedWifiOfflineRadiusKm.toFloat(),
                    onValueChange = { onOfflineRadiusKmChange(it.toInt()) },
                    enabled = sharedWifiEnabled && sharedWifiOfflineEnabled && !sharedWifiPrefetching,
                    valueRange = SharedWifiRepository.MIN_OFFLINE_RADIUS_KM.toFloat()..
                        SharedWifiRepository.MAX_OFFLINE_RADIUS_KM.toFloat(),
                    steps = SharedWifiRepository.MAX_OFFLINE_RADIUS_KM - SharedWifiRepository.MIN_OFFLINE_RADIUS_KM - 1
                )
                Text(
                    text = "1 km (quanh nhà) → 50 km (vùng rộng). Quét online & tải trước dùng cùng bán kính này.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                val storageGb = sharedWifiOfflineStorageMb / 1024f
                val maxNetLabel = when {
                    sharedWifiOfflineMaxNetworks >= 1_000_000 ->
                        "${sharedWifiOfflineMaxNetworks / 1_000_000} triệu"
                    else -> "${sharedWifiOfflineMaxNetworks / 1000}k"
                }
                Text(
                    text = if (storageGb >= 1f) {
                        "Giới hạn lưu trữ: ${"%.1f".format(storageGb)} GB (~$maxNetLabel mạng)"
                    } else {
                        "Giới hạn lưu trữ: $sharedWifiOfflineStorageMb MB (~$maxNetLabel mạng)"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = sharedWifiOfflineStorageMb.toFloat(),
                    onValueChange = { onOfflineStorageMbChange(it.toInt()) },
                    enabled = sharedWifiEnabled && sharedWifiOfflineEnabled && !sharedWifiPrefetching,
                    valueRange = SharedWifiRepository.MIN_OFFLINE_STORAGE_MB.toFloat()..
                        SharedWifiRepository.MAX_OFFLINE_STORAGE_MB.toFloat(),
                    steps = 39
                )
                Text(
                    text = "50 MB → 4 GB. Tăng dung lượng để tích lũy nhiều WiFi hơn qua nhiều lần tải trước.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                val canPrefetch = sharedWifiEnabled &&
                    sharedWifiOfflineEnabled &&
                    (wifiMasterEnabled || sharedWifiApiUrl.isNotBlank())

                Button(
                    onClick = onPrefetchSharedWifiArea,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canPrefetch && !sharedWifiPrefetching
                ) {
                    if (sharedWifiPrefetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val progress = sharedWifiPrefetchProgress
                        Text(
                            text = if (progress != null) {
                                "Đang tải ${progress.first}/${progress.second} điểm…"
                            } else {
                                "Đang tải…"
                            },
                            fontSize = 14.sp
                        )
                    } else {
                        Text("Tải trước vùng ${sharedWifiOfflineRadiusKm} km")
                    }
                }

                Text(
                    text = "Quét lưới GPS trong bán kính ${sharedWifiOfflineRadiusKm} km (có thể vài phút nếu vùng lớn). Cần Internet.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                if (sharedWifiOfflineCount > 0) {
                    TextButton(
                        onClick = onClearSharedWifiOffline,
                        enabled = sharedWifiEnabled && !sharedWifiPrefetching && !isClearingOffline
                    ) {
                        if (isClearingOffline) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang xóa...")
                        } else {
                            Text("Xóa bộ nhớ offline")
                        }
                    }
                }

                OutlinedTextField(
                    value = sharedWifiApiUrl,
                    onValueChange = onSharedWifiUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sharedWifiEnabled,
                    label = { Text("URL API bổ sung (tuỳ chọn)") },
                    placeholder = { Text("https://api.example.com/wifi/nearby") },
                    singleLine = false,
                    maxLines = 2
                )

                OutlinedTextField(
                    value = sharedWifiApiKey,
                    onValueChange = onSharedWifiApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sharedWifiEnabled,
                    label = { Text("API Key (tuỳ chọn)") },
                    singleLine = true
                )

                Text(
                    text = "API bổ sung: ?lat=&lng=&radius= hoặc {lat} {lng} {radius}. JSON: ssid, password (hoặc security.password).",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ngưỡng kích hoạt sóng yếu",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ứng dụng sẽ kích hoạt quét và đề xuất mạng mới khi cường độ sóng giảm dưới mức này.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Ngưỡng hiện tại:", fontSize = 14.sp, color = TextSecondary)
                    Text(
                        text = "${threshold}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { onThresholdChange(it.toInt()) },
                    valueRange = 10f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0x22FFFFFF)
                    )
                )
            }
        }

        // Pin & nền
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "Pin & chạy nền", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = if (isBatteryOptimized) {
                        "Tối ưu pin hệ thống: Đang bật — có thể dừng giám sát nền"
                    } else {
                        "Tối ưu pin hệ thống: Đã tắt cho app — giám sát ổn định hơn"
                    },
                    fontSize = 12.sp,
                    color = if (isBatteryOptimized) WifiWeak else WifiGood
                )

                Text(
                    text = if (monitoringEnabled) {
                        "Tự khởi động sau khi bật máy: Bật (khi giám sát đang bật)"
                    } else {
                        "Tự khởi động sau khi bật máy: Tắt (bật lại giám sát trên Trang chủ)"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Text(
                    text = if (monitoringEnabled) {
                        "Khi giám sát bật, icon \"WiFi Auto-Switcher\" hiện trên thanh trạng thái " +
                            "(thông báo dịch vụ nền — không phải chạy ẩn hoàn toàn)."
                    } else {
                        "App không chạy giám sát nền khi bạn tắt trên Trang chủ hoặc bấm \"Dừng giám sát\" trong thông báo."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                if (isBatteryOptimized) {
                    OutlinedButton(
                        onClick = onRequestBatteryExemption,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cho phép chạy nền (tắt tối ưu pin)")
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = "Lưu ý (Android 15)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Cần quyền Vị trí và WiFi lân cận để quét mạng.\n" +
                            "• Giám sát nền kiểm tra sóng ~45 giây/lần để tiết kiệm pin.\n" +
                            "• Lưu mật khẩu mạng dự phòng trong tab Quét WiFi.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
