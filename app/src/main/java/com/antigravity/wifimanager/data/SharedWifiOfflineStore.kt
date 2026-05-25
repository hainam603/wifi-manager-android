package com.antigravity.wifimanager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lưu WiFi cộng đồng đã tải xuống sử dụng cơ sở dữ liệu SQLite tối ưu để tra cứu nhanh khi ngoại tuyến.
 */
class SharedWifiOfflineStore(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val lock = Any()

    init {
        // Dọn dẹp tệp JSON cũ nếu tồn tại để giải phóng dung lượng bộ nhớ
        try {
            val oldJsonFile = File(context.filesDir, "shared_wifi_offline.json")
            if (oldJsonFile.exists()) {
                oldJsonFile.delete()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        companion object {
            const val DATABASE_NAME = "shared_wifi_offline.db"
            const val DATABASE_VERSION = 1
            const val TABLE_NAME = "wifi_credentials"
            const val COL_SSID = "ssid"
            const val COL_BSSID = "bssid"
            const val COL_PASSWORD = "password"
            const val COL_PROVIDER = "provider"
            const val COL_LAT = "lat"
            const val COL_LNG = "lng"
            const val COL_CACHED_AT = "cached_at"
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COL_SSID TEXT,
                    $COL_BSSID TEXT,
                    $COL_PASSWORD TEXT,
                    $COL_PROVIDER TEXT,
                    $COL_LAT REAL,
                    $COL_LNG REAL,
                    $COL_CACHED_AT INTEGER,
                    PRIMARY KEY ($COL_SSID, $COL_BSSID)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_lat_lng ON $TABLE_NAME ($COL_LAT, $COL_LNG)")
            db.execSQL("CREATE INDEX idx_bssid ON $TABLE_NAME ($COL_BSSID)")
            db.execSQL("CREATE INDEX idx_ssid ON $TABLE_NAME ($COL_SSID)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    fun count(): Int {
        synchronized(lock) {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_NAME}", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            }
            return 0
        }
    }

    fun getStorageBytes(): Long = synchronized(lock) {
        val dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME)
        return if (dbFile.exists()) dbFile.length() else 0L
    }

    fun getStorageMb(): Double = getStorageBytes() / (1024.0 * 1024.0)

    fun estimateMaxNetworksForStorageMb(storageMb: Int): Int {
        // SQLite lưu trữ có cấu trúc và index overhead, ước tính ~350 bytes trên mỗi bản ghi
        val bytes = storageMb.toLong() * 1024L * 1024L
        return (bytes / 350).toInt().coerceAtLeast(10_000)
    }

    fun clear() {
        synchronized(lock) {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_NAME}")
            db.execSQL("VACUUM")
        }
    }

    fun loadAll(): List<SharedWifiCredential> {
        synchronized(lock) {
            val list = mutableListOf<SharedWifiCredential>()
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_NAME}", null).use { cursor ->
                val colSsid = cursor.getColumnIndex(DatabaseHelper.COL_SSID)
                val colBssid = cursor.getColumnIndex(DatabaseHelper.COL_BSSID)
                val colPassword = cursor.getColumnIndex(DatabaseHelper.COL_PASSWORD)
                val colProvider = cursor.getColumnIndex(DatabaseHelper.COL_PROVIDER)
                val colLat = cursor.getColumnIndex(DatabaseHelper.COL_LAT)
                val colLng = cursor.getColumnIndex(DatabaseHelper.COL_LNG)
                val colCachedAt = cursor.getColumnIndex(DatabaseHelper.COL_CACHED_AT)

                while (cursor.moveToNext()) {
                    val ssid = cursor.getString(colSsid)
                    val password = cursor.getString(colPassword)
                    val bssidRaw = cursor.getString(colBssid)
                    val bssid = if (bssidRaw.isNullOrBlank()) null else bssidRaw
                    val provider = cursor.getString(colProvider)
                    val lat = if (cursor.isNull(colLat)) null else cursor.getDouble(colLat)
                    val lng = if (cursor.isNull(colLng)) null else cursor.getDouble(colLng)
                    val cachedAt = cursor.getLong(colCachedAt)

                    list.add(
                        SharedWifiCredential(
                            ssid = ssid,
                            password = password,
                            bssid = bssid,
                            providerName = provider,
                            latitude = lat,
                            longitude = lng,
                            cachedAtMs = cachedAt
                        )
                    )
                }
            }
            return list
        }
    }

    fun queryNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<SharedWifiCredential> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<SharedWifiCredential>()

        // 1. Tính toán Bounding Box địa lý để tìm kiếm nhanh bằng INDEX trong SQLite
        val latDegreePerM = 1.0 / 111320.0
        val lngDegreePerM = 1.0 / (111320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
        
        val deltaLat = radiusMeters * latDegreePerM
        val deltaLng = radiusMeters * lngDegreePerM
        
        val minLat = latitude - deltaLat
        val maxLat = latitude + deltaLat
        val minLng = longitude - deltaLng
        val maxLng = longitude + deltaLng

        synchronized(lock) {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                """
                SELECT * FROM ${DatabaseHelper.TABLE_NAME} 
                WHERE ${DatabaseHelper.COL_LAT} BETWEEN ? AND ? 
                  AND ${DatabaseHelper.COL_LNG} BETWEEN ? AND ?
                """.trimIndent(),
                arrayOf(minLat.toString(), maxLat.toString(), minLng.toString(), maxLng.toString())
            ).use { cursor ->
                val colSsid = cursor.getColumnIndex(DatabaseHelper.COL_SSID)
                val colBssid = cursor.getColumnIndex(DatabaseHelper.COL_BSSID)
                val colPassword = cursor.getColumnIndex(DatabaseHelper.COL_PASSWORD)
                val colProvider = cursor.getColumnIndex(DatabaseHelper.COL_PROVIDER)
                val colLat = cursor.getColumnIndex(DatabaseHelper.COL_LAT)
                val colLng = cursor.getColumnIndex(DatabaseHelper.COL_LNG)
                val colCachedAt = cursor.getColumnIndex(DatabaseHelper.COL_CACHED_AT)

                while (cursor.moveToNext()) {
                    val cachedAt = cursor.getLong(colCachedAt)
                    if (now - cachedAt > MAX_AGE_MS) continue

                    val lat = if (cursor.isNull(colLat)) null else cursor.getDouble(colLat)
                    val lng = if (cursor.isNull(colLng)) null else cursor.getDouble(colLng)
                    if (lat == null || lng == null) continue

                    // 2. Tính khoảng cách chính xác lượng giác cho tập dữ liệu kết quả nhỏ đã lọc
                    val dist = haversineMeters(latitude, longitude, lat, lng).toInt()
                    if (dist <= radiusMeters) {
                        val ssid = cursor.getString(colSsid)
                        val password = cursor.getString(colPassword)
                        val bssidRaw = cursor.getString(colBssid)
                        val bssid = if (bssidRaw.isNullOrBlank()) null else bssidRaw
                        val provider = cursor.getString(colProvider)

                        list.add(
                            SharedWifiCredential(
                                ssid = ssid,
                                password = password,
                                bssid = bssid,
                                providerName = provider,
                                latitude = lat,
                                longitude = lng,
                                cachedAtMs = cachedAt,
                                distanceMeters = dist
                            )
                        )
                    }
                }
            }
        }
        return list.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    fun upsert(
        credentials: List<SharedWifiCredential>,
        fallbackLat: Double,
        fallbackLng: Double,
        maxEntries: Int,
        maxStorageBytes: Long
    ) {
        if (credentials.isEmpty()) return

        synchronized(lock) {
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()

            db.beginTransaction()
            try {
                val stmt = db.compileStatement(
                    """
                    INSERT OR REPLACE INTO ${DatabaseHelper.TABLE_NAME} (
                        ${DatabaseHelper.COL_SSID}, 
                        ${DatabaseHelper.COL_BSSID}, 
                        ${DatabaseHelper.COL_PASSWORD}, 
                        ${DatabaseHelper.COL_PROVIDER}, 
                        ${DatabaseHelper.COL_LAT}, 
                        ${DatabaseHelper.COL_LNG}, 
                        ${DatabaseHelper.COL_CACHED_AT}
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                )

                credentials.forEach { raw ->
                    val bssidVal = raw.bssid.orEmpty().lowercase()
                    val latVal = raw.latitude ?: fallbackLat
                    val lngVal = raw.longitude ?: fallbackLng
                    val cachedVal = if (raw.cachedAtMs > 0L) raw.cachedAtMs else now

                    stmt.clearBindings()
                    stmt.bindString(1, raw.ssid)
                    stmt.bindString(2, bssidVal)
                    stmt.bindString(3, raw.password)
                    stmt.bindString(4, raw.providerName.orEmpty())
                    stmt.bindDouble(5, latVal)
                    stmt.bindDouble(6, lngVal)
                    stmt.bindLong(7, cachedVal)
                    stmt.executeInsert()
                }
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                db.endTransaction()
            }

            // 1. Tự động dọn dẹp các bản ghi quá hạn 90 ngày
            db.execSQL(
                "DELETE FROM ${DatabaseHelper.TABLE_NAME} WHERE ${DatabaseHelper.COL_CACHED_AT} < ?",
                arrayOf((now - MAX_AGE_MS).toString())
            )

            // 2. Giới hạn số lượng bản ghi tối đa
            db.execSQL(
                """
                DELETE FROM ${DatabaseHelper.TABLE_NAME} WHERE rowid NOT IN (
                    SELECT rowid FROM ${DatabaseHelper.TABLE_NAME} 
                    ORDER BY ${DatabaseHelper.COL_CACHED_AT} DESC LIMIT ?
                )
                """.trimIndent(),
                arrayOf(maxEntries.toString())
            )

            // 3. Giới hạn dung lượng tệp cơ sở dữ liệu
            val dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME)
            if (dbFile.exists() && dbFile.length() > maxStorageBytes) {
                // Xoá 15% số lượng bản ghi cũ nhất để giải phóng không gian
                db.execSQL(
                    """
                    DELETE FROM ${DatabaseHelper.TABLE_NAME} WHERE rowid IN (
                        SELECT rowid FROM ${DatabaseHelper.TABLE_NAME} 
                        ORDER BY ${DatabaseHelper.COL_CACHED_AT} ASC LIMIT (SELECT COUNT(*) * 15 / 100 FROM ${DatabaseHelper.TABLE_NAME})
                    )
                    """.trimIndent()
                )
                db.execSQL("VACUUM")
            }
        }
    }

    fun lookupCredential(ssid: String, bssid: String?): SharedWifiCredential? {
        synchronized(lock) {
            val db = dbHelper.readableDatabase
            val bssidVal = bssid.orEmpty().lowercase()
            db.rawQuery(
                """
                SELECT * FROM ${DatabaseHelper.TABLE_NAME} 
                WHERE ${DatabaseHelper.COL_SSID} = ? 
                ORDER BY (${DatabaseHelper.COL_BSSID} = ?) DESC, ${DatabaseHelper.COL_CACHED_AT} DESC 
                LIMIT 1
                """.trimIndent(),
                arrayOf(ssid, bssidVal)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val colSsid = cursor.getColumnIndex(DatabaseHelper.COL_SSID)
                    val colBssid = cursor.getColumnIndex(DatabaseHelper.COL_BSSID)
                    val colPassword = cursor.getColumnIndex(DatabaseHelper.COL_PASSWORD)
                    val colProvider = cursor.getColumnIndex(DatabaseHelper.COL_PROVIDER)
                    val colLat = cursor.getColumnIndex(DatabaseHelper.COL_LAT)
                    val colLng = cursor.getColumnIndex(DatabaseHelper.COL_LNG)
                    val colCachedAt = cursor.getColumnIndex(DatabaseHelper.COL_CACHED_AT)

                    val ssidRet = cursor.getString(colSsid)
                    val password = cursor.getString(colPassword)
                    val bssidRaw = cursor.getString(colBssid)
                    val bssidRet = if (bssidRaw.isNullOrBlank()) null else bssidRaw
                    val provider = cursor.getString(colProvider)
                    val lat = if (cursor.isNull(colLat)) null else cursor.getDouble(colLat)
                    val lng = if (cursor.isNull(colLng)) null else cursor.getDouble(colLng)
                    val cachedAt = cursor.getLong(colCachedAt)

                    return SharedWifiCredential(
                        ssid = ssidRet,
                        password = password,
                        bssid = bssidRet,
                        providerName = provider,
                        latitude = lat,
                        longitude = lng,
                        cachedAtMs = cachedAt
                    )
                }
            }
            return null
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
