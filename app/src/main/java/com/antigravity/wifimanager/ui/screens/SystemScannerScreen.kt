package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.data.WifiCredentialKeys
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.components.WifiBandBadge
import com.antigravity.wifimanager.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

data class SystemScannerRowModel(
    val ap: WifiApInfo,
    val savedPassword: String?,
    val hasSystemCredential: Boolean,
    val isConnected: Boolean
) {
    val stableKey: String
        get() = "${ap.ssid.lowercase(Locale.getDefault())}|${ap.bssid.lowercase(Locale.getDefault())}"
}

@Composable
fun SystemScannerScreen(
    networks: List<SystemScannerRowModel>,
    connectionState: WifiConnectionState,
    scanStatusText: String,
    isScanning: Boolean = false,
    connectingApKey: String? = null,
    connectFailedApKeys: Set<String> = emptySet(),
    rootConnectAvailable: Boolean = true,
    isPasswordBusy: Boolean = false,
    onRefreshScan: () -> Unit,
    onConnectNetwork: (ssid: String, bssid: String, password: String?) -> Unit,
    onHasSystemCredential: (String) -> Boolean,
    onGetSavedPassword: (String, String?) -> String?,
    onSavePassword: (String, String, String?) -> Unit,
    onRemovePassword: (String, String?) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenWifiPanel: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordDialogSsid by remember { mutableStateOf<String?>(null) }
    var passwordDialogBssid by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNetworks = remember(networks, searchQuery) {
        if (searchQuery.isBlank()) {
            networks
        } else {
            networks.filter { it.ap.ssid.contains(searchQuery, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isScanning || connectingApKey != null) return@FloatingActionButton
                    onRefreshScan()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Quét lại")
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WiFi Hệ thống",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quét trực tiếp từ phần cứng Android",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            Text(
                text = if (isScanning) "Đang tìm kiếm mạng..." else scanStatusText,
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenWifiSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F6366F1)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cài đặt WiFi", fontSize = 12.sp, color = TextPrimary)
                }

                Button(
                    onClick = onOpenWifiPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F06B6D4)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Accent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bảng WiFi nhanh", fontSize = 12.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm tên WiFi (SSID)...", color = TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0x22FFFFFF),
                    focusedContainerColor = Color(0x0AFFFFFF),
                    unfocusedContainerColor = Color(0x05FFFFFF)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!rootConnectAvailable) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0x2206B6D4)
                ) {
                    Text(
                        text = "ℹ️ Thiết bị chưa Root: Sẽ kết nối bằng yêu cầu liên kết của Android (có hộp thoại xác nhận). Cần bật Vị trí.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFE0F2FE),
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isScanning && filteredNetworks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Đang quét mạng WiFi xung quanh...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (filteredNetworks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Không tìm thấy mạng phù hợp" else "Không tìm thấy mạng WiFi nào",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredNetworks,
                        key = { it.stableKey }
                    ) { row ->
                        val isConnecting = connectingApKey == row.stableKey
                        val isConnectFailed = connectFailedApKeys.contains(row.stableKey)

                        SystemWifiApRow(
                            ap = row.ap,
                            isConnectedState = row.isConnected,
                            isConnecting = isConnecting,
                            connectFailed = isConnectFailed,
                            connectBlocked = connectingApKey != null,
                            savedPassword = row.savedPassword,
                            onConnectClick = {
                                val isSecured = row.ap.securityType.contains("WPA", ignoreCase = true) ||
                                        row.ap.securityType.contains("SAE", ignoreCase = true) ||
                                        row.ap.securityType.contains("PSK", ignoreCase = true)
                                val hasCredential = row.ap.hasStoredPassword || !row.savedPassword.isNullOrBlank() || row.hasSystemCredential

                                if ((isSecured && !hasCredential) || isConnectFailed) {
                                    passwordDialogSsid = row.ap.ssid
                                    passwordDialogBssid = row.ap.bssid
                                    passwordInput = row.savedPassword.orEmpty()
                                } else {
                                    onConnectNetwork(row.ap.ssid, row.ap.bssid, null)
                                }
                            },
                            onEditPasswordClick = {
                                passwordDialogSsid = row.ap.ssid
                                passwordDialogBssid = row.ap.bssid
                                passwordInput = row.savedPassword.orEmpty()
                            }
                        )
                    }
                }
            }
        }

        // Dialog nhập mật khẩu
        if (passwordDialogSsid != null) {
            val activeSsid = passwordDialogSsid!!
            val activeBssid = passwordDialogBssid
            val savedPassword = onGetSavedPassword(activeSsid, activeBssid)
            val isCurrentlyConnected = connectionState.isConnected && 
                    connectionState.ssid.equals(activeSsid, ignoreCase = true)
            val hasSystemCredential = onHasSystemCredential(activeSsid) || isCurrentlyConnected

            AlertDialog(
                onDismissRequest = { passwordDialogSsid = null },
                title = {
                    Text(
                        text = when {
                            !savedPassword.isNullOrEmpty() -> "Mật khẩu WiFi"
                            hasSystemCredential -> "Đã lưu trong hệ thống"
                            else -> "Kết nối vào mạng"
                        }
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = activeSsid, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mật khẩu") },
                            singleLine = true
                        )
                        if (savedPassword.isNullOrEmpty() && hasSystemCredential) {
                            Text(
                                text = "Mạng này đã được lưu trong hệ thống Android. Nhấn kết nối hoặc nhập mật khẩu để đồng bộ trực tiếp với app.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = connectingApKey == null && !isPasswordBusy,
                        onClick = {
                            onSavePassword(activeSsid, passwordInput, activeBssid)
                            onConnectNetwork(
                                activeSsid,
                                activeBssid ?: "02:00:00:00:00:00",
                                passwordInput.takeIf { it.isNotEmpty() }
                            )
                            passwordDialogSsid = null
                            passwordDialogBssid = null
                        }
                    ) {
                        if (isPasswordBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (savedPassword.isNullOrEmpty()) "Kết nối" else "Cập nhật")
                        }
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!savedPassword.isNullOrEmpty() || hasSystemCredential) {
                            TextButton(
                                onClick = {
                                    onRemovePassword(activeSsid, activeBssid)
                                    passwordDialogSsid = null
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Đã xóa thông tin mạng '${activeSsid}'")
                                    }
                                }
                            ) {
                                Text("Xóa mạng", color = WifiWeak)
                            }
                        }

                        TextButton(onClick = { passwordDialogSsid = null }) {
                            Text("Hủy")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SystemWifiApRow(
    ap: WifiApInfo,
    isConnectedState: Boolean,
    isConnecting: Boolean = false,
    connectFailed: Boolean = false,
    connectBlocked: Boolean = false,
    savedPassword: String? = null,
    onConnectClick: () -> Unit,
    onEditPasswordClick: () -> Unit
) {
    val cardBg = if (isConnectedState) Color(0xFF2563EB) else CardBackground
    val cardBorderBrush = remember(isConnectedState) {
        if (isConnectedState) {
            Brush.verticalGradient(
                colors = listOf(Color(0x8860A5FA), Color(0x222563EB))
            )
        } else {
            null
        }
    }

    val titleColor = if (isConnectedState) Color.White else TextPrimary
    val subtitleColor = if (isConnectedState) Color(0xFFDBEAFE) else TextSecondary

    val isSecured = ap.securityType.contains("WPA", ignoreCase = true) ||
            ap.securityType.contains("SAE", ignoreCase = true) ||
            ap.securityType.contains("PSK", ignoreCase = true)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connectBlocked && !isConnecting) { onConnectClick() },
        containerColor = cardBg,
        borderBrush = cardBorderBrush
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ap.ssid,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )
                    WifiBandBadge(
                        is5GHz = ap.is5GHz,
                        highlighted = isConnectedState
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "BSSID: ${ap.bssid}",
                    fontSize = 11.sp,
                    color = subtitleColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSecured) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bảo mật",
                            modifier = Modifier.size(10.dp),
                            tint = subtitleColor
                        )
                    }
                    Text(
                        text = "Bảo mật: ${ap.securityType.take(25)}",
                        fontSize = 11.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isConnecting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = if (isConnectedState) Color.White else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Đang kết nối...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnectedState) Color(0xFFDBEAFE) else MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isConnectedState) {
                    Text(
                        text = "Đang kết nối",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF86EFAC)
                    )
                } else {
                    if (connectFailed) {
                        Text(
                            text = "Kết nối thất bại — chạm để nhập lại mật khẩu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WifiWeak
                        )
                    } else if (ap.isSaved) {
                        Text(
                            text = "Đã lưu trong máy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WifiGood
                        )
                    }
                }

                if (!savedPassword.isNullOrBlank()) {
                    Text(
                        text = "Mật khẩu: $savedPassword",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnectedState) Color(0xFF86EFAC) else Accent
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Signal %
                Text(
                    text = "${ap.signalPercent}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnectedState) {
                        Color.White
                    } else {
                        when {
                            ap.signalPercent >= 70 -> WifiGood
                            ap.signalPercent >= 45 -> WifiMedium
                            else -> WifiWeak
                        }
                    }
                )

                IconButton(
                    onClick = onEditPasswordClick,
                    enabled = !isConnecting
                ) {
                    Icon(
                        imageVector = if (savedPassword.isNullOrBlank()) Icons.Default.VisibilityOff else Icons.Default.Edit,
                        contentDescription = if (savedPassword.isNullOrBlank()) "Lưu mật khẩu" else "Sửa mật khẩu",
                        tint = if (isConnectedState) {
                            Color.White
                        } else if (!savedPassword.isNullOrBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0x33FFFFFF)
                        }
                    )
                }
            }
        }
    }
}
