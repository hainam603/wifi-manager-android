package com.antigravity.wifimanager.data

import android.util.Log
import java.net.URL
import java.util.Locale
import kotlin.random.Random

/**
 * Động cơ AI Học máy Cục bộ (Local AI Engine) chạy Offline 100% trên thiết bị.
 * Tích hợp 2 Mô hình Heuristic thông minh:
 * 1. Smart Switcher: Quyết định chuyển mạng tối ưu dựa trên sóng, băng tần, lịch sử lỗi và trạng thái di chuyển.
 * 2. Smart Captive Portal Bypass: Tự động phân tích DOM/HTML, dự đoán nút bấm kết nối và tự sinh thông tin xác thực.
 */
object WifiLocalAiEngine {

    private const val TAG = "WifiLocalAiEngine"

    // --- MÔ HÌNH 1: SMART SWITCH DECISION ENGINE ---
    
    data class SwitchDecision(
        val shouldSwitch: Boolean,
        val score: Double, // Điểm số từ 0.0 đến 1.0
        val reason: String
    )

    /**
     * Chạy suy luận (inference) để quyết định có nên chuyển đổi mạng hay không.
     * Thay thế thuật toán so sánh cứng truyền thống.
     */
    fun evaluateSwitch(
        current: WifiConnectionState,
        target: WifiApInfo,
        userActivity: String = "STILL", // STILL, WALKING, IN_VEHICLE
        failedAttemptsCount: Int = 0,
        prefer5Ghz: Boolean = true
    ): SwitchDecision {
        // 1. Luật loại trừ di chuyển nhanh (Vehicle Filter)
        if (userActivity == "IN_VEHICLE") {
            return SwitchDecision(
                shouldSwitch = false,
                score = 0.1,
                reason = "Thiết bị đang di chuyển nhanh trên phương tiện. Tạm dừng chuyển đổi để tránh rớt mạng liên tục."
            )
        }

        // 2. Luật loại trừ mạng lỗi (Failure Cooldown Filter)
        if (failedAttemptsCount >= 2) {
            return SwitchDecision(
                shouldSwitch = false,
                score = 0.05,
                reason = "Mạng mục tiêu '${target.ssid}' đã thất bại $failedAttemptsCount lần gần đây. Đang trong thời gian phạt tránh bẫy mạng hỏng."
            )
        }

        // 3. Tính toán điểm sóng cơ bản (Base RSSI Score)
        val signalDiff = target.signalPercent - current.signalPercent
        var score = 0.5 + (signalDiff / 100.0) // Sóng khỏe hơn sẽ tăng điểm

        // 4. Cộng điểm ưu tiên Băng tần (Band Upgrade Bonus)
        if (prefer5Ghz && target.is5GHz && !current.is5GHz) {
            score += 0.25 // Thêm 25% điểm thưởng khi nâng cấp từ 2.4Ghz lên 5Ghz
            Log.d(TAG, "Smart Switcher: Thưởng +25% điểm nâng cấp băng tần 5GHz cho '${target.ssid}'")
        }

        // 5. Phạt điểm di chuyển chậm (Walking Penalty)
        if (userActivity == "WALKING") {
            score -= 0.15 // Trừ 15% điểm nếu người dùng đang đi bộ (tránh chuyển đổi quá nhạy khi sóng dao động nhẹ)
            Log.d(TAG, "Smart Switcher: Phạt -15% điểm do người dùng đang di chuyển đi bộ")
        }

        // 6. Phạt điểm chênh lệch sóng quá nhỏ
        if (signalDiff < 10 && !(target.is5GHz && !current.is5GHz)) {
            score -= 0.2 // Không đáng để ngắt mạng cũ nếu sóng mới không vượt trội ít nhất 10%
        }

        // Giới hạn điểm số từ 0.0 đến 1.0
        val finalScore = score.coerceIn(0.0, 1.0)
        val shouldSwitch = finalScore >= 0.75 // Ngưỡng kích hoạt chuyển mạng tối ưu là 75%

        val reason = when {
            shouldSwitch && target.is5GHz && !current.is5GHz -> 
                "Khuyên dùng: Nên chuyển sang mạng '${target.ssid}' (5GHz, sóng ${target.signalPercent}%) để nâng cấp băng thông siêu tốc (Điểm AI: ${(finalScore * 100).toInt()}%)."
            shouldSwitch -> 
                "Khuyên dùng: Mạng '${target.ssid}' có chất lượng sóng vượt trội (+${signalDiff}%) ổn định hơn mạng hiện tại (Điểm AI: ${(finalScore * 100).toInt()}%)."
            else -> 
                "Giữ nguyên: Mạng hiện tại '${current.ssid}' vẫn tối ưu hơn hoặc mạng mục tiêu chưa đủ độ tin cậy để chuyển đổi (Điểm AI: ${(finalScore * 100).toInt()}%)."
        }

        return SwitchDecision(shouldSwitch, finalScore, reason)
    }

    // --- MÔ HÌNH 2: SMART CAPTIVE PORTAL BYPASS MODEL ---

    data class BypassPlan(
        val targetUrl: String,
        val method: String,
        val params: Map<String, String>,
        val portalName: String
    )

    /**
     * Phân tích Heuristic cấu trúc HTML của trang Cổng chào để tự động sinh dữ liệu kết nối.
     */
    fun analyzeCaptivePortal(html: String, portalUrl: String): BypassPlan? {
        val lowerHtml = html.lowercase(Locale.getDefault())
        val lowerUrl = portalUrl.lowercase(Locale.getDefault())

        // 1. Nhận diện tên nhà mạng/chuỗi cửa hàng
        val portalName = when {
            lowerHtml.contains("circlek") || lowerUrl.contains("circlek") -> "Circle K VN"
            lowerHtml.contains("highlands") || lowerUrl.contains("highlands") -> "Highlands Coffee"
            lowerHtml.contains("starbucks") || lowerUrl.contains("starbucks") -> "Starbucks"
            lowerHtml.contains("gs25") || lowerUrl.contains("gs25") -> "GS25 VN"
            lowerHtml.contains("passio") || lowerUrl.contains("passio") -> "Passio Coffee"
            lowerHtml.contains("phuclong") || lowerUrl.contains("phuclong") -> "Phúc Long Tea"
            lowerHtml.contains("thecoffeehouse") || lowerUrl.contains("thecoffeehouse") -> "The Coffee House"
            else -> {
                val titleRegex = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                titleRegex.find(html)?.groupValues?.get(1)?.trim() ?: "WiFi Công Cộng"
            }
        }

        // 2. Tìm thẻ Form trong trang
        val formTagRegex = Regex("""<form([^>]+)>""", RegexOption.IGNORE_CASE)
        val formMatch = formTagRegex.find(html)

        val targetAction: String
        val method: String
        val inputs = mutableMapOf<String, String>()

        if (formMatch != null) {
            val attrs = formMatch.groupValues[1]
            // Trích xuất Action
            val actionRegex = Regex("""action\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            targetAction = actionRegex.find(attrs)?.groupValues?.get(1) ?: ""
            
            // Trích xuất Method
            val methodRegex = Regex("""method\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            method = methodRegex.find(attrs)?.groupValues?.get(1)?.uppercase(Locale.getDefault()) ?: "POST"

            // Giới hạn vùng phân tích inputs
            val startIndex = formMatch.range.first
            val endIndex = html.indexOf("</form>", startIndex, ignoreCase = true)
            val formContent = if (endIndex != -1) html.substring(startIndex, endIndex) else html.substring(startIndex)

            // Quét và phân tích Heuristic các thẻ Input
            val inputRegex = Regex("""<input\s+([^>]+)>""", RegexOption.IGNORE_CASE)
            inputRegex.findAll(formContent).forEach { inputMatch ->
                val inputAttrs = inputMatch.groupValues[1]
                val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val typeRegex = Regex("""type\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                val valRegex = Regex("""value\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

                val name = nameRegex.find(inputAttrs)?.groupValues?.get(1)
                val type = typeRegex.find(inputAttrs)?.groupValues?.get(1)?.lowercase(Locale.getDefault()) ?: "text"
                val value = valRegex.find(inputAttrs)?.groupValues?.get(1) ?: ""

                if (!name.isNullOrBlank()) {
                    when {
                        // AI tự phát hiện trường Số điện thoại
                        name.contains("phone", ignoreCase = true) || 
                        name.contains("sdt", ignoreCase = true) || 
                        name.contains("mobile", ignoreCase = true) -> {
                            inputs[name] = generateVnPhoneNumber()
                            Log.d(TAG, "Smart Bypass: AI tự điền Số điện thoại giả lập vào trường '$name'")
                        }
                        // AI tự phát hiện trường Email
                        name.contains("email", ignoreCase = true) || 
                        name.contains("mail", ignoreCase = true) -> {
                            inputs[name] = "user.${Random.nextInt(100, 999)}@gmail.com"
                            Log.d(TAG, "Smart Bypass: AI tự điền Email giả lập vào trường '$name'")
                        }
                        // AI tự phát hiện các ô checkbox đồng ý điều khoản
                        type == "checkbox" || name.contains("agree", ignoreCase = true) || name.contains("accept", ignoreCase = true) -> {
                            inputs[name] = "1"
                            Log.d(TAG, "Smart Bypass: AI tự động đồng ý điều khoản '$name'")
                        }
                        type != "submit" && type != "button" -> {
                            inputs[name] = value
                        }
                    }
                }
            }

            // Đảm bảo có tham số kích hoạt kết nối cơ bản của Router
            if (!inputs.containsKey("submit") && !inputs.containsKey("connect")) {
                inputs["submit"] = "Connect"
                inputs["agree"] = "1"
            }
        } else {
            // Trường hợp 1 chạm đơn giản không có form, chỉ có thẻ <a> (Direct link)
            val anchorRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
            var directLink: String? = null
            
            for (match in anchorRegex.findAll(html)) {
                val href = match.groupValues[1]
                val text = match.groupValues[2].lowercase(Locale.getDefault())
                
                if (text.contains("kết nối") || text.contains("connect") || text.contains("đồng ý") ||
                    text.contains("truy cập") || text.contains("internet") || text.contains("free") || text.contains("agree")
                ) {
                    if (!href.startsWith("javascript:", ignoreCase = true) && href.isNotBlank()) {
                        directLink = href
                        break
                    }
                }
            }

            if (directLink != null) {
                targetAction = directLink
                method = "GET"
            } else {
                return null // Không phân tích được cấu trúc trang
            }
        }

        val resolvedUrl = resolveAbsoluteUrl(portalUrl, targetAction)
        return BypassPlan(resolvedUrl, method, inputs, portalName)
    }

    // --- HÀM HELPER TIỆN ÍCH ---

    /** Tự sinh số điện thoại hợp lệ theo các đầu số mạng di động Việt Nam */
    private fun generateVnPhoneNumber(): String {
        val prefixes = listOf("090", "091", "098", "097", "096", "086", "032", "033", "034", "035", "038", "070", "079", "077", "081", "082")
        val randPrefix = prefixes[Random.nextInt(prefixes.size)]
        val suffix = String.format(Locale.US, "%07d", Random.nextInt(1000000, 9999999))
        return randPrefix + suffix
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://", ignoreCase = true) || relativeUrl.startsWith("https://", ignoreCase = true)) {
            return relativeUrl
        }
        val base = URL(baseUrl)
        if (relativeUrl.startsWith("//")) {
            return "${base.protocol}:$relativeUrl"
        }
        if (relativeUrl.startsWith("/")) {
            val portString = if (base.port != -1) ":" + base.port else ""
            return "${base.protocol}://${base.host}$portString$relativeUrl"
        }
        
        val basePath = base.path
        val dir = if (basePath.contains("/")) {
            basePath.substring(0, basePath.lastIndexOf('/') + 1)
        } else {
            "/"
        }
        val portString = if (base.port != -1) ":" + base.port else ""
        return "${base.protocol}://${base.host}$portString$dir$relativeUrl"
    }

    // --- MÔ HÌNH 3: LOCAL PASSWORD PATTERN & SIMILARITY PREDICTOR ---

    data class PasswordPrediction(
        val password: String,
        val confidence: Double, // Độ tin cậy từ 0.0 đến 1.0
        val source: String // Mô tả nguồn sinh ra
    )

    /**
     * Huấn luyện trực tiếp trên thiết bị (On-device ML) từ kho mật khẩu offline
     * để dự đoán 3 gợi ý mật khẩu thông minh nhất cho WiFi mục tiêu.
     */
    fun predictPasswords(
        targetSsid: String,
        targetBssid: String?,
        savedPasswords: Map<String, String>,
        offlineCredentials: List<SharedWifiCredential>
    ): List<PasswordPrediction> {
        val cleanTargetSsid = targetSsid.replace("\"", "").trim()
        val lowerTargetSsid = cleanTargetSsid.lowercase(Locale.getDefault())

        val candidates = mutableListOf<PasswordPrediction>()

        // 1. Thu thập tất cả các cặp (SSID, BSSID, Password) từ 2 nguồn offline
        val allCredentials = mutableMapOf<String, MutableSet<String>>() // ssid.lowercase -> set of passwords
        val bssidMap = mutableMapOf<String, String>() // bssid -> password
        val exactMatchPasswords = mutableSetOf<String>()

        // A. Nạp kho mật khẩu của người dùng tự lưu
        savedPasswords.forEach { (key, value) ->
            if (WifiCredentialKeys.isPlausibleWifiPassword(value)) {
                val (storedSsid, storedBssid) = WifiCredentialKeys.parseStorageKey(key)
                val cleanStoredSsid = storedSsid.replace("\"", "").trim()
                val lowerStored = cleanStoredSsid.lowercase(Locale.getDefault())
                
                allCredentials.getOrPut(lowerStored) { mutableSetOf() }.add(value)
                
                storedBssid?.let {
                    bssidMap[WifiCredentialKeys.normalizeBssid(it)] = value
                }

                if (cleanStoredSsid.equals(cleanTargetSsid, ignoreCase = true)) {
                    exactMatchPasswords.add(value)
                }
                
                targetBssid?.let { tb ->
                    if (storedBssid != null && WifiCredentialKeys.normalizeBssid(tb) == WifiCredentialKeys.normalizeBssid(storedBssid)) {
                        exactMatchPasswords.add(value)
                    }
                }
            }
        }

        // B. Nạp kho mật khẩu cộng đồng đã tải offline về máy
        offlineCredentials.forEach { cred ->
            if (WifiCredentialKeys.isPlausibleWifiPassword(cred.password)) {
                val cleanStoredSsid = cred.ssid.replace("\"", "").trim()
                val lowerStored = cleanStoredSsid.lowercase(Locale.getDefault())
                
                allCredentials.getOrPut(lowerStored) { mutableSetOf() }.add(cred.password)
                
                cred.bssid?.let {
                    bssidMap[WifiCredentialKeys.normalizeBssid(it)] = cred.password
                }

                if (cleanStoredSsid.equals(cleanTargetSsid, ignoreCase = true)) {
                    exactMatchPasswords.add(cred.password)
                }

                targetBssid?.let { tb ->
                    if (cred.bssid != null && WifiCredentialKeys.normalizeBssid(tb) == WifiCredentialKeys.normalizeBssid(cred.bssid)) {
                        exactMatchPasswords.add(cred.password)
                    }
                }
            }
        }

        // 2. Phân tích luật kết hợp và tương đồng (Rule & Pattern Inference)

        // C. ƯU TIÊN 1: Khớp chính xác (BSSID hoặc SSID) -> Độ tin cậy tuyệt đối
        exactMatchPasswords.forEach { pass ->
            candidates.add(PasswordPrediction(pass, 0.99, "Trùng khớp SSID"))
        }

        if (targetBssid != null) {
            val normBssid = WifiCredentialKeys.normalizeBssid(targetBssid)
            bssidMap[normBssid]?.let { pass ->
                candidates.add(PasswordPrediction(pass, 0.99, "Trùng khớp BSSID"))
            }
        }

        // D. ƯU TIÊN 2: Tương tự SSID qua khoảng cách Levenshtein (ví dụ: Highlands_Floor1 -> Highlands_Floor2)
        allCredentials.forEach { (storedSsidLower, passwords) ->
            if (storedSsidLower != lowerTargetSsid) {
                val similarity = getSimilarity(lowerTargetSsid, storedSsidLower)
                if (similarity >= 0.70) {
                    passwords.forEach { pass ->
                        val percent = (similarity * 100).toInt()
                        candidates.add(
                            PasswordPrediction(
                                pass, 
                                similarity * 0.92, 
                                "Tương đồng SSID '$storedSsidLower' ($percent%)"
                            )
                        )
                    }
                }
            }
        }

        // E. ƯU TIÊN 3: Khai phá mẫu mật khẩu phổ biến nhất kho (Pattern Mining)
        val allPasswordsInStore = mutableListOf<String>()
        allCredentials.values.forEach { allPasswordsInStore.addAll(it) }

        if (allPasswordsInStore.isNotEmpty()) {
            // Đếm tần suất xuất hiện của các hậu tố phổ biến
            val suffixCounts = mutableMapOf<String, Int>()
            val candidatesForSuffix = listOf("123", "@123", "123456", "8888", "2026", "@2026", "vn", "@123456")
            
            allPasswordsInStore.forEach { pass ->
                candidatesForSuffix.forEach { suffix ->
                    if (pass.endsWith(suffix)) {
                        suffixCounts[suffix] = (suffixCounts[suffix] ?: 0) + 1
                    }
                }
            }
            
            val topSuffixes = suffixCounts.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(2)
                .toMutableList()
            
            if (topSuffixes.size < 2) {
                val defaultSuffixes = listOf("123", "@123")
                defaultSuffixes.forEach { if (!topSuffixes.contains(it)) topSuffixes.add(it) }
            }

            // Tạo mật khẩu phỏng đoán dựa trên SSID mục tiêu + Hậu tố được học nhiều nhất
            val cleanNoSpaceSsid = cleanTargetSsid.replace(" ", "").lowercase(Locale.getDefault())
            topSuffixes.forEach { suffix ->
                if (cleanNoSpaceSsid.length >= 3) {
                    val genPass = cleanNoSpaceSsid + suffix
                    candidates.add(PasswordPrediction(genPass, 0.78, "Mẫu học máy '$suffix'"))
                }
            }

            // Tìm mật khẩu lặp lại nhiều nhất trong toàn bộ kho
            val passCounts = allPasswordsInStore.groupingBy { it }.eachCount()
            val topGlobalPasswords = passCounts.entries
                .sortedByDescending { it.value }
                .take(3)
            
            topGlobalPasswords.forEach { entry ->
                val freq = entry.value
                val pct = minOf(0.85, 0.55 + (freq * 0.05))
                candidates.add(PasswordPrediction(entry.key, pct, "Mật khẩu dùng chung ($freq lần)"))
            }
        }

        // F. ƯU TIÊN 4: Mật khẩu quốc dân phòng hờ
        val nationalFallbacks = listOf("12345678", "88888888", "00000000", "11111111", "99999999", "66666666")
        nationalFallbacks.forEach { fallback ->
            candidates.add(PasswordPrediction(fallback, 0.35, "Mật khẩu phổ biến"))
        }

        // Lọc trùng và trả về 3 kết quả tốt nhất xếp theo độ tin cậy
        return candidates
            .sortedByDescending { it.confidence }
            .distinctBy { it.password.trim() }
            .take(3)
    }

    private fun getLevenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j - 1], dp[j], prev) + 1
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun getSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = getLevenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }
}

