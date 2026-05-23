# WiFi Auto-Switcher (Android)

Ứng dụng Android quản lý và tự chuyển WiFi: quét mạng lân cận, mật khẩu cộng đồng/API, kết nối qua Root, giám sát sóng nền.

## Yêu cầu

- Android 8.0+ (API 26)
- Quyền vị trí, WiFi lân cận, thông báo (Android 13+)
- Root (để kết nối/chuyển mạng im lặng)

## Tính năng chính

- Quét WiFi và ghép mật khẩu từ API / WifiMaster / cache offline
- Tự chuyển mạng khi sóng yếu hoặc ưu tiên 5 GHz
- Giám sát nền với thông báo trên thanh trạng thái
- Đánh dấu mật khẩu API không hợp lệ sau khi kết nối thất bại

## Build

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`

## Cài đặt

Tải file APK từ [Releases](https://github.com/hainam603/wifi-manager-android/releases) và cài trên thiết bị (bật “Nguồn không xác định” nếu cần).
