package com.antigravity.wifimanager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class SplitDnsVpnService : VpnService() {

    companion object {
        private const val TAG = "SplitDnsVpnService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "wifi_monitor_channel_v2"
        const val ACTION_STOP = "com.antigravity.wifimanager.STOP_SPLIT_DNS_VPN"

        // DNS ảo của VPN — Android sẽ gửi toàn bộ query DNS tới đây
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_DNS = "10.0.0.2"

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, SplitDnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, SplitDnsVpnService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                // Sử dụng startService để gửi lệnh dừng (ACTION_STOP) khi app ở foreground.
                // Tránh dùng startForegroundService vì lệnh dừng không cần khởi động chạy nền lâu dài.
                context.startService(stopIntent)
            } catch (e: Exception) {
                // Fallback nếu ứng dụng ở background và startService bị giới hạn
                val intent = Intent(context, SplitDnsVpnService::class.java)
                context.stopService(intent)
            }
        }

        // Danh sách DoH theo thứ tự ưu tiên — ít bị corporate firewall chặn hơn 1.1.1.1/8.8.8.8
        val DOH_SERVERS = listOf(
            DoHServer("9.9.9.9",         "dns.quad9.net"),       // Quad9 — ít bị chặn
            DoHServer("149.112.112.112",  "dns.quad9.net"),       // Quad9 backup
            DoHServer("94.140.14.14",    "dns.adguard.com"),      // AdGuard
            DoHServer("94.140.15.15",    "dns.adguard.com"),      // AdGuard backup
            DoHServer("208.67.222.222",  "doh.opendns.com"),      // OpenDNS
            DoHServer("1.1.1.1",         "cloudflare-dns.com"),   // Cloudflare
            DoHServer("1.0.0.1",         "cloudflare-dns.com"),   // Cloudflare backup
            DoHServer("8.8.8.8",         "dns.google"),           // Google
            DoHServer("8.8.4.4",         "dns.google")            // Google backup
        )
    }

    data class DoHServer(val ip: String, val sniHost: String, val path: String = "/dns-query")

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    @Volatile
    private var isVpnRunning = false

    // DNS công ty — được đọc TRƯỚC khi VPN establish (lúc đó activeNetwork = WiFi thật)
    private var companyDnsServer: String = "8.8.8.8"

    // Cache DoH server hoạt động để tránh retry mỗi lần
    @Volatile
    private var workingDoHServer: DoHServer? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.i(TAG, "🚀 SplitDnsVpnService created")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Xử lý lệnh dừng từ switch UI
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "🛑 Nhận ACTION_STOP — đang dừng VPN...")
            closeInterface() // Đóng VPN interface ngay lập tức!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn()
        return START_NOT_STICKY  // Không tự restart sau khi bị dừng
    }

    private fun buildNotification() = run {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Giám sát WiFi nền", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.antigravity.wifimanager.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bypass chặn WiFi (Split DNS)")
            .setContentText("Đang vượt chặn Facebook/Messenger qua DNS-over-HTTPS...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Synchronized
    private fun startVpn() {
        if (isVpnRunning) return

        // ⚠️ Đọc DNS THẬT của WiFi TRƯỚC KHI gọi builder.establish()
        // Sau khi VPN active, activeNetwork sẽ trả về DNS ảo 10.0.0.2
        companyDnsServer = readRealWifiDns()
        Log.i(TAG, "🏢 DNS công ty (cached): $companyDnsServer")

        val iface = buildVpnInterface()
        if (iface == null) {
            Log.e(TAG, "❌ Không thể thiết lập VPN interface")
            stopSelf()
            return
        }
        vpnInterface = iface
        isVpnRunning = true

        vpnThread = Thread({ runVpnLoop(iface) }, "SplitDnsLoop").apply { start() }
        Log.i(TAG, "✅ Split DNS VPN khởi động thành công")
    }

    /** Đọc DNS của WiFi thật — phải gọi trước khi VPN establish */
    private fun readRealWifiDns(): String {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val lp = cm.getLinkProperties(cm.activeNetwork)
                // Ưu tiên IPv4 để tránh vấn đề với IPv6
                lp?.dnsServers
                    ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                    ?.hostAddress ?: "8.8.8.8"
            } else "8.8.8.8"
        } catch (_: Exception) { "8.8.8.8" }
    }

    private fun buildVpnInterface(): ParcelFileDescriptor? = try {
        Builder()
            .setSession("Split DNS Bypass")
            .addAddress(VPN_ADDRESS, 24)
            // Trỏ DNS hệ thống về server ảo của chúng ta
            .addDnsServer(VPN_DNS)
            // CHỈ route traffic đến DNS ảo 10.0.0.2 — KHÔNG route 8.8.8.8/1.1.1.1
            // vì nếu route các IP đó, DoH protected socket sẽ bị kẹt vào VPN (loop)
            .addRoute(VPN_DNS, 32)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
            }
            .establish()
    } catch (e: Exception) {
        Log.e(TAG, "Lỗi build VPN interface: ${e.message}", e)
        null
    }

    private fun runVpnLoop(iface: ParcelFileDescriptor) {
        val fd = iface.fileDescriptor
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buf = ByteArray(32768)

        Log.i(TAG, "📡 Bắt đầu vòng lặp đọc gói tin VPN...")
        try {
            while (isVpnRunning && !Thread.currentThread().isInterrupted) {
                val len = input.read(buf)
                if (len <= 0) continue
                // Xử lý gói tin bất đồng bộ để không block vòng đọc
                val packet = buf.copyOf(len)
                serviceScope.launch { handlePacket(packet, output) }
            }
        } catch (e: Exception) {
            if (isVpnRunning) Log.e(TAG, "Lỗi vòng lặp VPN: ${e.message}", e)
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
            closeInterface()
        }
    }

    private suspend fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        val len = packet.size
        if (len < 28) return  // Tối thiểu IP(20) + UDP(8)

        // Chỉ xử lý IPv4
        val version = (packet[0].toInt() and 0xF0) shr 4
        if (version != 4) return

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF

        // Chỉ xử lý UDP (17)
        if (protocol != 17) return

        val udpStart = ipHeaderLen
        if (udpStart + 8 > len) return

        val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                (packet[udpStart + 3].toInt() and 0xFF)
        val srcPort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                (packet[udpStart + 1].toInt() and 0xFF)

        // Chỉ xử lý DNS (cổng 53)
        if (destPort != 53) return

        val dnsStart = udpStart + 8
        val dnsLen = len - dnsStart
        if (dnsLen <= 0) return

        val dnsQuery = packet.copyOfRange(dnsStart, len)
        val srcIp = packet.copyOfRange(12, 16)
        val destIp = packet.copyOfRange(16, 20)

        val domain = parseDnsQueryDomain(dnsQuery)
        Log.d(TAG, "🔍 DNS query: $domain")

        val responsePayload: ByteArray? = if (isBypassTarget(domain)) {
            Log.i(TAG, "🚀 BYPASS: $domain → DoH")
            forwardDoHWithFallback(dnsQuery)
        } else {
            // Dùng DNS công ty đã cache từ TRƯỚC khi VPN establish
            Log.d(TAG, "💼 INTRANET: $domain → $companyDnsServer")
            forwardDnsUdp(dnsQuery, companyDnsServer)
                ?: forwardDnsUdp(dnsQuery, "9.9.9.9")   // fallback Quad9 nếu công ty DNS fail
        }

        if (responsePayload == null) {
            Log.w(TAG, "⚠️ Không nhận được phản hồi DNS cho: $domain")
            return
        }

        // Xây dựng gói IP/UDP phản hồi và ghi về TUN
        val responsePacket = buildIpUdpPacket(
            srcIp = destIp,   // IP nguồn = IP đích của query (DNS server ảo)
            destIp = srcIp,   // IP đích = IP nguồn của query (client)
            srcPort = 53,
            destPort = srcPort,
            payload = responsePayload
        )
        synchronized(output) {
            try { output.write(responsePacket) } catch (e: Exception) { /* ignore */ }
        }
    }

    // ─── Phân tích tên miền từ DNS query binary ────────────────────────────────

    private fun parseDnsQueryDomain(data: ByteArray): String {
        val sb = StringBuilder()
        var pos = 12 // Bỏ qua DNS Header (12 bytes)
        if (pos >= data.size) return ""
        try {
            while (pos < data.size) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) break
                pos++
                if (pos + labelLen > data.size) return sb.toString()
                if (sb.isNotEmpty()) sb.append('.')
                repeat(labelLen) { sb.append(Char(data[pos + it].toInt() and 0xFF)) }
                pos += labelLen
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    // ─── Kiểm tra domain cần bypass ───────────────────────────────────────────

    private fun isBypassTarget(domain: String): Boolean {
        val d = domain.lowercase()
        return listOf(
            "facebook", "fbcdn", "fbsbx", "fb.com", "fb.me", "m.me",
            "instagram", "cdninstagram", "messenger", "tfbnw.net", "facebook.net",
            "whatsapp", "wa.me"
        ).any { d.contains(it) }
    }

    // companyDnsServer field thay thế getCompanyDns() — được set trong startVpn()

    // ─── DoH với multi-server fallback + caching ───────────────────────────────

    private fun forwardDoHWithFallback(dnsQuery: ByteArray): ByteArray? {
        // Thử server đã cache trước (nhanh)
        workingDoHServer?.let { cached ->
            val result = forwardDoH(dnsQuery, cached.ip, cached.sniHost, cached.path, timeoutMs = 3000)
            if (result != null) return result
            // Cache hết hạn — reset
            workingDoHServer = null
            Log.w(TAG, "⚠️ DoH server cache (${cached.ip}) không còn hoạt động, thử lại...")
        }

        // Thử từng server trong danh sách
        for (server in DOH_SERVERS) {
            val result = forwardDoH(dnsQuery, server.ip, server.sniHost, server.path, timeoutMs = 2500)
            if (result != null) {
                workingDoHServer = server  // Cache server thành công
                Log.i(TAG, "✅ DoH server hoạt động: ${server.ip} (${server.sniHost})")
                return result
            }
        }

        // Tất cả DoH fail → fallback DNS/TCP rồi UDP
        Log.w(TAG, "⚠️ Tất cả DoH thất bại, fallback DNS/TCP...")
        return forwardDnsTcp(dnsQuery, "9.9.9.9")
            ?: forwardDnsTcp(dnsQuery, "8.8.8.8")
            ?: forwardDnsUdp(dnsQuery, "9.9.9.9")
            ?: forwardDnsUdp(dnsQuery, "8.8.8.8")
    }

    // ─── DNS over HTTPS (RFC 8484) qua raw SSL socket + protect() ─────────────
    // serverIp  : IP thực của server
    // sniHost   : domain cho SNI & cert verification
    // Quan trọng: IP này KHÔNG được nằm trong addRoute() VPN!

    private fun forwardDoH(dnsQuery: ByteArray, serverIp: String, sniHost: String,
                           path: String = "/dns-query", timeoutMs: Int = 2500): ByteArray? {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        return try {
            rawSocket = Socket()
            val ok = protect(rawSocket)  // bypass VPN — PHẢI trả về true
            if (!ok) {
                Log.w(TAG, "DoH protect() thất bại cho $serverIp")
                return null
            }
            rawSocket.connect(InetSocketAddress(serverIp, 443), timeoutMs)
            rawSocket.soTimeout = timeoutMs + 1000

            val sslCtx = SSLContext.getInstance("TLS")
            sslCtx.init(null, null, null)
            // createSocket(underlying, sniHost, port, autoClose)
            // sniHost dùng cho SNI + certificate hostname verification
            sslSocket = sslCtx.socketFactory
                .createSocket(rawSocket, sniHost, 443, true) as SSLSocket
            sslSocket.startHandshake()

            // HTTP/1.0 (không chunked) để parse body đơn giản hơn
            val httpReq = "POST $path HTTP/1.0\r\n" +
                "Host: $sniHost\r\n" +
                "Content-Type: application/dns-message\r\n" +
                "Accept: application/dns-message\r\n" +
                "Content-Length: ${dnsQuery.size}\r\n\r\n"

            val out = sslSocket.outputStream
            out.write(httpReq.toByteArray(Charsets.US_ASCII))
            out.write(dnsQuery)
            out.flush()

            val responseBytes = sslSocket.inputStream.readBytes()
            Log.d(TAG, "DoH OK: $serverIp response ${responseBytes.size} bytes")
            extractHttpBody(responseBytes)
        } catch (e: Exception) {
            Log.w(TAG, "DoH $serverIp thất bại: ${e.message}")
            null
        } finally {
            runCatching { sslSocket?.close() }
            runCatching { rawSocket?.close() }
        }
    }

    // Tìm phần body sau \r\n\r\n trong HTTP response
    private fun extractHttpBody(data: ByteArray): ByteArray? {
        for (i in 0 until data.size - 3) {
            if (data[i] == '\r'.code.toByte() &&
                data[i + 1] == '\n'.code.toByte() &&
                data[i + 2] == '\r'.code.toByte() &&
                data[i + 3] == '\n'.code.toByte()
            ) {
                val body = data.copyOfRange(i + 4, data.size)
                return if (body.isNotEmpty()) body else null
            }
        }
        return null
    }

    // ─── DNS over TCP (fallback khi DoH thất bại) ─────────────────────────────

    private fun forwardDnsTcp(dnsQuery: ByteArray, server: String): ByteArray? {
        var sock: Socket? = null
        return try {
            sock = Socket()
            protect(sock)
            sock.connect(InetSocketAddress(server, 53), 4000)
            sock.soTimeout = 4000

            val out = sock.outputStream
            // DNS/TCP: 2-byte length prefix
            out.write((dnsQuery.size shr 8) and 0xFF)
            out.write(dnsQuery.size and 0xFF)
            out.write(dnsQuery)
            out.flush()

            val ins = sock.inputStream
            val respLen = (ins.read() shl 8) or ins.read()
            if (respLen <= 0) return null
            val buf = ByteArray(respLen)
            var read = 0
            while (read < respLen) {
                val r = ins.read(buf, read, respLen - read)
                if (r < 0) break
                read += r
            }
            if (read == respLen) buf else null
        } catch (e: Exception) {
            Log.w(TAG, "DNS/TCP thất bại: ${e.message}")
            null
        } finally {
            runCatching { sock?.close() }
        }
    }

    // ─── DNS over UDP (fallback cuối cùng) ────────────────────────────────────

    private fun forwardDnsUdp(dnsQuery: ByteArray, server: String): ByteArray? {
        var sock: DatagramSocket? = null
        return try {
            sock = DatagramSocket()
            protect(sock)
            sock.soTimeout = 3000
            val addr = InetAddress.getByName(server)
            sock.send(DatagramPacket(dnsQuery, dnsQuery.size, addr, 53))
            val buf = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            resp.data.copyOfRange(0, resp.length)
        } catch (e: Exception) {
            Log.w(TAG, "DNS/UDP thất bại: ${e.message}")
            null
        } finally {
            runCatching { sock?.close() }
        }
    }

    // ─── Xây dựng gói tin IP/UDP phản hồi ──────────────────────────────────────

    private fun buildIpUdpPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + payload.size
        val pkt = ByteArray(totalLen)

        // IP Header
        pkt[0] = 0x45.toByte()
        pkt[1] = 0x00.toByte()
        pkt[2] = (totalLen shr 8).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[6] = 0x40.toByte()  // Don't fragment
        pkt[8] = 64.toByte()    // TTL
        pkt[9] = 17.toByte()    // UDP
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(destIp, 0, pkt, 16, 4)
        val ipCsum = checksum(pkt, 0, 20)
        pkt[10] = (ipCsum shr 8).toByte()
        pkt[11] = (ipCsum and 0xFF).toByte()

        // UDP Header
        pkt[20] = (srcPort shr 8).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (destPort shr 8).toByte()
        pkt[23] = (destPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()

        // Payload
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        return pkt
    }

    private fun checksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < off + len) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    // ─── Cleanup ────────────────────────────────────────────────────────────────

    private fun closeInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        isVpnRunning = false
    }

    override fun onDestroy() {
        Log.i(TAG, "🛑 SplitDnsVpnService đang dừng...")
        isVpnRunning = false
        isServiceRunning = false
        vpnThread?.interrupt()
        closeInterface()
        serviceJob.cancel()
        super.onDestroy()
    }
}
