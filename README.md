# HKeyboard VNI

Bàn phím tiếng Việt kiểu **VNI** cho Android, dành cho bàn phím rời (đã thử với
**Huawei Smart Keyboard**). Bộ gõ là bản port sang Java của **UniKey 1.0.4** —
đã được kiểm chứng là cho kết quả **giống hệt** bản gốc.

- Không hiện bàn phím ảo (view cao 0dp) → chỉ dùng bàn phím vật lý.
- Quy tắc gõ lấy trọn từ UniKey, không dùng quy tắc tự chế.
- Hiển thị số phiên bản và **tự phát hiện + cập nhật** bản mới từ GitHub Releases.

## Cài đặt

1. Tải `Hkeyboard.apk` ở mục [**Releases**](../../releases/latest).
2. Mở file để cài (cho phép "Cài đặt từ nguồn không xác định" nếu được hỏi).
3. Mở app **HKeyboard VNI** → bấm **Mở cài đặt bàn phím** → bật HKeyboard VNI.
4. Quay lại app → bấm **Chọn HKeyboard VNI** → chọn làm bàn phím mặc định.
5. Kết nối bàn phím rời và gõ.

App sẽ tự kiểm tra bản mới mỗi lần mở. Khi có bản mới, bấm **Cập nhật ngay** để
tải và cài trực tiếp.

## Quy tắc gõ

Dấu thanh:

| Phím | Dấu | Ví dụ |
|---|---|---|
| `1` | sắc | `a1` → á |
| `2` | huyền | `a2` → à |
| `3` | hỏi | `a3` → ả |
| `4` | ngã | `a4` → ã |
| `5` | nặng | `a5` → ạ |
| `0` | xoá dấu | `a10` → a |

Dấu chữ:

| Phím | Dấu | Ví dụ |
|---|---|---|
| `6` | mũ | `a6` → â, `e6` → ê, `o6` → ô |
| `7` | móc | `o7` → ơ, `u7` → ư |
| `8` | trăng | `a8` → ă |
| `9` | gạch ngang | `d9` → đ |

Ví dụ: `Vie65t Nam` → Việt Nam, `chu7o7ng tri2nh` → chương trình,
`d9a85c bie65t` → đặc biệt.

### Lưu ý về kiểm tra chính tả

App dùng **đúng bộ tuỳ chọn mặc định của UniKey**:
`freeMarking = 1`, `modernStyle = 0`, `spellCheckEnabled = 1`.

Vì bật kiểm tra chính tả, một số tổ hợp **không hợp lệ trong tiếng Việt** sẽ
không được thêm dấu — đây chính là hành vi của UniKey gốc, không phải lỗi:

| Gõ | Kết quả | Giải thích |
|---|---|---|
| `d9u7o7ng` | `đương` | âm tiết `ương` không hợp lệ → không thêm dấu huyền |
| `ngu7o7i` | `ngươi` | tương tự |
| `java8` | `java8` | không bị biến thành `javă` |
| `cac2` | `cac2` | `c` cuối không nhận dấu huyền |

Nếu muốn `d9u7o7ng` → `đường`, sửa `options.spellCheckEnabled = false` trong
`HKeyboardService`. Đánh đổi: khi đó `java8` sẽ thành `javă`.

## Phát triển

### Yêu cầu

- JDK 17 (JDK đi kèm Android Studio là đủ)
- Android SDK, compileSdk 34
- `JAVA_HOME` trỏ tới JDK

### Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

Kết quả: `app\build\outputs\apk\release\Hkeyboard.apk`

Ký release cần file `keystore.properties` ở gốc dự án (xem mẫu bên dưới).
**Không có file này thì Gradle tự rơi về chữ ký debug** và vẫn build được.

```properties
storeFile=keystore/hkeyboard-release.jks
storePassword=...
keyAlias=hkeyboard
keyPassword=...
```

> ⚠️ Mất file `.jks` hoặc mật khẩu = **không bao giờ cập nhật OTA được nữa**,
> người dùng phải gỡ app rồi cài lại. Hãy sao lưu cả hai.

### Chạy unit test

```powershell
.\gradlew.bat testReleaseUnitTest
```

### Phát hành phiên bản mới

```powershell
# Tăng versionCode +1 và versionName minor +1, rồi build, push, tạo release
powershell -NoProfile -ExecutionPolicy Bypass -File tools\release.ps1 -Bump

# Hoặc đặt số phiên bản cụ thể
powershell -NoProfile -ExecutionPolicy Bypass -File tools\release.ps1 -VersionName 2.2
```

Số hiệu phiên bản nằm ở [`version.properties`](version.properties) — nguồn duy
nhất, được Gradle đọc khi build và script tự sửa khi phát hành.

## Kiến trúc

```
app/src/main/java/com/hkeyboard/vni/
├── HKeyboardService.java   IME service, chuyển phím cho engine
├── UnikeyEngine.java       Bộ gõ — port 1:1 từ UniKey 1.0.4
├── KeyCharMap.java         Bảng keycode → ký tự (dự phòng)
├── UpdateChecker.java      Kiểm tra/tải cập nhật từ GitHub Releases
└── SettingsActivity.java   Màn hình thiết lập + nút cập nhật
```

### Hợp đồng engine ↔ service

`UnikeyEngine.process()` mô phỏng đúng `UkEngine::process` của C++:

| Kết quả | Ý nghĩa |
|---|---|
| `out != null` | xoá `backspaces` ký tự trước con trỏ rồi chèn `out` |
| `out == null && handled` | engine chỉ cập nhật trạng thái → **service phải tự chèn ký tự gốc** |
| `handled == false` | phím không liên quan (Enter, Tab…) → để hệ thống xử lý |

Dòng thứ hai là mấu chốt: UniKey trả về 0 cho **mọi ký tự thường** (chữ ASCII,
dấu cách, chữ số) và giao cho caller việc chèn ký tự. Nếu service quên bước này
thì chữ sẽ mất và trạng thái bị lệch.

## Kiểm chứng bộ gõ

Port lại mã C++ sang Java rất dễ sai ở chi tiết nhỏ, nên dự án có bộ kiểm chứng
**đối chiếu trực tiếp với mã C++ gốc**.

```powershell
# 1. Tai ma nguon UniKey (chi mot lan)
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-unikey.ps1

# 2. Bien dich bo may C++ tham chieu
cd tools\ref
build.bat

# 3. Doi chieu Java voi C++ tren 68.495 chuoi phim
cd ..\..
powershell -NoProfile -ExecutionPolicy Bypass -File tools\diff-test.ps1
```

Kết quả mong đợi:

```
CPP: 68495 lines | JAVA: 68495 lines
=== NO DIFFERENCES (68495 lines) ===
```

Chi tiết kỹ thuật (kỹ thuật "wrapper", cách chạy từng chế độ): xem
[`tools/README.md`](tools/README.md).

## Giấy phép

Phần **ứng dụng Android** của dự án này phát hành theo **GNU GPL v3**
(xem [`LICENSE`](LICENSE)).

`UnikeyEngine.java` là **tác phẩm phái sinh** từ UniKey 1.0.4 của Pham Kim Long
và các cộng tác viên, phát hành theo **GNU LGPL v2**. Vì vậy mã nguồn của bộ gõ
trong dự án này cũng thuộc LGPL v2. Mã nguồn UniKey gốc **không** được đưa vào
repo (xem [`NOTICE`](NOTICE)); dùng `tools/fetch-unikey.ps1` để tải về khi cần
chạy kiểm chứng.
