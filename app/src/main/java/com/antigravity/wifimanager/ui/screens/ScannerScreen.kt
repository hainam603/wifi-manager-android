package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.antigravity.wifimanager.data.WifiCredentialRefreshResult
import com.antigravity.wifimanager.ui.scanner.ScannerUiMapper
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.components.WifiBandBadge
import com.antigravity.wifimanager.ui.scanner.ScannerApRowModel
import com.antigravity.wifimanager.ui.scanner.ScannerDisplayState
import com.antigravity.wifimanager.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    networks: List<WifiApInfo>,
    connectionState: WifiConnectionState,
    displayState: ScannerDisplayState,
    networkCount: Int,
    scanStatusText: String,
    isScanning: Boolean = false,
    isRealtimeScanning: Boolean = false,
    connectingApKey: String? = null,
    connectFailedApKeys: Set<String> = emptySet(),
    rootConnectAvailable: Boolean = true,
    isPasswordBusy: Boolean = false,
    refreshingPasswordApKey: String? = null,
    connectedSignalPercent: Int = 0,
    scanCounter: Int = 0,
    onRefreshScan: () -> Unit,
    onConnectNetwork: (ssid: String, bssid: String, password: String?) -> Unit,
    onHasSystemCredential: (String) -> Boolean,
    onGetSavedPassword: (String, String?) -> String?,
    onRemovePassword: (String, String?) -> Unit,
    onResolvePassword: (String, String?) -> String?,
    onRefreshPasswordForBssid: suspend (String, String) -> WifiCredentialRefreshResult
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordDialogSsid by remember { mutableStateOf<String?>(null) }
    var passwordDialogBssid by remember { mutableStateOf<String?>(null) }
    var passwordDialogSimilarSsid by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }

    val quickFallback = remember(
        networks,
        connectionState.ssid,
        connectionState.bssid,
        connectionState.isConnected,
        connectionState.frequencyMhz,
        connectionState.signalPercent
    ) {
        if (networks.isEmpty()) {
            ScannerDisplayState.Empty
        } else {
            ScannerUiMapper.buildQuick(networks, connectionState)
        }
    }

    val effectiveDisplayState = when {
        displayState.hasRows() -> displayState
        quickFallback.hasRows() -> quickFallback
        else -> ScannerDisplayState.Empty
    }
    val hasDisplayRows = effectiveDisplayState.hasRows()
    val showScanningPlaceholder = isScanning && networkCount == 0
    val showEmptyPlaceholder = !isScanning && !hasDisplayRows
    val showPreparingList = isScanning && networkCount > 0 && !hasDisplayRows
    val showList = hasDisplayRows

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Nút Quét Lại phong cách Gradient Cyberpunk phát sáng mượt mà
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CyberPurple, CyberCyan)
                        )
                    )
                    .clickable {
                        if (isScanning || connectingApKey != null) return@clickable
                        onRefreshScan()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Quét lại",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Mạng khả dụng",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val connectableCount = (if (effectiveDisplayState.connectedRow != null) 1 else 0) +
                    effectiveDisplayState.savedRows.size
                Text(
                    text = "Có thể kết nối: $connectableCount/${effectiveDisplayState.totalRowCount()} mạng",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = when {
                        isScanning -> "Đang phân tích..."
                        isRealtimeScanning -> "Quét thời gian thực"
                        else -> "Đứng yên"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRealtimeScanning && !isScanning) CyberEmerald else TextSecondary,
                    modifier = Modifier
                        .background(
                            if (isRealtimeScanning && !isScanning) Color(0x1A10B981) else Color(0x0CFFFFFF),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Text(
                text = scanStatusText,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!rootConnectAvailable) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0x1AEF4444),
                    leftIndicatorColor = CyberRose
                ) {
                    Text(
                        text = "Chưa có quyền Root — không thể tự kết nối mạng im lặng. Hãy cấp Root tại Cấu hình hoặc dùng kết nối thủ công bằng cách sao chép mật khẩu (icon bút).",
                        modifier = Modifier.padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFFECACA),
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showScanningPlaceholder) {
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
                            strokeWidth = 3.dp,
                            color = CyberCyan
                        )
                        Text(
                            text = "Đang quét WiFi và tải dữ liệu cộng đồng...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (showEmptyPlaceholder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Không tìm thấy mạng WiFi phù hợp lân cận",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (showPreparingList) {
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
                            strokeWidth = 3.dp,
                            color = CyberPurple
                        )
                        Text(
                            text = "Đang xử lý $networkCount mạng...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (showList) {
                ScannerNetworkList(
                    modifier = Modifier.weight(1f),
                    displayState = effectiveDisplayState,
                    scanCounter = scanCounter,
                    connectedSignalPercent = connectedSignalPercent,
                    connectingApKey = connectingApKey,
                    connectFailedApKeys = connectFailedApKeys,
                    refreshingPasswordApKey = refreshingPasswordApKey,
                    rootConnectAvailable = rootConnectAvailable,
                    onConnectNetwork = onConnectNetwork,
                    onResolvePassword = onResolvePassword,
                    onRefreshPasswordForBssid = onRefreshPasswordForBssid,
                    coroutineScope = coroutineScope,
                    snackbarHostState = snackbarHostState,
                    onOpenPasswordDialog = { ssid, bssid, initial, similarSsid ->
                        passwordDialogSsid = ssid
                        passwordDialogBssid = bssid
                        passwordDialogSimilarSsid = similarSsid
                        passwordInput = initial
                    }
                )
            }
        }

        // Hộp thoại nhập mật khẩu phong cách Sci-Fi Glass Dialog cực kỳ cao cấp
        if (passwordDialogSsid != null) {
            val activeSsid = passwordDialogSsid!!
            val activeBssid = passwordDialogBssid
            val savedPassword = onGetSavedPassword(activeSsid, activeBssid)
            val isCurrentlyConnected = connectionState.isConnected && 
                connectionState.ssid.replace("\"", "").lowercase().trim() == activeSsid.replace("\"", "").lowercase().trim()
            val hasSystemCredential = onHasSystemCredential(activeSsid) || isCurrentlyConnected

            AlertDialog(
                onDismissRequest = { passwordDialogSsid = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = CosmicBgStart, // Đảm bảo nền tối độ tương phản cao dễ đọc
                modifier = Modifier.border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(24.dp)),
                title = {
                    Text(
                        text = if (!savedPassword.isNullOrEmpty()) "Mật khẩu mạng đã lưu" else "Cấu hình mật khẩu WiFi",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = activeSsid.replace("\"", ""),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = CyberCyan
                        )
                        if (!rootConnectAvailable) {
                            Text(
                                text = "Thiết bị chưa Root — mật khẩu sẽ chỉ lưu cục bộ trong ứng dụng này. Để kết nối thật, hãy sao chép rồi dán vào cài đặt WiFi hệ thống.",
                                fontSize = 11.sp,
                                color = CyberAmber,
                                lineHeight = 16.sp
                            )
                        }
                        
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mật khẩu kết nối") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                focusedLabelColor = CyberCyan,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        
                        val similarSsid = passwordDialogSimilarSsid
                        if (savedPassword.isNullOrEmpty() && !similarSsid.isNullOrBlank()) {
                            Text(
                                text = "✨ Gợi ý mật khẩu từ SSID tương tự: '$similarSsid'",
                                fontSize = 12.sp,
                                color = CyberPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = connectingApKey == null && !isPasswordBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        onClick = {
                            if (!rootConnectAvailable) {
                                // Nếu không có Root, ta cho phép lưu mật khẩu vào DB cục bộ
                                onRemovePassword(activeSsid, activeBssid) // Reset
                                onConnectNetwork(activeSsid, activeBssid ?: "02:00:00:00:00:00", passwordInput) // Lưu
                                passwordDialogSsid = null
                                passwordDialogBssid = null
                                return@Button
                            }
                            onConnectNetwork(
                                activeSsid,
                                passwordDialogBssid ?: "02:00:00:00:00:00",
                                passwordInput
                            )
                            passwordDialogSsid = null
                            passwordDialogBssid = null
                        }
                    ) {
                        Text(
                            text = when {
                                !rootConnectAvailable -> "Lưu mật khẩu"
                                savedPassword.isNullOrEmpty() -> "Kết nối"
                                else -> "Cập nhật & kết nối"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!savedPassword.isNullOrEmpty() || hasSystemCredential) {
                            TextButton(
                                onClick = {
                                    onRemovePassword(activeSsid, activeBssid)
                                    passwordDialogSsid = null
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Đã xóa và quên mạng '${activeSsid}'")
                                    }
                                }
                            ) {
                                Text("Quên mạng", color = CyberRose, fontWeight = FontWeight.Bold)
                            }
                        }

                        TextButton(onClick = { passwordDialogSsid = null }) {
                            Text("Đóng", color = TextSecondary)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ScannerNetworkList(
    modifier: Modifier = Modifier,
    displayState: ScannerDisplayState,
    scanCounter: Int = 0,
    connectedSignalPercent: Int,
    connectingApKey: String?,
    connectFailedApKeys: Set<String>,
    refreshingPasswordApKey: String?,
    rootConnectAvailable: Boolean,
    onConnectNetwork: (String, String, String?) -> Unit,
    onResolvePassword: (String, String?) -> String?,
    onRefreshPasswordForBssid: suspend (String, String) -> WifiCredentialRefreshResult,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onOpenPasswordDialog: (ssid: String, bssid: String, initial: String, similarSsid: String?) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scanCounter) {
        if (scanCounter > 0) listState.animateScrollToItem(0)
    }
    val connectedRow = displayState.connectedRow
    val savedRows = displayState.savedRows
    val nearbyRows = displayState.nearbyRows

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (connectedRow != null) {
            item(key = "header_connected", contentType = "header") {
                Text(
                    text = "MẠNG ĐANG KẾT NỐI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
                )
            }
            item(key = "connected_${connectedRow.stableKey}", contentType = "connected") {
                ScannerApListItem(
                    row = connectedRow,
                    signalPercentOverride = connectedSignalPercent,
                    isConnectedState = true,
                    connectingApKey = connectingApKey,
                    connectFailedApKeys = connectFailedApKeys,
                    refreshingPasswordApKey = refreshingPasswordApKey,
                    rootConnectAvailable = rootConnectAvailable,
                    onConnectNetwork = onConnectNetwork,
                    onResolvePassword = onResolvePassword,
                    onRefreshPasswordForBssid = onRefreshPasswordForBssid,
                    coroutineScope = coroutineScope,
                    snackbarHostState = snackbarHostState,
                    onOpenPasswordDialog = onOpenPasswordDialog
                )
            }
        }

        if (savedRows.isNotEmpty()) {
            item(key = "header_saved", contentType = "header") {
                Text(
                    text = "MẠNG SẴN SÀNG KẾT NỐI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp, top = 12.dp)
                )
            }
            items(
                items = savedRows,
                key = { it.stableKey },
                contentType = { "saved" }
            ) { row ->
                ScannerApListItem(
                    row = row,
                    isConnectedState = false,
                    connectingApKey = connectingApKey,
                    connectFailedApKeys = connectFailedApKeys,
                    refreshingPasswordApKey = refreshingPasswordApKey,
                    rootConnectAvailable = rootConnectAvailable,
                    onConnectNetwork = onConnectNetwork,
                    onResolvePassword = onResolvePassword,
                    onRefreshPasswordForBssid = onRefreshPasswordForBssid,
                    coroutineScope = coroutineScope,
                    snackbarHostState = snackbarHostState,
                    onOpenPasswordDialog = onOpenPasswordDialog
                )
            }
        }

        if (nearbyRows.isNotEmpty()) {
            item(key = "header_nearby", contentType = "header") {
                Text(
                    text = "MẠNG CHƯA CÓ MẬT KHẨU",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp, top = 12.dp)
                )
            }
            items(
                items = nearbyRows,
                key = { it.stableKey },
                contentType = { "nearby" }
            ) { row ->
                ScannerApListItem(
                    row = row,
                    isConnectedState = false,
                    connectingApKey = connectingApKey,
                    connectFailedApKeys = connectFailedApKeys,
                    refreshingPasswordApKey = refreshingPasswordApKey,
                    rootConnectAvailable = rootConnectAvailable,
                    onConnectNetwork = onConnectNetwork,
                    onResolvePassword = onResolvePassword,
                    onRefreshPasswordForBssid = onRefreshPasswordForBssid,
                    coroutineScope = coroutineScope,
                    snackbarHostState = snackbarHostState,
                    onOpenPasswordDialog = onOpenPasswordDialog
                )
            }
        }
    }
}

@Composable
private fun ScannerApListItem(
    row: ScannerApRowModel,
    signalPercentOverride: Int? = null,
    isConnectedState: Boolean,
    connectingApKey: String?,
    connectFailedApKeys: Set<String>,
    refreshingPasswordApKey: String?,
    rootConnectAvailable: Boolean,
    onConnectNetwork: (String, String, String?) -> Unit,
    onResolvePassword: (String, String?) -> String?,
    onRefreshPasswordForBssid: suspend (String, String) -> WifiCredentialRefreshResult,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onOpenPasswordDialog: (ssid: String, bssid: String, initial: String, similarSsid: String?) -> Unit
) {
    val ap = if (signalPercentOverride != null) {
        row.ap.copy(signalPercent = signalPercentOverride)
    } else {
        row.ap
    }
    val apKey = row.stableKey
    val canRefreshByBssid = WifiCredentialKeys.isValidBssid(ap.bssid) &&
        (
            ap.isSharedPasswordRejected ||
                !ap.sharedPasswordFromApi.isNullOrBlank() ||
                !ap.sharedProviderName.isNullOrBlank()
            )

    val isConnectFailed = connectFailedApKeys.contains(apKey)

    WifiApRow(
        ap = ap,
        isConnectedState = isConnectedState,
        isConnecting = connectingApKey == apKey,
        connectFailed = isConnectFailed,
        connectEnabled = true, // Cho phép bấm để nhập/sao chép kể cả khi chưa có Root
        connectBlocked = connectingApKey != null || refreshingPasswordApKey != null,
        passwordDisplay = row.passwordDisplay ?: row.savedPassword,
        showRefreshPasswordButton = canRefreshByBssid && !isConnectedState,
        isRefreshingPassword = refreshingPasswordApKey == apKey,
        onRefreshPasswordClick = {
            coroutineScope.launch {
                val result = onRefreshPasswordForBssid(ap.ssid, ap.bssid)
                snackbarHostState.showSnackbar(result.message)
            }
        },
        onConnectClick = {
            if (connectingApKey != null) return@WifiApRow
            
            // Trường hợp chưa Root, bấm vào card sẽ mở hộp thoại để người dùng xem/sửa/copy mật khẩu
            if (!rootConnectAvailable) {
                onOpenPasswordDialog(
                    ap.ssid,
                    ap.bssid,
                    onResolvePassword(ap.ssid, ap.bssid)
                        ?: ap.sharedPasswordFromApi
                        ?: row.similarPassword.orEmpty(),
                    row.similarSsid
                )
                return@WifiApRow
            }

            if (ap.isSharedPasswordRejected) {
                onOpenPasswordDialog(
                    ap.ssid,
                    ap.bssid,
                    onResolvePassword(ap.ssid, ap.bssid)
                        ?: ap.sharedPasswordFromApi
                        ?: row.similarPassword.orEmpty(),
                    row.similarSsid
                )
                return@WifiApRow
            }
            val hasAnySaved = ap.isReadyToConnect ||
                !row.savedPassword.isNullOrEmpty() ||
                row.hasSystemCredential ||
                ap.hasStoredPassword
            if ((row.needsPassword && !hasAnySaved) || isConnectFailed) {
                onOpenPasswordDialog(
                    ap.ssid,
                    ap.bssid,
                    onResolvePassword(ap.ssid, ap.bssid)
                        ?: row.savedPassword
                        ?: row.similarPassword.orEmpty(),
                    row.similarSsid
                )
            } else {
                onConnectNetwork(ap.ssid, ap.bssid, null)
            }
        },
        onEditPasswordClick = {
            onOpenPasswordDialog(
                ap.ssid,
                ap.bssid,
                onResolvePassword(ap.ssid, ap.bssid)
                    ?: row.savedPassword
                    ?: row.similarPassword.orEmpty(),
                row.similarSsid
            )
        }
    )
}

@Composable
private fun WifiApRow(
    ap: WifiApInfo,
    isConnectedState: Boolean,
    isConnecting: Boolean = false,
    connectFailed: Boolean = false,
    connectEnabled: Boolean = true,
    connectBlocked: Boolean = false,
    passwordDisplay: String? = null,
    showRefreshPasswordButton: Boolean = false,
    isRefreshingPassword: Boolean = false,
    onRefreshPasswordClick: () -> Unit = {},
    onConnectClick: () -> Unit,
    onEditPasswordClick: () -> Unit
) {
    // Vạch màu đứng bên mép trái chỉ thị cường độ sóng hoặc kết nối tích cực
    val leftIndicatorColor = when {
        isConnectedState -> CyberCyan
        ap.signalPercent >= 70 -> CyberEmerald
        ap.signalPercent >= 45 -> CyberAmber
        else -> CyberRose
    }

    val cardBg = if (isConnectedState) Color(0x1B06B6D4) else Color(0x0CFFFFFF)
    val cardBorderBrush = remember(isConnectedState) {
        if (isConnectedState) {
            Brush.verticalGradient(
                colors = listOf(Color(0x6606B6D4), Color(0x1106B6D4))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0x1FFFFFFF), Color(0x02FFFFFF))
            )
        }
    }

    val titleColor = if (isConnectedState) CyberCyan else TextPrimary
    val subtitleColor = TextSecondary

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = connectEnabled && !connectBlocked && !isConnecting) { onConnectClick() },
        containerColor = cardBg,
        borderBrush = cardBorderBrush,
        leftIndicatorColor = leftIndicatorColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), // Chừa 22dp start để không chạm left indicator
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Hàng 1: SSID + Băng tần + Badge loại mạng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ap.ssid,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    WifiBandBadge(
                        is5GHz = ap.is5GHz,
                        highlighted = isConnectedState
                    )

                    // Hiển thị badge "Đã lưu" hoặc "Cộng đồng"
                    if (ap.isReadyToConnect || ap.isSaved) {
                        Text(
                            text = "Đã lưu",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            modifier = Modifier
                                .background(Color(0x1A10B981), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    } else if (!ap.sharedProviderName.isNullOrBlank()) {
                        Text(
                            text = "Cộng đồng",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            modifier = Modifier
                                .background(Color(0x1A06B6D4), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Hàng 2: BSSID & Bảo mật
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BSSID: ${ap.bssid}",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                    Text(
                        text = if (ap.securityType.contains("WPA", ignoreCase = true)) "WPA2/WPA3" else "Mở",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }

                // Trạng thái kết nối / lỗi
                if (isConnecting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = CyberCyan,
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            text = "Đang liên kết mạng...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                } else if (isConnectedState) {
                    Text(
                        text = "Đang kết nối chủ động",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberEmerald,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    if (ap.isSharedPasswordRejected) {
                        Text(
                            text = "Mật khẩu cộng đồng sai — bấm để sửa",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberRose,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (showRefreshPasswordButton) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = onRefreshPasswordClick,
                                enabled = !isRefreshingPassword && !connectBlocked,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isRefreshingPassword) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = CyberCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isRefreshingPassword) "Đang cập nhật..." else "Cập nhật mật khẩu (BSSID)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (connectFailed) {
                        Text(
                            text = "Kết nối thất bại — chạm để sửa mật khẩu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberRose,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else if (ap.isReadyToConnect) {
                        Text(
                            text = "Sẵn sàng kết nối tự động",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Hiển thị mật khẩu đã lưu/cộng đồng nếu có
                if (!passwordDisplay.isNullOrBlank()) {
                    Text(
                        text = "Mật khẩu: $passwordDisplay",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnectedState) CyberEmerald else CyberCyan,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (showRefreshPasswordButton && !ap.isSharedPasswordRejected) {
                    TextButton(
                        onClick = onRefreshPasswordClick,
                        enabled = !isRefreshingPassword && !connectBlocked,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isRefreshingPassword) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đang cập nhật...", fontSize = 11.sp)
                        } else {
                            Text("Cập nhật mật khẩu (BSSID)", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Cột bên phải: % Sóng và Nút sửa mật khẩu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${ap.signalPercent}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isConnectedState) {
                        Color.White
                    } else {
                        when {
                            ap.signalPercent >= 70 -> CyberEmerald
                            ap.signalPercent >= 45 -> CyberAmber
                            else -> CyberRose
                        }
                    }
                )

                IconButton(
                    onClick = onEditPasswordClick,
                    enabled = !isConnecting,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x0AFFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (passwordDisplay.isNullOrBlank()) Icons.Default.VisibilityOff else Icons.Default.Edit,
                        contentDescription = "Sửa mật khẩu",
                        tint = if (isConnectedState) {
                            Color.White
                        } else if (!passwordDisplay.isNullOrBlank()) {
                            CyberCyan
                        } else {
                            Color(0x33FFFFFF)
                        },
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
