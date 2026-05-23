## WiFi Auto-Switcher v1.1.0

### Cải tiến chính

- Phân biệt mật khẩu theo **SSID + BSSID** (hai router cùng tên khác MAC)
- Nút **Cập nhật mật khẩu (BSSID)** trên từng card WiFi (API `{bssid}` hoặc quét lại + khớp MAC)
- Tab Quét mượt hơn; sửa lỗi header có mạng nhưng list trống
- Bỏ loading toàn màn hình; giữ spinner trên từng nút/thao tác
- Từ chối mật khẩu API theo từng AP (BSSID), không chặn cả SSID

### Sửa lỗi

- Giám sát nền không bật khi chưa bật trong cài đặt
- Cooldown 60s giữa lần chuyển mạng tự động
- Thông báo nền hiện trên thanh trạng thái (kênh LOW)

### Cài đặt

Tải APK bên dưới. Cần quyền Root để kết nối im lặng.

### Lưu ý

- APK release chưa ký (unsigned) — phù hợp cài thử.
