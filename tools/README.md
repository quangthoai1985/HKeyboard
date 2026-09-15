# tools — Bộ kiểm chứng bộ gõ

Thư mục này chứa các công cụ dùng để **kiểm chứng** rằng `UnikeyEngine.java`
(trong `app/src/main/java/com/hkeyboard/vni/`) hoạt động **giống hệt** bộ máy
gõ của UniKey gốc. Các file ở đây **không** được đóng gói vào APK.

## Vì sao cần?

`UnikeyEngine.java` là bản port 1:1 từ mã nguồn C++ của UniKey 1.0.4
(`x-unikey-1.0.4/src/ukengine/`). Port lại mã C++ sang Java rất dễ sai ở các
chi tiết nhỏ (chỉ số bảng, quy tắc đặt dấu, xử lý backspace...). Cách duy nhất
để chắc chắn là **chạy cả hai bản trên cùng một tập phím và so từng kết quả**.

## Các file

| File | Vai trò |
|---|---|
| `ref/refmain.cpp` | Harness chạy bộ máy C++ gốc của UniKey |
| `ref/build.bat` | Biên dịch `ref/ref.exe` (cần Visual Studio Build Tools) |
| `ref/makewrap.ps1` | Sinh `ukengine_wrap.cpp` = bản sao `ukengine.cpp` có gắn hook wrapper |
| `ref/makeinputproc.ps1` | Sinh `inputproc_wrap.cpp` = bản sao `inputproc.cpp` có gắn hook wrapper |
| `ref/corpus_big.txt` | 68.495 chuỗi phím để kiểm tra |
| `ref/cases.txt` | Các ca tiêu biểu (dùng để tra kết quả bằng mắt) |
| `DiffDriver.java` | Harness chạy bộ máy Java, cùng định dạng đầu ra |
| `EngineTest.java` | Bộ test đọc được (75 ca), mọi giá trị mong đợi lấy từ UniKey gốc |
| `gen-corpus.ps1` | Sinh lại `ref/corpus_big.txt` |
| `diff-test.ps1` | Chạy cả hai bản trên corpus và báo cáo khác biệt |

## Kỹ thuật "wrapper"

Bộ máy gõ giữ trạng thái của **cả một từ**, nên đầu ra của nó có thể trùng với
ký tự người dùng vừa gõ (gõ `v` `i` thì engine trả về `"vi"`). Vì vậy không thể
phân biệt "ký tự do người dùng gõ" với "bản ghi do engine sinh ra".

Cách giải quyết: mỗi ký tự gõ được thay bằng `U+E000 + id` **trước khi** đưa vào
engine, và được ánh xạ ngược lại khi ghi đầu ra. Nhờ đó mọi ký tự trong đệm ra
đều là "hàng do engine sinh ra".

Giới hạn: `id` từ 1 đến 4095 (phải < 8192 vì `U+E000 + 8192` vượt khỏi phạm vi
`char` của Java).

## Hợp đồng của engine (giống đúng C++)

`process()` trả về `Result`:

* `out != null` — xoá `backspaces` ký tự trước con trỏ, rồi chèn `out`.
* `out == null && handled == true` — engine chỉ cập nhật trạng thái; **người gọi
  phải tự chèn ký tự gốc**. (Đây là lý do HKeyboardService phải chèn `rawChar`.)
* `handled == false` — phím không liên quan (Enter, Tab...) → để hệ thống xử lý.

`processBackspace()`:

* `consumed == true` — xoá `backspaces` ký tự rồi chèn `out`.
* `consumed == false` — engine không sinh nội dung → **hệ thống tự xoá một ký tự**.
  (Khi đó `backspaces` chỉ mô tả những gì engine đã bỏ khỏi bộ đệm của chính nó,
  người gọi **không** được xoá thêm.)

## Chạy kiểm chứng

```powershell
# 1. Biên dịch bộ máy C++ tham chiếu (một lần, cần Visual Studio Build Tools)
cd tools\ref
build.bat

# 2. Đối chiếu Java với C++ trên 68.495 chuỗi phím
cd ..\..
powershell -NoProfile -ExecutionPolicy Bypass -File tools\diff-test.ps1

# 3. Chạy bộ test đọc được
& "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" -encoding UTF-8 `
    -d tools\build app\src\main\java\com\hkeyboard\vni\UnikeyEngine.java tools\EngineTest.java
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -cp tools\build EngineTest
```

Kết quả mong đợi:

```
CPP: 68495 lines | JAVA: 68495 lines
=== NO DIFFERENCES (68495 lines) ===

TONG KET: 75 OK, 0 FAIL
```

> Lưu ý: các script `.ps1` phải là **ASCII thuần** — Windows PowerShell 5.1 đọc
> file không có BOM theo bảng mã ANSI, nên ký tự tiếng Việt trong script sẽ bị
> hỏng và gây lỗi cú pháp.

## Tuỳ chọn của UniKey

`CreateDefaultUnikeyOptions()` trong `unikey.cpp`:

```c
pOpt->freeMarking        = 1;   // cho phép đặt dấu ở bất kỳ nguyên âm nào
pOpt->modernStyle        = 0;   // "hòa"/"khỏe" (kiểu cũ)
pOpt->macroEnabled       = 0;
pOpt->spellCheckEnabled  = 1;   // kiểm tra chính tả tiếng Việt
pOpt->autoNonVnRestore   = 0;
```

`HKeyboardService` dùng đúng bộ mặc định này. Muốn xem hành vi khi đổi tuỳ chọn,
đặt biến môi trường trước khi chạy `ref.exe`:

* `UK_NOSPELL=1` — tắt kiểm tra chính tả (khi đó `d9u7o7ng` → `đường`,
  `ngu7o7i` → `người`, nhưng `java8` → `javă`).
* `UK_MODERN=1` — kiểu mới (`hóa` thay vì `hòa`).
* `UK_NOMARK=1` — tắt FreeMarking.
