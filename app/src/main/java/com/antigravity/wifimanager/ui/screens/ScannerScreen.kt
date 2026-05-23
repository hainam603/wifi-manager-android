package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.antigravity.wifimanager.data.WifiApInfo
import com.antigravity.wifimanager.data.WifiConnectionState
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.components.WifiBandBadge
import com.antigravity.wifimanager.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

private fun wifiApKey(ssid: String, bssid: String): String =
    "${ssid.lowercase(Locale.getDefault())}|${bssid.lowercase(Locale.getDefault())}"

@Composable
fun ScannerScreen(
    scannedNetworks: List<WifiApInfo>,
    scanStatusText: String,
    isScanning: Boolean = false,
    connectingApKey: String? = null,
    connectFailedApKeys: Set<String> = emptySet(),
    rootConnectAvailable: Boolean = true,
    isPasswordBusy: Boolean = false,
    actionsBlocked: Boolean = false,
    currentConnectionState: WifiConnectionState,
    onRefreshScan: () -> Unit,
    onConnectNetwork: (ssid: String, bssid: String) -> Unit,
    onHasSystemCredential: (String) -> Boolean,
    onGetSavedPassword: (String) -> String?,
    onSavePassword: (String, String) -> Unit,
    onRemovePassword: (String) -> Unit,
    onGetSimilarSsidPassword: (String) -> Pair<String, String>?,
    onResolvePassword: (String, String?) -> String?
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordDialogSsid by remember { mutableStateOf<String?>(null) }
    var passwordDialogBssid by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 1. Phân chia các mạng thành 3 nhóm: Đang kết nối, Đã lưu, Lân cận
    val connectedSsid = if (currentConnectionState.isConnected && currentConnectionState.ssid.isNotEmpty() &&
        currentConnectionState.ssid != "Mạng WiFi" && currentConnectionState.ssid != "Đang kết nối WiFi" &&
        currentConnectionState.ssid != "<unknown ssid>") {
        currentConnectionState.ssid
    } else {
        null
    }

    val connectedAp = if (connectedSsid != null) {
        val sameSsidAps = scannedNetworks.filter { it.ssid == connectedSsid }
        when {
            sameSsidAps.isNotEmpty() -> {
                when {
                    currentConnectionState.frequencyMhz >= 4900 ->
                        sameSsidAps.filter { it.is5GHz }.maxByOrNull { it.signalPercent }
                            ?: sameSsidAps.maxByOrNull { it.signalPercent }
                    currentConnectionState.frequencyMhz in 2400..2500 ->
                        sameSsidAps.filter { !it.is5GHz }.maxByOrNull { it.signalPercent }
                            ?: sameSsidAps.maxByOrNull { it.signalPercent }
                    else -> sameSsidAps.maxByOrNull { it.signalPercent }
                }
            }
            else -> WifiApInfo(
                ssid = connectedSsid,
                bssid = currentConnectionState.bssid.ifEmpty { "00:00:00:00:00:00" },
                signalPercent = currentConnectionState.signalPercent,
                frequencyMhz = currentConnectionState.frequencyMhz.coerceAtLeast(2412),
                isSaved = true,
                hasStoredPassword = true,
                securityType = currentConnectionState.authType
            )
        }
    } else {
        null
    }

    val wifiApComparator = compareByDescending<WifiApInfo> { it.signalPercent }
        .thenByDescending { it.is5GHz }
        .thenByDescending { it.frequencyMhz }
        .thenBy { it.ssid.lowercase(Locale.getDefault()) }

    // Mạng đã lưu
    val sortedSaved = scannedNetworks.filter {
        it.ssid != connectedSsid && it.hasStoredPassword
    }.sortedWith(wifiApComparator)

    // Mạng lân cận
    val sortedNearby = scannedNetworks.filter {
        it.ssid != connectedSsid && !it.hasStoredPassword
    }.sortedWith(wifiApComparator)


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isScanning || connectingApKey != null || actionsBlocked) return@FloatingActionButton
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
                text = "Tìm thấy ${scannedNetworks.size} mạng WiFi xung quanh bạn",
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

            if (scannedNetworks.isEmpty()) {
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
                        if (isScanning) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Đang tìm kiếm tín hiệu WiFi...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "Chưa có mạng — bấm nút quét để tải danh sách",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Nhóm Đang kết nối
                    if (connectedAp != null) {
                        item {
                            Text(
                                text = "Đang kết nối",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
                            )


                            val connectedPassword = onResolvePassword(connectedAp.ssid, connectedAp.bssid)

                            WifiApRow(
                                ap = connectedAp,
                                isConnectedState = true,
                                isConnecting = connectingApKey == wifiApKey(connectedAp.ssid, connectedAp.bssid),
                                connectFailed = connectFailedApKeys.contains(
                                    wifiApKey(connectedAp.ssid, connectedAp.bssid)
                                ),
                                connectEnabled = rootConnectAvailable,
                                connectBlocked = connectingApKey != null,
                                passwordDisplay = connectedPassword,
                                onConnectClick = {
                                    onConnectNetwork(connectedAp.ssid, connectedAp.bssid)
                                },
                                onEditPasswordClick = {
                                    passwordDialogSsid = connectedAp.ssid
                                    passwordDialogBssid = connectedAp.bssid
                                    passwordInput = onGetSavedPassword(connectedAp.ssid)
                                        ?: onGetSimilarSsidPassword(connectedAp.ssid)?.second.orEmpty()
                                    passwordVisible = true
                                }
                            )
                        }
                    }

                    // 2. Nhóm Mạng Wi-Fi đã lưu
                    if (sortedSaved.isNotEmpty()) {
                        item {
                            Text(
                                text = "Mạng Wi-Fi đã lưu",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp, top = 12.dp)
                            )
                        }
                        
                        items(sortedSaved) { ap ->
                            val savedPassword = onGetSavedPassword(ap.ssid)
                            val similarPassword = onGetSimilarSsidPassword(ap.ssid)
                            val hasSystemCredential = onHasSystemCredential(ap.ssid)
                            val needsPassword = ap.securityType.contains("WPA", ignoreCase = true) ||
                                                ap.securityType.contains("SAE", ignoreCase = true) ||
                                                ap.securityType.contains("PSK", ignoreCase = true)
                            val resolvedPassword = onResolvePassword(ap.ssid, ap.bssid)
                                ?: ap.sharedPasswordFromApi?.takeIf { ap.isSharedPasswordRejected }
                            val passwordDisplay = when {
                                ap.isSharedPasswordRejected -> {
                                    val provider = ap.sharedProviderName
                                    val apiPass = ap.sharedPasswordFromApi
                                    when {
                                        !apiPass.isNullOrBlank() && !provider.isNullOrBlank() ->
                                            "$apiPass (API: $provider)"
                                        !apiPass.isNullOrBlank() -> apiPass
                                        else -> null
                                    }
                                }
                                !resolvedPassword.isNullOrEmpty() -> {
                                    val provider = ap.sharedProviderName
                                    if (!provider.isNullOrBlank() && !savedPassword.isNullOrEmpty()) {
                                        "$resolvedPassword (API: $provider)"
                                    } else if (!provider.isNullOrBlank()) {
                                        "$resolvedPassword (chia sẻ: $provider)"
                                    } else if (!similarPassword?.second.isNullOrEmpty()) {
                                        "$resolvedPassword (từ ${similarPassword!!.first})"
                                    } else {
                                        resolvedPassword
                                    }
                                }
                                hasSystemCredential -> "(đã lưu trên máy — mật khẩu ẩn)"
                                else -> null
                            }

                            WifiApRow(
                                ap = ap,
                                isConnectedState = false,
                                isConnecting = connectingApKey == wifiApKey(ap.ssid, ap.bssid),
                                connectFailed = connectFailedApKeys.contains(wifiApKey(ap.ssid, ap.bssid)),
                                connectEnabled = rootConnectAvailable,
                                connectBlocked = connectingApKey != null,
                                passwordDisplay = passwordDisplay,
                                onConnectClick = {
                                    if (connectingApKey != null) return@WifiApRow
                                    if (!rootConnectAvailable) return@WifiApRow
                                    if (ap.isSharedPasswordRejected) {
                                        passwordDialogSsid = ap.ssid
                                        passwordDialogBssid = ap.bssid
                                        passwordInput = onResolvePassword(ap.ssid, ap.bssid)
                                            ?: ap.sharedPasswordFromApi
                                            ?: similarPassword?.second
                                            ?: ""
                                        passwordVisible = passwordInput.isNotEmpty()
                                        return@WifiApRow
                                    }
                                    val hasAnySaved = ap.isReadyToConnect || !savedPassword.isNullOrEmpty() || hasSystemCredential || ap.hasStoredPassword
                                    if (needsPassword && !hasAnySaved) {
                                        passwordDialogSsid = ap.ssid
                                        passwordDialogBssid = ap.bssid
                                        val similar = onGetSimilarSsidPassword(ap.ssid)
                                        passwordInput = onResolvePassword(ap.ssid, ap.bssid) ?: similar?.second ?: ""
                                        passwordVisible = passwordInput.isEmpty()
                                    } else {
                                        onConnectNetwork(ap.ssid, ap.bssid)
                                    }
                                },
                                onEditPasswordClick = {
                                    passwordDialogSsid = ap.ssid
                                    passwordDialogBssid = ap.bssid
                                    passwordInput = onResolvePassword(ap.ssid, ap.bssid) ?: savedPassword ?: similarPassword?.second ?: ""
                                    passwordVisible = true
                                }
                            )
                        }
                    }

                    // 3. Nhóm Mạng Wi-Fi lân cận
                    if (sortedNearby.isNotEmpty()) {
                        item {
                            Text(
                                text = "Chọn mạng Wi-Fi lân cận",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp, top = 12.dp)
                            )
                        }

                        items(sortedNearby) { ap ->
                            val savedPassword = onGetSavedPassword(ap.ssid)
                            val similarPassword = onGetSimilarSsidPassword(ap.ssid)
                            val hasSystemCredential = onHasSystemCredential(ap.ssid)
                            val needsPassword = ap.securityType.contains("WPA", ignoreCase = true) ||
                                                ap.securityType.contains("SAE", ignoreCase = true) ||
                                                ap.securityType.contains("PSK", ignoreCase = true)
                            val resolvedPassword = onResolvePassword(ap.ssid, ap.bssid)
                                ?: ap.sharedPasswordFromApi?.takeIf { ap.isSharedPasswordRejected }
                            val passwordDisplay = when {
                                ap.isSharedPasswordRejected -> {
                                    val provider = ap.sharedProviderName
                                    val apiPass = ap.sharedPasswordFromApi
                                    when {
                                        !apiPass.isNullOrBlank() && !provider.isNullOrBlank() ->
                                            "$apiPass (API: $provider)"
                                        !apiPass.isNullOrBlank() -> apiPass
                                        else -> null
                                    }
                                }
                                !resolvedPassword.isNullOrEmpty() -> {
                                    val provider = ap.sharedProviderName
                                    when {
                                        !provider.isNullOrBlank() -> "$resolvedPassword (chia sẻ: $provider)"
                                        !similarPassword?.second.isNullOrEmpty() ->
                                            "$resolvedPassword (từ ${similarPassword!!.first})"
                                        else -> resolvedPassword
                                    }
                                }
                                ap.isReadyToConnect && hasSystemCredential ->
                                    "(đã lưu trên máy — mật khẩu ẩn)"
                                else -> null
                            }

                            WifiApRow(
                                ap = ap,
                                isConnectedState = false,
                                isConnecting = connectingApKey == wifiApKey(ap.ssid, ap.bssid),
                                connectFailed = connectFailedApKeys.contains(wifiApKey(ap.ssid, ap.bssid)),
                                connectEnabled = rootConnectAvailable,
                                connectBlocked = connectingApKey != null,
                                passwordDisplay = passwordDisplay,
                                onConnectClick = {
                                    if (connectingApKey != null) return@WifiApRow
                                    if (!rootConnectAvailable) return@WifiApRow
                                    if (ap.isSharedPasswordRejected) {
                                        passwordDialogSsid = ap.ssid
                                        passwordDialogBssid = ap.bssid
                                        passwordInput = onResolvePassword(ap.ssid, ap.bssid)
                                            ?: ap.sharedPasswordFromApi
                                            ?: similarPassword?.second
                                            ?: ""
                                        passwordVisible = passwordInput.isNotEmpty()
                                        return@WifiApRow
                                    }
                                    val hasAnySaved = ap.isReadyToConnect || !savedPassword.isNullOrEmpty() || hasSystemCredential
                                    if (needsPassword && !hasAnySaved) {
                                        passwordDialogSsid = ap.ssid
                                        passwordDialogBssid = ap.bssid
                                        val similar = onGetSimilarSsidPassword(ap.ssid)
                                        passwordInput = onResolvePassword(ap.ssid, ap.bssid) ?: similar?.second ?: ""
                                        passwordVisible = passwordInput.isEmpty()
                                    } else {
                                        onConnectNetwork(ap.ssid, ap.bssid)
                                    }
                                },
                                onEditPasswordClick = {
                                    passwordDialogSsid = ap.ssid
                                    passwordDialogBssid = ap.bssid
                                    passwordInput = onResolvePassword(ap.ssid, ap.bssid) ?: savedPassword ?: similarPassword?.second ?: ""
                                    passwordVisible = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (passwordDialogSsid != null) {
            val activeSsid = passwordDialogSsid!!
            val savedPassword = onGetSavedPassword(activeSsid)
            val hasSystemCredential = onHasSystemCredential(activeSsid)

            AlertDialog(
                onDismissRequest = { passwordDialogSsid = null },
                title = {
                    Text(
                        text = when {
                            !savedPassword.isNullOrEmpty() -> "Mật khẩu WiFi"
                            hasSystemCredential -> "Đã lưu trong hệ thống"
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
                            singleLine = true,
                            readOnly = savedPassword != null && !passwordVisible,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        val similar = onGetSimilarSsidPassword(activeSsid)
                        if (savedPassword.isNullOrEmpty() && similar != null) {
                            Text(
                                text = "✨ Phát hiện mật khẩu từ mạng tương tự: '${similar.first}'",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (savedPassword != null && !passwordVisible) {
                            Text(
                                text = "Mật khẩu đang được ẩn. Bấm biểu tượng mắt để xem hoặc sửa.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        } else if (savedPassword.isNullOrEmpty() && hasSystemCredential) {
                            Text(
                                text = "Mạng này đã có thông tin đăng nhập trong hệ thống. Bạn có thể lưu mật khẩu thủ công để app dùng trực tiếp.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = connectingApKey == null && !isPasswordBusy && !actionsBlocked,
                        onClick = {
                            onSavePassword(activeSsid, passwordInput)
                            if (rootConnectAvailable) {
                                onConnectNetwork(
                                    activeSsid,
                                    passwordDialogBssid ?: "02:00:00:00:00:00"
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
                        if (!savedPassword.isNullOrEmpty()) {
                            TextButton(
                                onClick = {
                                    onRemovePassword(activeSsid)
                                    passwordDialogSsid = null
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Đã xóa mật khẩu của '${activeSsid}'")
                                    }
                                }
                            ) {
                                Text("Xóa")
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
private fun WifiApRow(
    ap: WifiApInfo,
    isConnectedState: Boolean,
    isConnecting: Boolean = false,
    connectFailed: Boolean = false,
    connectEnabled: Boolean = true,
    connectBlocked: Boolean = false,
    passwordDisplay: String? = null,
    onConnectClick: () -> Unit,
    onEditPasswordClick: () -> Unit
) {
    val cardBg = if (isConnectedState) {
        Color(0xFF3B82F6) // A premium solid royal blue color to match the screenshot!
    } else {
        CardBackground
    }
    
    val cardBorderBrush = if (isConnectedState) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x8860A5FA),
                Color(0x223B82F6)
            )
        )
    } else {
        null
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
                
                if (isConnectedState) {
                    Text(
                        text = "Đang kết nối • Bảo mật: ${if (ap.securityType.contains("WPA", ignoreCase = true)) "WPA2/WPA3" else "Open"}",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                } else {
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
                } else if (ap.isSharedPasswordRejected) {
                    Text(
                        text = "Mật khẩu API không hợp lệ — chờ cập nhật",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnectedState) Color(0xFFFECACA) else WifiWeak
                    )
                } else if (connectFailed && !ap.isSharedPasswordRejected) {
                    Text(
                        text = "Kết nối thất bại — thử lại hoặc sửa mật khẩu",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnectedState) Color(0xFFFECACA) else WifiWeak
                    )
                } else if (ap.isReadyToConnect) {
                    Text(
                        text = "Sẵn sàng kết nối",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnectedState) Color(0xFFBBF7D0) else WifiGood
                    )
                }

                if (!passwordDisplay.isNullOrBlank()) {
                    Text(
                        text = "Mật khẩu: $passwordDisplay",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnectedState) Color(0xFFE0F2FE) else Accent,
                        maxLines = 3
                    )
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
