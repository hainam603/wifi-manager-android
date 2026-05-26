package com.antigravity.wifimanager.data

import android.content.Context
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

    /**
     * Kiểm tra xem mạng hiện tại có bị chặn bởi Captive Portal hay không.
     * Trả về URL cổng chào chuyển hướng nếu có, ngược lại trả về null.
     */
    fun detectRedirectUrl(): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(CHECK_URL)
            connection = url.openConnection() as HttpURLConnection
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
    fun hasInternetAccess(): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(CHECK_URL)
            connection = url.openConnection() as HttpURLConnection
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
     */
    fun attemptAutoBypass(portalUrl: String): BypassResult {
        Log.d(TAG, "Starting captive portal auto-bypass for: $portalUrl")
        
        // 1. Tải HTML của trang cổng chào
        val html = fetchHtml(portalUrl)
        if (html.isBlank()) {
            return BypassResult(
                attempted = true,
                success = false,
                portalName = null,
                message = "Không thể tải nội dung trang xác thực."
            )
        }

        // Nhận diện tên dịch vụ dựa trên mã nguồn (ví dụ: Circle K, Highlands, Starbucks...)
        val portalName = detectPortalName(html, portalUrl)
        Log.d(TAG, "Identified captive portal name: $portalName")

        // 2. Tìm kiếm và phân tích các thẻ Form
        val formBlock = parseFormBlock(html)
        if (formBlock == null) {
            // Không thấy form rõ ràng, thử tìm link bấm trực tiếp
            val directLink = findDirectConnectionLink(html, portalUrl)
            if (directLink != null) {
                Log.d(TAG, "No form found, but found direct connection link: $directLink. Executing GET request...")
                executeDirectGet(directLink)
                val internetOk = hasInternetAccess()
                return BypassResult(
                    attempted = true,
                    success = internetOk,
                    portalName = portalName,
                    message = if (internetOk) "Tự động vượt qua qua liên kết một chạm thành công!" else "Đã gửi yêu cầu kết nối nhưng chưa có Internet."
                )
            }

            return BypassResult(
                attempted = true,
                success = false,
                portalName = portalName,
                message = "Không tìm thấy biểu mẫu xác thực (Form) hoặc nút kết nối một chạm."
            )
        }

        // 3. Phân tích các tham số trong form (bao gồm các thẻ input, hidden, và nút Submit)
        val action = formBlock.action.ifBlank { portalUrl }
        val targetUrl = resolveAbsoluteUrl(portalUrl, action)
        val method = formBlock.method.uppercase(Locale.getDefault())
        val params = formBlock.inputs.toMutableMap()

        Log.d(TAG, "Found Form Target: $targetUrl | Method: $method | Params count: ${params.size}")

        // 4. Giả lập bấm nút submit (thêm tham số submit nếu có nút bấm kết nối)
        val submitButtonKey = formBlock.submitButtonName
        if (!submitButtonKey.isNullOrBlank()) {
            params[submitButtonKey] = formBlock.submitButtonValue ?: "Submit"
        } else {
            // Thêm các tham số submit giả lập phổ biến để đảm bảo Router chấp nhận
            params["submit"] = "Connect"
            params["connect"] = "true"
            params["agree"] = "1"
            params["accept"] = "1"
        }

        // 5. Gửi yêu cầu giả lập (POST hoặc GET) lên Router của WiFi
        if (method == "POST") {
            executePostRequest(targetUrl, params, portalUrl)
        } else {
            executeGetRequest(targetUrl, params)
        }

        // 6. Xác thực xem Internet đã mở hay chưa
        Log.d(TAG, "Simulated submit finished. Verifying internet connectivity...")
        
        // Đợi một khoảng ngắn cho router xử lý cấp quyền
        Thread.sleep(2000)
        
        val internetOk = hasInternetAccess()
        return if (internetOk) {
            BypassResult(
                attempted = true,
                success = true,
                portalName = portalName,
                message = "Đã tự động vượt qua trang chào WiFi '$portalName' thành công!"
            )
        } else {
            // Thử một lần cuối với GET trực tiếp vào trang gốc (một số cổng chào chỉ cần click link gốc)
            executeDirectGet(portalUrl)
            Thread.sleep(1500)
            val secondCheck = hasInternetAccess()
            BypassResult(
                attempted = true,
                success = secondCheck,
                portalName = portalName,
                message = if (secondCheck) "Đã vượt qua trang chào thành công ở lần thử dự phòng!" else "Đã thử gửi form nhưng chưa có quyền truy cập Internet."
            )
        }
    }

    private fun fetchHtml(urlStr: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
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
        
        // Tìm Action
        val actionRegex = Regex("""action\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val action = actionRegex.find(formAttributes)?.groupValues?.get(1) ?: ""

        // Tìm Method
        val methodRegex = Regex("""method\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val method = methodRegex.find(formAttributes)?.groupValues?.get(1) ?: "POST"

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
            val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val valueRegex = Regex("""value\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            val typeRegex = Regex("""type\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

            val name = nameRegex.find(attrs)?.groupValues?.get(1)
            val value = valueRegex.find(attrs)?.groupValues?.get(2) ?: ""
            val type = typeRegex.find(attrs)?.groupValues?.get(1)?.lowercase(Locale.getDefault()) ?: "text"

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
            val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val typeRegex = Regex("""type\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            val valueRegex = Regex("""value\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

            val name = nameRegex.find(attrs)?.groupValues?.get(1)
            val type = typeRegex.find(attrs)?.groupValues?.get(1)?.lowercase(Locale.getDefault()) ?: "submit"
            val value = valueRegex.find(attrs)?.groupValues?.get(1) ?: match.groupValues[2].trim()

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
        val anchorRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        anchorRegex.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            val text = match.groupValues[2].lowercase(Locale.getDefault())
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

    private fun executeDirectGet(urlStr: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            val code = connection.responseCode
            return code in 200..399
        } catch (_: Exception) {
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun executePostRequest(targetUrl: String, params: Map<String, String>, referer: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.useCaches = false
            
            // Thiết lập Headers chuẩn trình duyệt điện thoại để vượt tường lửa Router
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            // Ghi dữ liệu POST body
            val postData = buildQueryString(params)
            Log.d(TAG, "Executing POST to $targetUrl with body: $postData")
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val code = connection.responseCode
            Log.d(TAG, "POST Request returned response code: $code")
            return code in 200..399
        } catch (e: Exception) {
            Log.e(TAG, "Error executing HTTP POST to bypass portal", e)
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeGetRequest(targetUrl: String, params: Map<String, String>): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val queryString = buildQueryString(params)
            val fullUrl = if (targetUrl.contains("?")) {
                "$targetUrl&$queryString"
            } else {
                "$targetUrl?$queryString"
            }
            
            Log.d(TAG, "Executing GET to $fullUrl")
            val url = URL(fullUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")

            val code = connection.responseCode
            Log.d(TAG, "GET Request returned response code: $code")
            return code in 200..399
        } catch (e: Exception) {
            Log.e(TAG, "Error executing HTTP GET to bypass portal", e)
            return false
        } finally {
            connection?.disconnect()
        }
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
