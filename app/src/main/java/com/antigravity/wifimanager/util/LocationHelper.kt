package com.antigravity.wifimanager.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

object LocationHelper {

    data class GeoPoint(val latitude: Double, val longitude: Double)

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): GeoPoint? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var best: Location? = null
        for (provider in providers) {
            if (!manager.isProviderEnabled(provider)) continue
            try {
                val loc = manager.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.time > best.time) {
                    best = loc
                }
            } catch (_: Exception) {
                // Bỏ qua provider lỗi
            }
        }

        return best?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
