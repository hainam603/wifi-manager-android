# 🛠️ Android Projects Build Guide

> **Hướng dẫn dành cho AI assistants và developers** để build, cài đặt và debug các Android project trong workspace này.

---

## 📁 Workspace Structure

```
N:\project ad\
├── vcam\                    # Virtual Camera - Xposed Module
├── LocationSpoofer\         # GPS Spoofer - Xposed Module
└── BUILD_GUIDE.md           # (file này)
```

---

## 🔧 Môi Trường Phát Triển

### Yêu cầu hệ thống

| Component       | Version / Path                                      |
|-----------------|-----------------------------------------------------|
| **OS**          | Windows                                             |
| **JDK**         | JDK 17 — `C:\Program Files\Java\jdk-17`             |
| **Android SDK** | `C:\Users\Admin\AppData\Local\Android\Sdk`           |
| **ADB**         | Có sẵn trong PATH (từ Android SDK `platform-tools`)  |
| **Gradle**      | Sử dụng Gradle Wrapper (`gradlew.bat`) trong mỗi project |

### Thiết bị test

| Property       | Value                        |
|----------------|------------------------------|
| **Serial**     | `8b26492b`                   |
| **Model**      | Xiaomi 23049RAD8C (marble)   |
| **Android**    | 15 (API 35)                  |
| **Root**       | Magisk                       |
| **Xposed**     | LSPosed                      |

### Biến môi trường (đã cấu hình)

```
JAVA_HOME       = C:\Program Files\Java\jdk-17
ANDROID_HOME    = C:\Users\Admin\AppData\Local\Android\Sdk
ANDROID_SDK_ROOT = C:\Users\Admin\AppData\Local\Android\Sdk
```

> [!IMPORTANT]
> Mỗi project đã có `gradle.properties` với `org.gradle.java.home` trỏ đến JDK 17.
> Điều này đảm bảo Gradle luôn dùng đúng JDK bất kể biến môi trường `JAVA_HOME` hệ thống.

---

## 📦 Project: VCAM (Virtual Camera)

### Thông tin

| Property          | Value                                  |
|-------------------|----------------------------------------|
| **Path**          | `N:\project ad\vcam`                   |
| **Package**       | `com.example.vcam`                     |
| **Language**      | Java                                   |
| **Build System**  | Gradle (Groovy DSL)                    |
| **AGP**           | 7.1.0                                  |
| **Gradle**        | 7.2                                    |
| **Min SDK**       | 28                                     |
| **Target SDK**    | 30                                     |
| **Type**          | Xposed Module (LSPosed compatible)     |

### Cách build

```powershell
cd "N:\project ad\vcam"
.\gradlew.bat assembleDebug
```

### Cách cài đặt

```powershell
adb -s 8b26492b install -r "N:\project ad\vcam\app\build\outputs\apk\debug\app-debug.apk"
```

### Cách mở app

```powershell
adb -s 8b26492b shell am start -n com.example.vcam/.MainActivity
```

### Kiểm tra crash

```powershell
# Xem log realtime
adb -s 8b26492b logcat -s VCAM:D AndroidRuntime:E

# Kiểm tra process còn sống không
adb -s 8b26492b shell pidof com.example.vcam
```

### Cấu trúc source chính

```
app/src/main/java/com/example/vcam/
├── MainActivity.java        # UI chính, chọn ảnh, preview
├── HookMain.java            # Xposed hooks (Camera2 API), floating toggle
└── ImageToSurface.java      # Render engine, vẽ ảnh lên Surface
```

### Lưu ý quan trọng

> [!WARNING]
> `ImageToSurface.java` được dùng bởi CẢ `MainActivity` (app chính) VÀ `HookMain` (Xposed context).
> - Trong app chính: **KHÔNG CÓ** class `XposedBridge` → gọi trực tiếp sẽ crash `NoClassDefFoundError`
> - Phải dùng `safeLog()` wrapper với `try-catch(NoClassDefFoundError)` thay vì `XposedBridge.log()`

> [!NOTE]
> Các file cấu hình runtime được lưu tại `/data/local/tmp/`:
> - `virtual.jpg` — Ảnh tĩnh được inject vào camera
> - `vcam_config.txt` — Cấu hình scale, offset, rotation, shake, noise, timestamp
> - `vcam_image_name.txt` — Tên file ảnh gốc
> - `vcam_history.txt` — Lịch sử cấu hình theo ảnh

---

## 📦 Project: LocationSpoofer

### Thông tin

| Property          | Value                                  |
|-------------------|----------------------------------------|
| **Path**          | `N:\project ad\LocationSpoofer`        |
| **Package**       | `com.example.locationspoofer`          |
| **Language**      | Kotlin                                 |
| **Build System**  | Gradle (Kotlin DSL `.kts`)             |
| **AGP**           | 7.4.2                                  |
| **Gradle**        | 8.0                                    |
| **Min SDK**       | 28                                     |
| **Target SDK**    | 34                                     |
| **Type**          | Xposed Module (LSPosed compatible)     |

### Cách build

```powershell
cd "N:\project ad\LocationSpoofer"
.\gradlew.bat assembleDebug
```

### Cách cài đặt

```powershell
adb -s 8b26492b install -r "N:\project ad\LocationSpoofer\app\build\outputs\apk\debug\app-debug.apk"
```

### Cách mở app

```powershell
adb -s 8b26492b shell am start -n com.example.locationspoofer/.MainActivity
```

### Dependencies đáng chú ý

- `org.osmdroid:osmdroid-android:6.1.18` — Bản đồ OpenStreetMap
- `de.robv.android.xposed:api:82` — Xposed API (`compileOnly`)

---

## ⚡ Quick Commands

### Build tất cả projects

```powershell
# VCAM
cd "N:\project ad\vcam"; .\gradlew.bat assembleDebug

# LocationSpoofer
cd "N:\project ad\LocationSpoofer"; .\gradlew.bat assembleDebug
```

### Cài đặt tất cả

```powershell
adb -s 8b26492b install -r "N:\project ad\vcam\app\build\outputs\apk\debug\app-debug.apk"
adb -s 8b26492b install -r "N:\project ad\LocationSpoofer\app\build\outputs\apk\debug\app-debug.apk"
```

### Debug commands

```powershell
# Xem tất cả log từ cả 2 app
adb -s 8b26492b logcat -s VCAM:D LocationSpoofer:D AndroidRuntime:E

# Force stop app
adb -s 8b26492b shell am force-stop com.example.vcam
adb -s 8b26492b shell am force-stop com.example.locationspoofer

# Kiểm tra Xposed module có active
adb -s 8b26492b shell ls /data/local/tmp/vcam_config.txt

# Screenshot
adb -s 8b26492b exec-out screencap -p > screenshot.png
```

---

## 🚨 Troubleshooting

### Build fails: "Java 8 incompatible"
**Nguyên nhân**: `JAVA_HOME` trỏ JDK 8 thay vì JDK 17.
**Fix**: Đảm bảo `gradle.properties` có dòng:
```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

### Build fails: "SDK location not found"
**Nguyên nhân**: Thiếu `local.properties`.
**Fix**: Tạo file `local.properties` trong root project:
```properties
sdk.dir=C\:\\Users\\Admin\\AppData\\Local\\Android\\Sdk
```

### App crash: NoClassDefFoundError XposedBridge
**Nguyên nhân**: Code dùng chung giữa app chính và Xposed hook gọi trực tiếp `XposedBridge.log()`.
**Fix**: Dùng wrapper:
```java
private static void safeLog(String msg) {
    try {
        de.robv.android.xposed.XposedBridge.log("【TAG】" + msg);
    } catch (NoClassDefFoundError e) {
        android.util.Log.d("TAG", msg);
    }
}
```

### ADB: device not found
**Fix**: Kiểm tra kết nối USB và serial:
```powershell
adb devices -l
# Nếu serial thay đổi, cập nhật trong các lệnh adb -s <new_serial>
```

### Gradle daemon chiếm RAM
```powershell
.\gradlew.bat --stop
```

---

## 🎨 Icon Design System

Cả 2 app dùng chung phong cách icon **"Grid Premium"**:
- **Background**: Nền tối + lưới grid trắng mờ
- **Foreground**: Biểu tượng trắng trên nền tối

| App               | Màu nền         | Biểu tượng            |
|-------------------|------------------|-----------------------|
| **VCAM**          | Teal `#004D40`   | Camera + lens         |
| **LocationSpoofer**| Indigo `#1A237E` | Location pin + crosshair |

File icon nằm tại:
```
app/src/main/res/drawable/ic_launcher_background.xml    # Nền grid
app/src/main/res/drawable/ic_launcher_foreground.xml     # Biểu tượng
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml      # Adaptive icon config
```
