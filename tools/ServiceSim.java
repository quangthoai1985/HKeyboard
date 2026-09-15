import com.hkeyboard.vni.UnikeyEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Kiem tra "hop dong" engine <-> service: mo phong DUNG cach HKeyboardService
 * dua ket qua vao o nhap lieu, KHONG dung wrapper (tuc la go ky tu that).
 *
 * Day la kich ban that: nguoi dung go ky tu Unicode (vi du '@', '!', hoac chu
 * co dau san) chu khong phai ky tu da bi boc.
 */
public class ServiceSim {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            UnikeyEngine e = new UnikeyEngine();
            StringBuilder field = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == 8) {
                    UnikeyEngine.Result r = e.processBackspace();
                    if (r.consumed) {
                        if (r.out != null) {
                            if (r.backspaces > 0) {
                                field.setLength(Math.max(0, field.length() - r.backspaces));
                            }
                            field.append(r.out);
                        }
                    } else if (field.length() > 0) {
                        field.setLength(field.length() - 1);
                    }
                    continue;
                }
                UnikeyEngine.Result r = e.process(c);
                if (r.out != null) {
                    if (r.backspaces > 0) {
                        field.setLength(Math.max(0, field.length() - r.backspaces));
                    }
                    field.append(r.out);
                } else if (r.handled) {
                    // HKeyboardService applyResult(): chen rawChar khi out == null
                    field.append(c);
                } else {
                    field.append("<?>");   // de he thong xu ly
                }
            }
            StringBuilder caps = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                int ch = line.charAt(i);
                if (ch < 0x20) {
                    caps.append(String.format("U+%04X ", ch));
                } else {
                    caps.append(String.format("U+%04X ", ch));
                }
            }
            System.out.println(caps.toString().trim() + "\t" + field);
        }
    }
}
