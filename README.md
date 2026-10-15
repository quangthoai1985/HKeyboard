# HKeyboard

Bàn phím tiếng Việt kiểu **Telex** và **VNI** cho Android, dành cho bàn phím rời
(đã thử với **Huawei Smart Keyboard**). Bộ gõ là bản port sang Java của
**UniKey 1.0.4** — đã được kiểm chứng là cho kết quả **giống hệt** bản gốc ở cả
hai kiểu gõ.

- Không hiện bàn phím ảo (view cao 0dp) → chỉ dùng bàn phím vật lý.
- Quy tắc gõ lấy trọn từ UniKey, không dùng quy tắc tự chế.
- Chọn kiểu gõ **Telex / VNI** ngay trong app, có hiệu lực tức thì.
- Hiển thị số phiên bản và **tự phát hiện + cập nhật** bản mới từ GitHub Releases.

## Cài đặt

1. Tải `Hkeyboard.apk` ở mục [**Releases**](../../releases/latest).
2. Mở file để cài (cho phép "Cài đặt từ nguồn không xác định" nếu được hỏi).
3. Mở app **HKeyboard** → bấm **Mở cài đặt bàn phím** → bật HKeyboard.
4. Quay lại app → bấm **Chọn HKeyboard** → chọn làm bàn phím mặc định.
5. Chọn **Kiểu gõ**: Telex hoặc VNI (mặc định VNI).
6. Kết nối bàn phím rời và gõ.

App sẽ tự kiểm tra bản mới mỗi lần mở. Khi có bản mới, bấm **Cập nhật ngay** để
tải và cài trực tiếp.

## Quy tắc gõ — Telex

Theo đúng `TelexMethodMapping` của UniKey.

Dấu thanh:

| Phím | Dấu | Ví dụ |
|---|---|---|
| `s` | sắc | `as` → á |
| `f` | huyền | `af` → à |
| `r` | hỏi | `ar` → ả |
| `x` | ngã | `ax` → ã |
| `j` | nặng | `aj` → ạ |
| `z` | xoá dấu | `asz` → a |

Dấu chữ:

| Phím | Kết quả | Ghi chú |
|---|---|---|
| `aa` `ee` `oo` | â ê ô | |
| `aw` `ow` `uw` | ă ơ ư | `uow` → ươ |
| `dd` | đ | |
| `w` | ư | khi đứng riêng: `w` → ư, `tw` → tư |
| `[` `]` | ơ ư | `{` `}` → Ơ Ư |

Gõ phím dấu hai lần để huỷ: `ass` → as, `aaa` → aa, `ww` → w, `[[` → [.

Ví dụ: `Vieetj Nam` → Việt Nam, `dduwowngf` → đường, `chuwowng trinhf` →
chương trình, `nuocws` → nước (dấu gõ sau cùng cũng được).

> Lưu ý (hành vi gốc của UniKey): từ tiếng Anh bắt đầu bằng `w` sẽ thành `ư`
> (`window` → ưindow). Gõ `ww` để ra chữ `w`.

## Quy tắc gõ — VNI

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

## Lưu ý về kiểm tra chính tả

App dùng **đúng bộ tuỳ chọn mặc định của UniKey**:
`freeMarking = 1`, `modernStyle = 0`, `spellCheckEnabled = 1`.

Vì bật kiểm tra chính tả, một số tổ hợp **không hợp lệ trong tiếng Việt** sẽ
không được thêm dấu — đây chính là hành vi của UniKey gốc, không phải lỗi
(kết quả lấy từ chính bộ máy C++ của UniKey):

| Kiểu gõ | Gõ | Kết quả | Nếu tắt kiểm tra chính tả |
|---|---|---|---|
| VNI | `java8` | `java8` | `javă` |
| VNI | `hello1` | `hello1` | `helló` |
| VNI | `cac2` | `cac2` (`c` cuối không nhận dấu huyền) | `cac2` |
| Telex | `class` | `class` | `clas` |
| Telex | `javaw` | `javaw` | `javă` |
| Telex | `cacf` | `cacf` | `cacf` |

Muốn tắt, sửa `options.spellCheckEnabled = false` trong `HKeyboardService`.

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
├── UnikeyEngine.java       Bộ gõ Telex + VNI — port 1:1 từ UniKey 1.0.4
├── ImePrefs.java           Lưu kiểu gõ đang chọn (dùng chung app ↔ service)
├── KeyCharMap.java         Bảng keycode → ký tự (dự phòng)
├── UpdateChecker.java      Kiểm tra/tải cập nhật từ GitHub Releases
└── SettingsActivity.java   Màn hình thiết lập: kiểu gõ, nút cập nhật
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
**đối chiếu trực tiếp với mã C++ gốc**: VNI trên 68.495 chuỗi phím, Telex trên
397.156 chuỗi phím.

Trên Linux/macOS (cần g++, python3, JDK):

```bash
tools/diff-test.sh          # VNI + Telex
tools/diff-test.sh --all    # thêm các chế độ NOSPELL / MODERN / NOMARK / CapsLock
```

Trên Windows:

```powershell
# 1. Tai ma nguon UniKey (chi mot lan)
powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-unikey.ps1

# 2. Bien dich bo may C++ tham chieu
cd tools\ref
build.bat

# 3. Doi chieu Java voi C++
cd ..\..
powershell -NoProfile -ExecutionPolicy Bypass -File tools\diff-test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\diff-test.ps1 -Telex
```

Kết quả mong đợi: không có khác biệt nào.

Chi tiết kỹ thuật (kỹ thuật "wrapper", cách chạy từng chế độ): xem
[`tools/README.md`](tools/README.md).

## Giấy phép

Phần **ứng dụng Android** của dự án này phát hành theo **GNU GPL v3**
(xem [`LICENSE`](LICENSE)).

`UnikeyEngine.java` là **tác phẩm phái sinh** từ UniKey 1.0.4 của Pham Kim Long
và các cộng tác viên, phát hành theo **GNU LGPL v2**. Vì vậy mã nguồn của bộ gõ
trong dự án này cũng thuộc LGPL v2. Mã nguồn UniKey gốc **không** được đưa vào
repo (xem [`NOTICE`](NOTICE)); dùng `tools/fetch-unikey.ps1` (hoặc
`tools/diff-test.sh`) để tải về khi cần chạy kiểm chứng.
