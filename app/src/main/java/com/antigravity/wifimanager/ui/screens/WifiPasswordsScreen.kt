package com.antigravity.wifimanager.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.SharedWifiCredential
import com.antigravity.wifimanager.data.WifiCredentialKeys
import com.antigravity.wifimanager.ui.components.AddressText
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.theme.*
import com.antigravity.wifimanager.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPasswordsScreen(
    savedPasswords: Map<String, String>,
    offlinePasswords: List<SharedWifiCredential>,
    onDeletePassword: (String, String?) -> Unit,
    onShowToast: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Cục bộ, 1 = Offline Cộng đồng

    // Bộ lọc Dropdown theo Cột
    var selectedFilterColumn by remember { mutableStateOf("Tất cả") }
    var filterColumnDropdownExpanded by remember { mutableStateOf(false) }

    // Reset bộ lọc khi chuyển tab chính
    LaunchedEffect(selectedTab) {
        selectedFilterColumn = "Tất cả"
    }

    // Lấy vị trí GPS hiện tại của thiết bị để tính khoảng cách bất đồng bộ
    val userLocation = remember { LocationHelper.getLastKnownLocation(context) }
    val userLat = userLocation?.latitude
    val userLng = userLocation?.longitude

    // 1. Xử lý & lọc danh sách WiFi lưu cục bộ
    val localItems = remember(savedPasswords, searchQuery, selectedFilterColumn) {
        savedPasswords.map { (key, password) ->
            val parsed = WifiCredentialKeys.parseStorageKey(key)
            val ssid = parsed.first
            val bssid = parsed.second
            Triple(ssid, bssid, password)
        }.filter { (ssid, bssid, password) ->
            val q = searchQuery.trim()
            
            // Lọc nâng cao theo Cột được chọn từ Dropdown
            if (q.isEmpty()) {
                true
            } else {
                when (selectedFilterColumn) {
                    "Tên WiFi" -> ssid.contains(q, ignoreCase = true)
                    "Mật khẩu" -> password.contains(q, ignoreCase = true)
                    "Địa chỉ MAC" -> bssid?.contains(q, ignoreCase = true) == true
                    else -> ssid.contains(q, ignoreCase = true) || (bssid != null && bssid.contains(q, ignoreCase = true))
                }
            }
        }.sortedBy { it.first.lowercase() }
    }

    // 2. Xử lý & lọc danh sách WiFi offline cộng đồng
    val offlineItems = remember(offlinePasswords, searchQuery, selectedFilterColumn, userLat, userLng) {
        offlinePasswords.filter { cred ->
            val q = searchQuery.trim()
            
            // Lọc nâng cao theo Cột được chọn từ Dropdown
            if (q.isEmpty()) {
                true
            } else {
                when (selectedFilterColumn) {
                    "Tên WiFi" -> cred.ssid.contains(q, ignoreCase = true)
                    "Mật khẩu" -> cred.password.contains(q, ignoreCase = true)
                    "Địa chỉ MAC" -> cred.bssid?.contains(q, ignoreCase = true) == true
                    "Khoảng cách (km)" -> {
                        val maxKm = q.toDoubleOrNull()
                        if (maxKm != null && userLat != null && userLng != null && cred.latitude != null && cred.longitude != null) {
                            val dist = LocationHelper.calculateDistanceKm(userLat, userLng, cred.latitude, cred.longitude)
                            dist != null && dist <= maxKm
                        } else {
                            // Nếu chưa có vị trí GPS hoặc parse lỗi, mặc định hiển thị
                            true
                        }
                    }
                    else -> cred.ssid.contains(q, ignoreCase = true) || (cred.bssid != null && cred.bssid.contains(q, ignoreCase = true))
                }
            }
        }.sortedBy { cred ->
            // Sắp xếp ưu tiên khoảng cách gần người dùng nhất
            if (userLat != null && userLng != null && cred.latitude != null && cred.longitude != null) {
                LocationHelper.calculateDistanceKm(userLat, userLng, cred.latitude, cred.longitude) ?: Double.MAX_VALUE
            } else {
                Double.MAX_VALUE
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Tiêu đề trang
        Column {
            Text(
                text = "Mật khẩu đã lưu",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Bảng dữ liệu mật khẩu và định vị WiFi của bạn",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selector chuyển Tab chính (Cục bộ ↔ Cộng đồng)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x05FFFFFF), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("Đã lưu cục bộ", "WiFi cộng đồng offline")
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                val textSelectedColor = if (index == 0) CyberPurple else CyberCyan
                val bgSelectedColor = if (index == 0) Color(0x108B5CF6) else Color(0x1006B6D4)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) bgSelectedColor else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) textSelectedColor else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bộ đôi điều khiển: 1 Dropdown chọn Cột + 1 TextBox nhập giá trị tìm kiếm
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Dropdown chọn Cột
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { filterColumnDropdownExpanded = true }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val iconTint = if (selectedTab == 0) CyberPurple else CyberCyan
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = selectedFilterColumn,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "▼",
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                }

                DropdownMenu(
                    expanded = filterColumnDropdownExpanded,
                    onDismissRequest = { filterColumnDropdownExpanded = false },
                    modifier = Modifier
                        .background(Color(0xE60B0D17)) // Nền kính mờ tối
                        .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(8.dp))
                ) {
                    val columns = if (selectedTab == 0) {
                        listOf("Tất cả", "Tên WiFi", "Mật khẩu", "Địa chỉ MAC")
                    } else {
                        listOf("Tất cả", "Tên WiFi", "Mật khẩu", "Địa chỉ MAC", "Khoảng cách (km)")
                    }
                    columns.forEach { col ->
                        DropdownMenuItem(
                            text = { Text(col, color = TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                selectedFilterColumn = col
                                filterColumnDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 2. TextBox nhập giá trị tìm kiếm tương ứng (BasicTextField để 100% control vertical alignment tránh lỗi mất chữ)
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(if (selectedTab == 0) CyberPurple else CyberCyan),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp)),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                val placeText = when (selectedFilterColumn) {
                                    "Tên WiFi" -> "Nhập tên WiFi để lọc..."
                                    "Mật khẩu" -> "Nhập mật khẩu cần tìm..."
                                    "Địa chỉ MAC" -> "Nhập địa chỉ MAC/BSSID..."
                                    "Khoảng cách (km)" -> "Nhập bán kính km tối đa (vd: 2 hoặc 5.5)..."
                                    else -> "Nhập từ khóa tìm kiếm..."
                                }
                                Text(placeText, color = TextSecondary, fontSize = 13.sp)
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Xóa",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val currentItemsCount = if (selectedTab == 0) localItems.size else offlineItems.size

        if (currentItemsCount == 0) {
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
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0x08FFFFFF), CircleShape)
                            .border(1.dp, Color(0x12FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Không tìm thấy dữ liệu WiFi phù hợp",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // HIỂN THỊ DẠNG BẢNG (TABLE) TRÊN GLASS CARD PHẲNG TỐI ƯU KHÔNG GIAN
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                containerColor = Color(0x06FFFFFF)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header của Bảng (Chuyển thành 3 cột tối ưu hiển thị)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0FFFFFFF))
                            .border(width = (0.5).dp, color = Color(0x15FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WIFI / MẬT KHẨU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) CyberPurple else CyberCyan,
                            modifier = Modifier.weight(0.45f)
                        )
                        Text(
                            text = if (selectedTab == 0) "MAC/BSSID" else "K.CÁCH / ĐỊA CHỈ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.weight(0.42f)
                        )
                        Text(
                            text = "CHỨC NĂNG",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.13f)
                        )
                    }

                    // Danh sách các hàng dữ liệu trong bảng
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        if (selectedTab == 0) {
                            // CỘT TAB CỤC BỘ DẠNG BẢNG 3 CỘT
                            items(localItems, key = { "${it.first}|${it.second.orEmpty()}" }) { (ssid, bssid, password) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(width = (0.3).dp, color = Color(0x08FFFFFF))
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Cột 1: SSID / Mật khẩu (Dòng trên wifi, dòng dưới mật khẩu)
                                    Column(
                                        modifier = Modifier.weight(0.45f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = ssid,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = password,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyberCyan,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Cột 2: BSSID
                                    Text(
                                        text = bssid?.uppercase() ?: "SSID Trần",
                                        fontSize = 10.sp,
                                        color = if (bssid != null) TextSecondary else CyberPurple.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.42f)
                                    )

                                    // Cột 3: Nút hành động gọn gàng
                                    Row(
                                        modifier = Modifier.weight(0.13f),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = CyberCyan,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(password))
                                                    onShowToast("Đã copy mật khẩu của '$ssid'")
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa",
                                            tint = CyberRose,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { onDeletePassword(ssid, bssid) }
                                        )
                                    }
                                }
                            }
                        } else {
                            // CỘT TAB CỘNG ĐỒNG OFFLINE DẠNG BẢNG 3 CỘT
                            items(offlineItems, key = { "offline_${it.ssid}|${it.bssid.orEmpty()}" }) { cred ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(width = (0.3).dp, color = Color(0x08FFFFFF))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Cột 1: SSID / Mật khẩu (Dòng trên wifi, dòng dưới mật khẩu)
                                    Column(
                                        modifier = Modifier.weight(0.45f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = cred.ssid,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = cred.password,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyberCyan,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Cột 2: Vị trí & Khoảng cách GPS
                                    Box(modifier = Modifier.weight(0.42f)) {
                                        if (cred.latitude != null && cred.longitude != null) {
                                            val dist = if (userLat != null && userLng != null) {
                                                LocationHelper.calculateDistanceKm(userLat, userLng, cred.latitude, cred.longitude)
                                            } else null

                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (dist != null) {
                                                    Text(
                                                        text = "Cách bạn ${"%.2f".format(dist)} km",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = CyberCyan,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = CyberCyan,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    AddressText(
                                                        latitude = cred.latitude,
                                                        longitude = cred.longitude
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = cred.bssid?.uppercase() ?: "Không có tọa độ",
                                                fontSize = 9.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Cột 3: Nút hành động gọn gàng
                                    Row(
                                        modifier = Modifier.weight(0.13f),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = CyberCyan,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(cred.password))
                                                    onShowToast("Đã copy mật khẩu của '${cred.ssid}'")
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
