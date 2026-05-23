## WiFi Auto-Switcher v1.1.1

### Cải tiến

- Tab Quét mượt hơn: chỉ quét khi bấm FAB, không tự quét khi chuyển tab
- Hiển thị danh sách ngay bằng `buildQuick` trong khi build display đầy đủ
- Cache mật khẩu và build scanner trên luồng IO, giảm lag UI

### Sửa lỗi

- Không còn màn hình trống khi header báo “Tìm thấy N mạng”
- Sửa crash `ConcurrentModificationException` khi đọc cache mật khẩu
- Placeholder “Đang quét” / “Đang chuẩn bị danh sách” thay vùng nội dung trống

### Cài đặt

Tải APK bên dưới. Cần quyền Root để kết nối im lặng.

### Lưu ý

- APK release chưa ký (unsigned) — phù hợp cài thử.
