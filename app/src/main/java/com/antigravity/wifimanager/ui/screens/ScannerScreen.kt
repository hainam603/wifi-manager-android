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
    onSavePassword: (String, String, String?) -> Unit,
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
            Text(
                text = "Mạng khả dụng",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tìm thấy ${effectiveDisplayState.totalRowCount()} mạng WiFi",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = if (isScanning) "Đang quét WiFi và tải dữ liệu cộng đồng..." else scanStatusText,
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!rootConnectAvailable) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0x33F59E0B)
                ) {
                    Text(
                        text = "Chưa có quyền Root — không thể kết nối im lặng. Vào Cấu hình → Yêu cầu Root. Vẫn có thể xem/sửa mật khẩu (icon bút).",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFFDE68A),
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
                            strokeWidth = 3.dp
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Không tìm thấy mạng WiFi nào có mật khẩu",
                            color = TextSecondary,
                            fontSize = 14.sp
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
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Đang chuẩn bị danh sách $networkCount mạng...",
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Đang tải giao diện...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

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
                            else -> "Lưu mật khẩu WiFi"
                        }
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = activeSsid)
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mật khẩu") },
                            singleLine = true
                        )
                        val similarSsid = passwordDialogSimilarSsid
                        if (savedPassword.isNullOrEmpty() && !similarSsid.isNullOrBlank()) {
                            Text(
                                text = "✨ Phát hiện mật khẩu từ mạng tương tự: '$similarSsid'",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = connectingApKey == null && !isPasswordBusy,
                        onClick = {
                            onSavePassword(activeSsid, passwordInput, activeBssid)
                            if (rootConnectAvailable) {
                                onConnectNetwork(
                                    activeSsid,
                                    passwordDialogBssid ?: "02:00:00:00:00:00",
                                    passwordInput
                                )
                            }
                            passwordDialogSsid = null
                            passwordDialogBssid = null
                        }
                    ) {
                        if (isPasswordBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Đang lưu...")
                        } else {
                            Text(
                                when {
                                    !rootConnectAvailable -> "Lưu mật khẩu"
                                    savedPassword.isNullOrEmpty() -> "Kết nối"
                                    else -> "Cập nhật"
                                }
                            )
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
                                        snackbarHostState.showSnackbar("Đã xóa và quên mạng '${activeSsid}'")
                                    }
                                }
                            ) {
                                Text("Xóa mạng")
                            }
                        }

                        TextButton(onClick = { passwordDialogSsid = null }) {
                            Text("Đóng")
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
                    text = "Đang kết nối",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
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
                    text = "Mạng Wi-Fi",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, top = 12.dp)
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
                    text = "Chọn mạng Wi-Fi lân cận",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, top = 12.dp)
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
        connectEnabled = rootConnectAvailable,
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
            if (connectingApKey != null || !rootConnectAvailable) return@WifiApRow
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
    val cardBg = if (isConnectedState) Color(0xFF3B82F6) else CardBackground
    val cardBorderBrush = remember(isConnectedState) {
        if (isConnectedState) {
            Brush.verticalGradient(
                colors = listOf(Color(0x8860A5FA), Color(0x223B82F6))
            )
        } else {
            null
        }
    }

    val titleColor = if (isConnectedState) Color.White else TextPrimary
    val subtitleColor = if (isConnectedState) Color(0xFFDBEAFE) else TextSecondary
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = connectEnabled && !connectBlocked && !isConnecting) { onConnectClick() },
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
                Text(
                    text = "Bảo mật: ${if (ap.securityType.contains("WPA", ignoreCase = true)) "WPA2/WPA3" else "Open"}",
                    fontSize = 11.sp,
                    color = subtitleColor
                )

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
                    if (ap.isSharedPasswordRejected) {
                        Text(
                            text = "Mật khẩu API không hợp lệ — chờ cập nhật",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WifiWeak
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
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
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
                            text = "Kết nối thất bại — thử lại hoặc sửa mật khẩu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WifiWeak
                        )
                    } else if (ap.isReadyToConnect) {
                        Text(
                            text = "Sẵn sàng kết nối",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WifiGood
                        )
                    }
                }

                if (!passwordDisplay.isNullOrBlank()) {
                    Text(
                        text = "Mật khẩu: $passwordDisplay",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnectedState) Color(0xFF86EFAC) else Accent
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
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang cập nhật...", fontSize = 11.sp)
                        } else {
                            Text("Cập nhật mật khẩu (BSSID)", fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mức sóng %
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
                        imageVector = if (passwordDisplay.isNullOrBlank()) Icons.Default.VisibilityOff else Icons.Default.Edit,
                        contentDescription = if (passwordDisplay.isNullOrBlank()) "Lưu mật khẩu" else "Sửa mật khẩu",
                        tint = if (isConnectedState) {
                            Color.White
                        } else if (!passwordDisplay.isNullOrBlank()) {
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
