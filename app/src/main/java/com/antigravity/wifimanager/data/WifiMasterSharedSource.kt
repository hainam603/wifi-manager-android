package com.antigravity.wifimanager.data

import com.blackhat.wifipasswords.WifiMaster
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Nguồn WiFi cộng đồng tích hợp sẵn (thư viện WifiMaster / WiFi Key database).
 * Không cần URL hay API key.
 */
class WifiMasterSharedSource {

    private val wifiMaster = WifiMaster()
    private val TIMEOUT_MS = 15_000L

    suspend fun fetchNearby(latitude: Double, longitude: Double): List<SharedWifiCredential> {
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                wifiMaster.listWifiListByLatLong(
                    latitude.toString(),
                    longitude.toString(),
                    onCompleted = { json ->
                        if (!resumed.compareAndSet(false, true)) return@listWifiListByLatLong
                        val parsed = SharedWifiJsonParser.parse(
                            json,
                            defaultProvider = "WifiMaster",
                            userLat = latitude,
                            userLng = longitude
                        )
                        continuation.resume(parsed)
                    },
                    onError = {
                        if (!resumed.compareAndSet(false, true)) return@listWifiListByLatLong
                        continuation.resume(emptyList())
                    }
                )
            }
        } ?: emptyList()
    }

    /** Tra một hotspot theo ID bản ghi WifiMaster (nhanh, không cần quét GPS). */
    suspend fun fetchById(wifiMasterId: Long): SharedWifiCredential? {
        if (wifiMasterId <= 0L || wifiMasterId > Int.MAX_VALUE) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                wifiMaster.getWifiById(
                    wifiMasterId.toInt(),
                    onCompleted = { json ->
                        if (!resumed.compareAndSet(false, true)) return@getWifiById
                        if (json.contains("No Hotspot", ignoreCase = true) ||
                            json.contains("not found", ignoreCase = true)
                        ) {
                            continuation.resume(null)
                            return@getWifiById
                        }
                        val parsed = SharedWifiJsonParser.parse(
                            json,
                            defaultProvider = "WifiMaster",
                            userLat = null,
                            userLng = null
                        )
                        continuation.resume(
                            parsed.firstOrNull()?.copy(wifiMasterId = wifiMasterId)
                        )
                    },
                    onError = {
                        if (!resumed.compareAndSet(false, true)) return@getWifiById
                        continuation.resume(null)
                    }
                )
            }
        }
    }
}
