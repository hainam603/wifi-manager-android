package com.antigravity.wifimanager.data

import com.blackhat.wifipasswords.WifiMaster
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Nguồn WiFi cộng đồng tích hợp sẵn (thư viện WifiMaster / WiFi Key database).
 * Không cần URL hay API key.
 */
class WifiMasterSharedSource {

    private val wifiMaster = WifiMaster()

    suspend fun fetchNearby(latitude: Double, longitude: Double): List<SharedWifiCredential> {
        return suspendCoroutine { continuation ->
            wifiMaster.listWifiListByLatLong(
                latitude.toString(),
                longitude.toString(),
                onCompleted = { json ->
                    val parsed = SharedWifiJsonParser.parse(
                        json,
                        defaultProvider = "WifiMaster",
                        userLat = latitude,
                        userLng = longitude
                    )
                    continuation.resume(parsed)
                },
                onError = {
                    continuation.resume(emptyList())
                }
            )
        }
    }
}
