## WiFi Auto-Switcher v1.2.0

### Cải tiến mới

- **Hiển thị Địa chỉ & Khoảng cách thực tế**: 
  - Tích hợp Geocoder để hiển thị địa chỉ thực tế (số nhà, tên đường, quận/huyện, thành phố) cho các điểm phát WiFi Cộng đồng.
  - Tự động đo khoảng cách địa lý (Haversine) từ vị trí thiết bị của bạn đến điểm phát WiFi (Hiển thị dạng: `📍 Cách X.XX km · [Địa chỉ thực tế]`).
  - Hỗ trợ sắp xếp/lọc danh sách WiFi cộng đồng theo khoảng cách gần nhất và lọc theo bán kính tùy chọn (ví dụ: tìm trong bán kính 2 km).
- **Cải tiến Trực quan & Trải nghiệm (UX)**:
  - Tách component địa chỉ dùng chung (`AddressText`) để tái sử dụng tối ưu trên cả hai màn hình Quét và Mật khẩu.
  - Loại bỏ vòng xoay spinner (`CircularProgressIndicator`) trùng lặp ở góc phải khi đang kết nối, chỉ giữ lại spinner và dòng thông báo tiến trình kết nối chi tiết ở phía dưới thông tin BSSID giúp giao diện trực quan và chuyên nghiệp hơn.
  - Giữ hiển thị nút sửa mật khẩu nhưng tạm vô hiệu hóa khi đang tiến hành kết nối.

### Sửa lỗi

- Khắc phục triệt để lỗi xung đột mã nguồn và mất ngoặc nhọn ở màn hình lưu mật khẩu.
- Đồng bộ hóa mượt mà các luồng dữ liệu vị trí GPS của người dùng khi ứng dụng khởi động.

### Cài đặt

Cài đặt APK release hoặc build trực tiếp từ mã nguồn. Cần quyền Root để kết nối im lặng (silent connection).
