package com.antigravity.wifimanager.ui.components

import android.location.Geocoder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Composable tra cứu địa chỉ thực tế từ Geocoder bất đồng bộ và an toàn.
 */
@Composable
fun AddressText(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var addressText by remember(latitude, longitude) { mutableStateOf("Đang tìm vị trí...") }

    LaunchedEffect(latitude, longitude) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("vi", "VN"))
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                
                if (address != null) {
                    val fullAddress = address.getAddressLine(0)
                    addressText = fullAddress ?: "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                } else {
                    addressText = "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                }
            } catch (e: Exception) {
                addressText = "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
            }
        }
    }

    Text(
        text = addressText,
        fontSize = 11.sp, // Đổi từ 9.sp thành 11.sp cho dễ đọc hơn ở danh sách quét
        color = TextSecondary,
        modifier = modifier
    )
}
