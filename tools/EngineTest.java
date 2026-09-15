import com.hkeyboard.vni.UnikeyEngine;

import java.nio.charset.StandardCharsets;

/**
 * Test harness cho UnikeyEngine — chạy trên desktop (không cần Android).
 *
 * Mô phỏng đúng cách HKeyboardService áp dụng kết quả vào ô nhập liệu:
 *   - out != null            -> xoá `backspaces` ký tự rồi chèn `out`
 *   - out == null && handled -> chèn ký tự gốc (dấu cách, dấu câu, chữ số...)
 *   - handled == false       -> để hệ thống xử lý (Enter, Tab...)
 *
 * MỌI giá trị "mong đợi" trong file này đều lấy từ chính bộ máy C++ của
 * UniKey 1.0.4 (tools/ref/ref.exe, tuỳ chọn mặc định: freeMarking=1,
 * modernStyle=0, spellCheckEnabled=1), chứ không phải do suy đoán.
 *
 * Chạy: xem tools/README.md
 */
public class EngineTest {

    private static final class Field {
        final StringBuilder sb = new StringBuilder();

        void apply(UnikeyEngine.Result r, char raw) {
            if (r.out == null) {
                if (r.handled) {
                    sb.append(raw);
                }
                return;
            }
            if (r.backspaces > 0) {
                int len = sb.length();
                sb.delete(Math.max(0, len - r.backspaces), len);
            }
            sb.append(r.out);
        }

        void backspace(UnikeyEngine.Result r) {
            if (r.consumed) {
                // Engine tự xử lý: xoá `backspaces` ký tự rồi chèn `out`.
                if (r.out == null) {
                    return;
                }
                if (r.backspaces > 0) {
                    int len = sb.length();
                    sb.delete(Math.max(0, len - r.backspaces), len);
                }
                sb.append(r.out);
            } else {
                // Engine trả về 0 -> để hệ thống tự xoá một ký tự.
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                }
            }
        }
    }

    private static int pass = 0;
    private static int fail = 0;

    /** '<' trong chuỗi phím = phím Backspace. */
    private static void check(String label, String keys, String expected) {
        UnikeyEngine engine = new UnikeyEngine();
        Field field = new Field();
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (c == '<') {
                UnikeyEngine.Result r = engine.processBackspace();
                trace.append(" [BS]");
                field.backspace(r);
                continue;
            }
            UnikeyEngine.Result r = engine.process(c);
            field.apply(r, c);
            if (trace.length() > 0) {
                trace.append(' ');
            }
            trace.append(category(r, c));
        }
        String actual = field.sb.toString();
        boolean ok = actual.equals(expected);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.println((ok ? "  OK   " : "  FAIL ") + label
                + " | go: \"" + keys + "\""
                + " | mong doi: \"" + expected + "\""
                + " | thuc te: \"" + actual + "\"");
        if (!ok) {
            System.out.println("         trace: " + trace);
        }
    }

    private static String category(UnikeyEngine.Result r, char c) {
        if (r.out != null) {
            return "[" + r.backspaces + "|\"" + r.out + "\"]";
        }
        return r.handled ? "[raw:" + c + "]" : "[pass]";
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));

        System.out.println("=== 1. Loi nguoi dung bao: go chu, cach ra khoang trang, roi go so ===");
        check("chu + cach + so", "a 1", "a 1");
        check("tu + cach + so", "viet 1", "viet 1");
        check("co dau + cach + so", "vie65t 1", "việt 1");
        check("cach + so", " 1", " 1");
        check("hai khoang trang + so", "a  1", "a  1");
        check("chu + cach + so 6", "a 6", "a 6");
        check("chu + cach + so 9", "a 9", "a 9");
        check("cau nhieu tu", "To6i 1", "Tôi 1");
        check("aaa + so", "HKeyboard", "HKeyboard");

        System.out.println();
        System.out.println("=== 2. Go VNI co ban (trong cung mot tu) ===");
        check("a1 sac", "a1", "á");
        check("a2 huyen", "a2", "à");
        check("a3 hoi", "a3", "ả");
        check("a4 nga", "a4", "ã");
        check("a5 nang", "a5", "ạ");
        check("a6 mu", "a6", "â");
        check("a8 trang", "a8", "ă");
        check("a61", "a61", "ấ");
        check("a81", "a81", "ắ");
        check("e6", "e6", "ê");
        check("o6", "o6", "ô");
        check("o7", "o7", "ơ");
        check("u7", "u7", "ư");
        check("d9", "d9", "đ");
        check("D9", "D9", "Đ");
        // Go lai phim dau lan hai: UniKey coi phim thu hai la ky tu thuong
        check("a1 roi 1", "a11", "a1");
        check("a6 roi 6", "a66", "a6");
        check("o7 roi 7", "o77", "o7");
        check("d9 roi 9", "d99", "d9");

        System.out.println();
        System.out.println("=== 3. Cac tu thuc te (theo dung UniKey) ===");
        check("Vie65t Nam", "Vie65t Nam", "Việt Nam");
        check("tie61ng", "tie61ng", "tiếng");
        check("thu73", "thu73", "thử");
        check("chao2", "chao2", "chào");
        check("kho3e", "kho3e", "khỏe");
        check("hoa2", "hoa2", "hòa");
        check("hoan2", "hoan2", "hoàn");
        check("nghie6ng", "nghie6ng", "nghiêng");
        check("chu7o7ng tri2nh", "chu7o7ng tri2nh", "chương trình");
        check("d9a85c bie65t", "d9a85c bie65t", "đặc biệt");
        check("ca3m o7n", "ca3m o7n", "cảm ơn");
        // Cac tu duoi day KHONG co dau: kiem tra chinh ta cua UniKey tu choi
        // vi am tiet khong hop le trong tieng Viet.
        check("d9u7o7ng (khong hop le)", "d9u7o7ng", "đương");
        check("ngu7o7i (khong hop le)", "ngu7o7i", "ngươi");
        check("Ho7p (khong hop le)", "Ho7p", "Hơp");
        check("quye63n (khong hop le)", "quye63n", "quyển");
        check("giu7a (khong hop le)", "giu7a", "giưa");
        check("thuye63n (khong hop le)", "thuye63n", "thuyển");
        check("du7o7ng2 (khong hop le)", "du7o7ng2", "dường");
        check("Nguye63n (khong hop le)", "Nguye63n", "Nguyển");

        System.out.println();
        System.out.println("=== 4. Tu khong phai tieng Viet ===");
        check("abc123", "abc123", "abc123");
        check("hello", "hello", "hello");
        check("java8", "java8", "java8");
        check("x2", "x2", "x2");
        check("a@b.com", "a@b.com", "a@b.com");
        check("0912345678", "0912345678", "0912345678");
        check("a7 (khong phai VNI)", "a7", "a7");
        check("a9 (khong phai VNI)", "a9", "a9");

        System.out.println();
        System.out.println("=== 5. Dau thanh cuoi c, ch, p, t ===");
        check("cac1", "cac1", "các");
        check("cac2 khong duoc", "cac2", "cac2");
        check("cac5", "cac5", "cạc");
        check("that1", "that1", "thát");
        check("that2 khong duoc", "that2", "that2");
        check("that3 khong duoc", "that3", "that3");

        System.out.println();
        System.out.println("=== 6. Backspace ===");
        // Sau khi xoa, engine luon quen tu => go '1' tiep thi '1' tro thanh
        // ky tu thuong (kiem tra chinh ta: am tiet 'a' da bi xoa khoi tu).
        check("a1 roi backspace", "a1<", "");
        check("a12 roi backspace", "a12<", "");
        check("d9 roi backspace", "d9<", "");
        check("a 1 roi backspace", "a 1<", "a ");
        check("vie65t roi backspace", "vie65t<", "việ");
        check("a1<1", "a1<1", "1");
        check("a1<1< (rong)", "a1<1<", "");

        System.out.println();
        System.out.println("=== 7. Chu hoa / Caps ===");
        check("A1", "A1", "Á");
        check("VIE65T", "VIE65T", "VIỆT");
        check("Ao1", "Ao1", "Áo");
        check("UY1", "UY1", "ÚY");
        check("uy1", "uy1", "úy");
        check("oa1", "oa1", "óa");
        check("oe1", "oe1", "óe");

        System.out.println();
        System.out.println("=================================================");
        System.out.println("TONG KET: " + pass + " OK, " + fail + " FAIL");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
