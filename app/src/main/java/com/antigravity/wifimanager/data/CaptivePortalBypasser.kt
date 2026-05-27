package com.antigravity.wifimanager.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Tự động phát hiện và vượt qua (bypass) trang cổng chào xác thực WiFi (Captive Portal).
 * Thiết kế cho các trang xác thực "Một chạm" (One-Click) thường thấy ở Circle K, GS25, quán cafe...
 */
object CaptivePortalBypasser {

    private const val TAG = "CaptivePortalBypasser"
    private const val CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"

    data class BypassResult(
        val attempted: Boolean,
        val success: Boolean,
        val portalName: String?,
        val message: String
    )

    private fun getWifiNetwork(context: Context): android.net.Network? {
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return cm.allNetworks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                } ?: cm.activeNetwork
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy WiFi Network", e)
        }
        return null
    }

    private fun openConnection(context: Context, url: URL): HttpURLConnection {
        val network = getWifiNetwork(context)
        return if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "Định tuyến kết nối qua WiFi Network cụ thể: $network")
            network.openConnection(url) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
    }

    private fun reportNetworkConnectivity(context: Context, hasConnectivity: Boolean) {
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = getWifiNetwork(context)
            if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "Báo cáo trạng thái kết nối mạng cho OS: network=$network, hasConnectivity=$hasConnectivity")
                cm.reportNetworkConnectivity(network, hasConnectivity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi báo cáo trạng thái kết nối mạng lên OS", e)
        }
    }

    private fun extractRedirectUrlFromHtml(html: String): String? {
        try {
            // 1. Thử tìm thẻ meta refresh: <meta http-equiv="refresh" content="0;url=http://192.168.1.1/login">
            val metaRegex = Regex("""<meta[^>]+http-equiv\s*=\s*["']refresh["'][^>]+url\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            metaRegex.find(html)?.groupValues?.get(1)?.let { return it.trim() }

            val metaRegex2 = Regex("""content\s*=\s*["'][^"']*url\s*=\s*([^"']+)["']""", RegexOption.IGNORE_CASE)
            metaRegex2.find(html)?.groupValues?.get(1)?.let { return it.trim() }

            // Support no-quotes
            val metaNoQuotesRegex = Regex("""content\s*=\s*[^>]*url\s*=\s*([^\s"'>]+)""", RegexOption.IGNORE_CASE)
            metaNoQuotesRegex.find(html)?.groupValues?.get(1)?.let { return it.trim() }

            // 2. Thử tìm javascript redirect: window.location.href = "..." hoặc window.location = "..." hoặc location.replace("...")
            val jsRegexes = listOf(
                Regex("""window\.location\.href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""window\.location\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""location\.href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""location\.replace\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE),
                Regex("""window\.location\.replace\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
            )
            for (regex in jsRegexes) {
                regex.find(html)?.groupValues?.get(1)?.let { return it.trim() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi phân tích URL chuyển hướng từ HTML", e)
        }
        return null
    }

    /**
     * Kiểm tra xem mạng hiện tại có bị chặn bởi Captive Portal hay không.
     * Trả về URL cổng chào chuyển hướng nếu có, ngược lại trả về null.
     */
    fun detectRedirectUrl(context: Context): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(CHECK_URL)
            connection = openConnection(context, url)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")

            val code = connection.responseCode
            Log.d(TAG, "Detection request returned response code: $code")
            if (code in 300..399) {
                val redirectUrl = connection.getHeaderField("Location")
                if (!redirectUrl.isNullOrBlank()) {
                    Log.d(TAG, "Detected Captive Portal redirecting to: $redirectUrl")
                    return redirectUrl
                }
            } else if (code == 200) {
                // Đọc HTML xem có phải trang chuyển hướng/login không (tránh một số router chặn trả về 200 OK kèm HTML)
                val contentType = connection.contentType ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    val html = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val extractedUrl = extractRedirectUrlFromHtml(html)
                    if (!extractedUrl.isNullOrBlank()) {
                        Log.d(TAG, "Detected Captive Portal redirect via HTML/JS to: $extractedUrl")
                        return extractedUrl
                    }
                    if (html.contains("<form", ignoreCase = true) || html.contains("<input", ignoreCase = true)) {
                        Log.d(TAG, "Detected Captive Portal form in HTTP 200 response, using CHECK_URL")
                        return CHECK_URL
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting captive portal", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    /**
     * Kiểm tra xem thiết bị đã thực sự có kết nối Internet hoàn chỉnh hay chưa.
     */
    fun hasInternetAccess(context: Context): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(CHECK_URL)
            connection = openConnection(context, url)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")

            val code = connection.responseCode
            return code == 204
        } catch (_: Exception) {
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Tiến hành vượt qua trang cổng chào bằng cách giả lập bấm nút xác thực ngầm.
     * Hỗ trợ cơ chế vòng lặp nhiều bước (Multi-step Bypass Loop) dành cho các trang chào phức tạp (như Three O'Clock).
     */
    fun attemptAutoBypass(context: Context, portalUrl: String): BypassResult {
        Log.d(TAG, "Starting captive portal auto-bypass loop for: $portalUrl")
        
        var currentUrl = portalUrl
        var html = fetchHtml(context, currentUrl)
        if (html.isBlank()) {
            return BypassResult(
                attempted = true,
                success = false,
                portalName = null,
                message = "Không thể tải nội dung trang xác thực."
            )
        }

        val portalName = detectPortalName(html, portalUrl)
        Log.d(TAG, "Identified captive portal name: $portalName")

        var step = 1
        val maxSteps = 4
        var lastResponseHtml = html
        
        while (step <= maxSteps) {
            Log.d(TAG, "Executing bypass step $step/max $maxSteps...")
            
            // 1. Kiểm tra Internet trước khi thực hiện bước tiếp theo để tránh lặp dư thừa
            if (hasInternetAccess(context)) {
                Log.d(TAG, "Internet access detected at step $step! Dismissing portal.")
                reportNetworkConnectivity(context, true)
                return BypassResult(
                    attempted = true,
                    success = true,
                    portalName = portalName,
                    message = "Đã tự động vượt qua trang chào WiFi '$portalName' thành công sau ${step - 1} bước!"
                )
            }

            // 2. Thử AI Local trước cho HTML hiện tại
            val aiPlan = WifiLocalAiEngine.analyzeCaptivePortal(lastResponseHtml, currentUrl)
            if (aiPlan != null) {
                Log.d(TAG, "Step $step (AI Local): targetUrl=${aiPlan.targetUrl}, method=${aiPlan.method}")
                val ok = if (aiPlan.method == "POST") {
                    executePostRequestWithHtmlResponse(context, aiPlan.targetUrl, aiPlan.params, currentUrl)
                } else {
                    executeGetRequestWithHtmlResponse(context, aiPlan.targetUrl, aiPlan.params)
                }
                
                if (ok != null && ok.first.isNotBlank()) {
                    lastResponseHtml = ok.first
                    currentUrl = ok.second
                    step++
                    Thread.sleep(1500)
                    continue
                }
            }

            // 3. Thử phân tích Form Heuristic thủ công
            val formBlock = parseFormBlock(lastResponseHtml)
            if (formBlock != null) {
                val action = formBlock.action.ifBlank { currentUrl }
                val targetUrl = resolveAbsoluteUrl(currentUrl, action)
                val method = formBlock.method.uppercase(Locale.getDefault())
                val params = formBlock.inputs.toMutableMap()

                Log.d(TAG, "Step $step (Heuristic Form): Target=$targetUrl | Method=$method")

                val submitButtonKey = formBlock.submitButtonName
                if (!submitButtonKey.isNullOrBlank()) {
                    params[submitButtonKey] = formBlock.submitButtonValue ?: "Submit"
                } else {
                    params["submit"] = "Connect"
                    params["connect"] = "true"
                    params["agree"] = "1"
                    params["accept"] = "1"
                }

                val ok = if (method == "POST") {
                    executePostRequestWithHtmlResponse(context, targetUrl, params, currentUrl)
                } else {
                    executeGetRequestWithHtmlResponse(context, targetUrl, params)
                }

                if (ok != null && ok.first.isNotBlank()) {
                    lastResponseHtml = ok.first
                    currentUrl = ok.second
                    step++
                    Thread.sleep(1500)
                    continue
                }
            }

            // 4. Thử tìm link trực tiếp (Direct Link) trong HTML hiện tại
            val directLink = findDirectConnectionLink(lastResponseHtml, currentUrl)
            if (directLink != null) {
                Log.d(TAG, "Step $step (Direct Link): Link=$directLink")
                val nextHtml = executeDirectGetWithHtmlResponse(context, directLink)
                if (!nextHtml.isNullOrBlank()) {
                    lastResponseHtml = nextHtml
                    currentUrl = directLink
                    step++
                    Thread.sleep(1500)
                    continue
                }
            }

            // Nếu không tìm thấy form, AI plan hay direct link nào nữa ở bước này
            Log.d(TAG, "Step $step: No executable action (Form, AI or Link) found in current HTML. Stopping loop.")
            break
        }

        // Kiểm tra Internet sau khi chạy xong vòng lặp
        Thread.sleep(2000)
        val finalInternetOk = hasInternetAccess(context)
        if (finalInternetOk) {
            reportNetworkConnectivity(context, true)
            return BypassResult(
                attempted = true,
                success = true,
                portalName = portalName,
                message = "Đã tự động vượt qua trang chào WiFi '$portalName' thành công nhờ cơ chế đa bước!"
            )
        }

        // Dự phòng cuối: Thử GET trực tiếp vào portalUrl gốc
        Log.d(TAG, "Final fallback: Direct GET to original portal URL: $portalUrl")
        executeDirectGet(context, portalUrl)
        Thread.sleep(1500)
        val fallbackOk = hasInternetAccess(context)
        if (fallbackOk) {
            reportNetworkConnectivity(context, true)
        }
        return BypassResult(
            attempted = true,
            success = fallbackOk,
            portalName = portalName,
            message = if (fallbackOk) "Đã vượt qua trang chào thành công ở lần thử dự phòng!" else "Đã chạy hết các bước xác thực nhưng chưa được cấp quyền Internet."
        )
    }

    private fun fetchHtml(context: Context, urlStr: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = openConnection(context, url)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val code = connection.responseCode
            if (code in 200..299) {
                return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HTML from portal", e)
        } finally {
            connection?.disconnect()
        }
        return ""
    }

    private fun detectPortalName(html: String, url: String): String {
        val lowerHtml = html.lowercase(Locale.getDefault())
        val lowerUrl = url.lowercase(Locale.getDefault())
        return when {
            lowerHtml.contains("circlek") || lowerUrl.contains("circlek") -> "Circle K VN"
            lowerHtml.contains("highlands") || lowerUrl.contains("highlands") -> "Highlands Coffee"
            lowerHtml.contains("starbucks") || lowerUrl.contains("starbucks") -> "Starbucks"
            lowerHtml.contains("gs25") || lowerUrl.contains("gs25") -> "GS25"
            lowerHtml.contains("passio") || lowerUrl.contains("passio") -> "Passio Coffee"
            lowerHtml.contains("three o'clock") || lowerHtml.contains("threeoclock") || lowerUrl.contains("threeoclock") -> "Three O'Clock"
            lowerHtml.contains("airport") || lowerUrl.contains("airport") -> "Sân bay công cộng"
            else -> {
                // Thử lấy thẻ <title> làm tên
                val titleRegex = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                val match = titleRegex.find(html)
                match?.groupValues?.get(1)?.trim() ?: "WiFi Miễn phí"
            }
        }
    }

    data class FormDetails(
        val action: String,
        val method: String,
        val inputs: Map<String, String>,
        val submitButtonName: String?,
        val submitButtonValue: String?
    )

    private fun parseFormBlock(html: String): FormDetails? {
        // Tìm thẻ form đầu tiên (chúng ta quan tâm tới form kết nối chính)
        val formTagRegex = Regex("""<form([^>]+)>""", RegexOption.IGNORE_CASE)
        val formMatch = formTagRegex.find(html) ?: return null

        val formAttributes = formMatch.groupValues[1]
        
        // Tìm Action (hỗ trợ có hoặc không có dấu nháy)
        val actionRegex = Regex("""action\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val actionMatch = actionRegex.find(formAttributes)
        val action = actionMatch?.groupValues?.get(1)?.ifBlank { null } ?: actionMatch?.groupValues?.get(2) ?: ""

        // Tìm Method (hỗ trợ có hoặc không có dấu nháy)
        val methodRegex = Regex("""method\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val methodMatch = methodRegex.find(formAttributes)
        val method = methodMatch?.groupValues?.get(1)?.ifBlank { null } ?: methodMatch?.groupValues?.get(2) ?: "POST"

        // Tìm toàn bộ thẻ form đóng để giới hạn phạm vi quét inputs
        val formStartIndex = formMatch.range.first
        val formEndIndex = html.indexOf("</form>", formStartIndex, ignoreCase = true)
        val formContent = if (formEndIndex != -1) {
            html.substring(formStartIndex, formEndIndex)
        } else {
            html.substring(formStartIndex) // Dự phòng nếu thiếu thẻ đóng
        }

        // Quét toàn bộ inputs
        val inputs = mutableMapOf<String, String>()
        val inputTagRegex = Regex("""<input\s+([^>]+)>""", RegexOption.IGNORE_CASE)
        
        var submitName: String? = null
        var submitVal: String? = null

        inputTagRegex.findAll(formContent).forEach { match ->
            val attrs = match.groupValues[1]
            val nameRegex = Regex("""name\s*=\s*(?:["']([^"']+)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
            val valueRegex = Regex("""value\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
            val typeRegex = Regex("""type\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)

            val nameMatch = nameRegex.find(attrs)
            val name = nameMatch?.groupValues?.get(1)?.ifBlank { null } ?: nameMatch?.groupValues?.get(2)

            val valueMatch = valueRegex.find(attrs)
            val value = valueMatch?.groupValues?.get(1)?.ifBlank { null } ?: valueMatch?.groupValues?.get(2) ?: ""

            val typeMatch = typeRegex.find(attrs)
            val type = (typeMatch?.groupValues?.get(1)?.ifBlank { null } ?: typeMatch?.groupValues?.get(2) ?: "text").lowercase(Locale.getDefault())

            if (!name.isNullOrBlank()) {
                if (type == "submit" || type == "button") {
                    submitName = name
                    submitVal = value
                } else {
                    inputs[name] = value
                }
            }
        }

        // Quét cả các thẻ <button type="submit">
        val buttonTagRegex = Regex("""<button\s+([^>]+)>([^<]*)</button>""", RegexOption.IGNORE_CASE)
        buttonTagRegex.findAll(formContent).forEach { match ->
            val attrs = match.groupValues[1]
            val nameRegex = Regex("""name\s*=\s*(?:["']([^"']+)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
            val typeRegex = Regex("""type\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)
            val valueRegex = Regex("""value\s*=\s*(?:["']([^"']*)["']|([^\s>]+))""", RegexOption.IGNORE_CASE)

            val nameMatch = nameRegex.find(attrs)
            val name = nameMatch?.groupValues?.get(1)?.ifBlank { null } ?: nameMatch?.groupValues?.get(2)

            val typeMatch = typeRegex.find(attrs)
            val type = (typeMatch?.groupValues?.get(1)?.ifBlank { null } ?: typeMatch?.groupValues?.get(2) ?: "submit").lowercase(Locale.getDefault())

            val valueMatch = valueRegex.find(attrs)
            val value = valueMatch?.groupValues?.get(1)?.ifBlank { null } ?: valueMatch?.groupValues?.get(2) ?: match.groupValues[2].trim()

            if (!name.isNullOrBlank() && type == "submit") {
                submitName = name
                submitVal = value
            }
        }

        return FormDetails(
            action = action,
            method = method,
            inputs = inputs,
            submitButtonName = submitName,
            submitButtonValue = submitVal
        )
    }

    private fun findDirectConnectionLink(html: String, baseUrl: String): String? {
        // Tìm các thẻ <a> có chứa từ khóa kết nối
        val anchorRegex = Regex("""<a\s+[^>]*href\s*=\s*(?:["']([^"']+)["']|([^\s>]+))[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        anchorRegex.findAll(html).forEach { match ->
            val href = match.groupValues.getOrNull(1)?.ifBlank { null } ?: match.groupValues.getOrNull(2) ?: ""
            val text = match.groupValues.getOrNull(3)?.lowercase(Locale.getDefault()) ?: ""
            if (text.contains("kết nối") || text.contains("connect") || text.contains("đồng ý") ||
                text.contains("truy cập") || text.contains("internet") || text.contains("free") || text.contains("agree")
            ) {
                if (!href.startsWith("javascript:", ignoreCase = true) && href.isNotBlank()) {
                    return resolveAbsoluteUrl(baseUrl, href)
                }
            }
        }
        return null
    }

    // --- CÁC HÀM HTTP PHẢN HỒI HTML (HỖ TRỢ BYPASS NHIỀU BƯỚC) ---

    private fun executePostRequestWithHtmlResponse(
        context: Context,
        targetUrl: String,
        params: Map<String, String>,
        referer: String
    ): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = openConnection(context, url)
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.useCaches = false
            
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val postData = buildQueryString(params)
            Log.d(TAG, "POST Request to $targetUrl body: $postData")
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val code = connection.responseCode
            Log.d(TAG, "POST Response code: $code")
            if (code in 200..399) {
                val location = connection.getHeaderField("Location")
                val actualUrl = if (!location.isNullOrBlank()) {
                    resolveAbsoluteUrl(targetUrl, location)
                } else {
                    connection.url.toString()
                }
                val htmlResponse = try {
                    connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
                return Pair(htmlResponse, actualUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi POST và đọc HTML phản hồi", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun executeGetRequestWithHtmlResponse(
        context: Context,
        targetUrl: String,
        params: Map<String, String>
    ): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        try {
            val queryString = buildQueryString(params)
            val fullUrl = if (targetUrl.contains("?")) {
                "$targetUrl&$queryString"
            } else {
                "$targetUrl?$queryString"
            }
            
            Log.d(TAG, "GET Request to $fullUrl")
            val url = URL(fullUrl)
            connection = openConnection(context, url)
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")

            val code = connection.responseCode
            Log.d(TAG, "GET Response code: $code")
            if (code in 200..399) {
                val location = connection.getHeaderField("Location")
                val actualUrl = if (!location.isNullOrBlank()) {
                    resolveAbsoluteUrl(fullUrl, location)
                } else {
                    connection.url.toString()
                }
                val htmlResponse = try {
                    connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
                return Pair(htmlResponse, actualUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi GET và đọc HTML phản hồi", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun executeDirectGetWithHtmlResponse(context: Context, urlStr: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = openConnection(context, url)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            val code = connection.responseCode
            if (code in 200..399) {
                return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }

    // --- CÁC HÀM TIỆN ÍCH DỰ PHÒNG CŨ (ĐỂ ĐẢM BẢO TƯƠNG THÍCH NGƯỢC) ---

    private fun executeDirectGet(context: Context, urlStr: String): Boolean {
        return executeDirectGetWithHtmlResponse(context, urlStr) != null
    }

    private fun executePostRequest(context: Context, targetUrl: String, params: Map<String, String>, referer: String): Boolean {
        return executePostRequestWithHtmlResponse(context, targetUrl, params, referer) != null
    }

    private fun executeGetRequest(context: Context, targetUrl: String, params: Map<String, String>): Boolean {
        return executeGetRequestWithHtmlResponse(context, targetUrl, params) != null
    }

    private fun buildQueryString(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            val encKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
            val encVal = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            "$encKey=$encVal"
        }
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
        
        // Đường dẫn tương đối không bắt đầu bằng gạch chéo
        val basePath = base.path
        val dir = if (basePath.contains("/")) {
            basePath.substring(0, basePath.lastIndexOf('/') + 1)
        } else {
            "/"
        }
        val portString = if (base.port != -1) ":" + base.port else ""
        return "${base.protocol}://${base.host}$portString$dir$relativeUrl"
    }
}
