package com.antigravity.wifimanager.data

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiAiManager {

    suspend fun testConnection(apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "API Key không được để trống."
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
            val response = model.generateContent("Respond with exactly 'OK'.")
            if (response.text.isNullOrBlank()) {
                "Không nhận được phản hồi từ mô hình Gemini."
            } else {
                null // Thành công
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.localizedMessage ?: e.message ?: "Lỗi không xác định"
            when {
                msg.contains("Unable to resolve host", ignoreCase = true) || 
                msg.contains("ConnectException", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) -> 
                    "Không có kết nối Internet: Thiết bị đang ngoại tuyến hoặc mạng của bạn bị chặn máy chủ Google."
                msg.contains("API key not valid", ignoreCase = true) || 
                msg.contains("API_KEY_INVALID", ignoreCase = true) -> 
                    "API Key không hợp lệ. Hãy tạo lại hoặc sao chép chính xác từ Google AI Studio."
                else -> "Lỗi kết nối: $msg"
            }
        }
    }

    suspend fun diagnoseSwitchFailure(
        apiKey: String,
        log: SwitchLog,
        hasRoot: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Chưa cấu hình Gemini API Key. Vui lòng vào tab Cấu hình để nhập API Key miễn phí."
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
            
            val rootText = if (hasRoot) "Đã được cấp quyền Root" else "Chưa có quyền Root (đang kết nối thông thường)"
            val statusLabel = if (log.isSuccess) "THÀNH CÔNG" else "THẤT BẠI"
            val detailLabel = if (log.isSuccess) "Kết quả: ${log.connectionStatus ?: "Chuyển mạng hoàn tất"}" else "Lý do thất bại: ${log.failureReason ?: "Không rõ"}"
            val section1Title = if (log.isSuccess) "🔍 **Phân tích kỹ thuật**" else "🔍 **Nguyên nhân chính**"
            val section2Title = if (log.isSuccess) "💡 **Đánh giá hiệu năng**" else "💡 **Khuyến nghị hành động**"

            val prompt = """
                Bạn là Trợ lý AI chuyên gia mạng Android cao cấp của ứng dụng "WiFi Auto-Switcher".
                Hãy phân tích kỹ thuật và chẩn đoán nhật ký chuyển đổi WiFi bị $statusLabel sau đây:
                
                - Thời gian: ${log.timestamp}
                - Mạng nguồn (Đang dùng): "${log.fromSsid}" (Cường độ sóng: ${log.fromSignal}%)
                - Mạng đích (Muốn chuyển): "${log.toSsid}" (Cường độ sóng: ${log.toSignal}%)
                - Trạng thái thiết bị: $rootText
                - $detailLabel
                
                Hãy cung cấp phản hồi bằng tiếng Việt ngắn gọn, súc tích và chia làm 3 phần rõ ràng:
                1. $section1Title: Phân tích kỹ thuật (khoảng cách sóng, nâng cấp lên 5Ghz, sóng cải thiện thế nào, hoặc giải thích nguyên nhân thất bại).
                2. $section2Title: Đánh giá chất lượng đường truyền (tín hiệu tăng bao nhiêu %, băng tần tối ưu chưa) hoặc các bước hành động cụ thể để khắc phục lỗi.
                3. ⚡ **Lời khuyên tối ưu**: Lời khuyên để cấu hình WiFi Auto-Switcher chạy tốt hơn trong trường hợp này.
                
                ĐẶC BIỆT QUAN TRỌNG: Bạn PHẢI thêm chính xác một dòng mã hành động ẩn ở cuối cùng của câu trả lời (ở dòng riêng biệt cuối cùng, không có ký tự nào khác) dựa trên chẩn đoán của bạn để ứng dụng kích hoạt chức năng "Sửa lỗi 1 chạm":
                - Nếu WiFi này yêu cầu đăng nhập trang chào (Captive Portal, cổng chào WiFi công cộng): 
                  Ghi: [ACTION: BYPASS_CAPTIVE|${log.toSsid}]
                - Nếu kết nối thất bại do Sai mật khẩu / Cần mật khẩu / Không có mật khẩu offline phù hợp: 
                  Ghi: [ACTION: TRY_COMMON_PASSWORDS|${log.toSsid}]
                - Nếu kết nối thành công nhưng WiFi này không truy cập được Internet (No Internet, không có mạng): 
                  Ghi: [ACTION: SWITCH_TO_BETTER_WIFI]
                - Nếu không thuộc các trường hợp trên: 
                  Ghi: [ACTION: NONE]

                Định dạng đẹp mắt với các ký tự emoji, định dạng Markdown (đặc biệt là in đậm và danh sách), không giải thích dông dài.
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            response.text ?: "Không nhận được phản hồi từ AI. Hãy thử lại."
        } catch (e: Exception) {
            e.printStackTrace()
            "Lỗi kết nối Gemini AI: ${e.localizedMessage ?: "Không xác định"}. Vui lòng kiểm tra lại API Key hoặc kết nối mạng."
        }
    }
}
