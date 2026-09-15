import com.hkeyboard.vni.UnikeyEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Bo so sanh vi sai (phia Java).
 *
 * KY THUAT WRAPPER: giong tools/ref/refmain.cpp — moi ky tu go duoc thay bang
 * U+E000+id truoc khi dua vao engine, va doi nguoc lai khi in ra. Nho vay
 * khong nham lan giua "ky tu nguoi dung go" va "ban ghi engine sinh ra".
 *
 * Moi dong stdin: mot chuoi phim ('\b' = backspace).
 * Moi dong stdout: <raw>\t<field>\t<U+XXXX ...>
 * Neu mot dong gay loi, in ra <raw>\t!ERROR: ...
 */
public class DiffDriver {
    private static final int WRAP_BASE = 0xE000;
    private static final int WRAP_MAX = 4096;
    private static int WRAP_COUNT = 0;
    private static final int[] WRAP_MAP = new int[WRAP_MAX];

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            String result;
            try {
                result = runOne(line);
            } catch (Throwable t) {
                result = line + "\t!ERROR: " + t + "\t";
            }
            out.println(result);
        }
        out.flush();
    }

    private static String runOne(String line) {
        UnikeyEngine e = new UnikeyEngine();
        e.setKeyWrapper(new Unwrapper());
        StringBuilder field = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == 8) {
                UnikeyEngine.Result r = e.processBackspace();
                if (r.out != null) {
                    applyBackspaces(field, r.backspaces);
                    field.append(r.out);
                } else if (r.backspaces > 0) {
                    applyBackspaces(field, r.backspaces);
                } else if (field.length() > 0) {
                    field.setLength(field.length() - 1);
                }
                continue;
            }
            raw.append(c);
            WRAP_COUNT++;
            if (WRAP_COUNT >= WRAP_MAX) {
                WRAP_COUNT = 1;
            }
            WRAP_MAP[WRAP_COUNT] = c;
            char wrapped = (char) (WRAP_BASE + WRAP_COUNT);

            UnikeyEngine.Result r = e.process(wrapped);
            if (r.out != null) {
                applyBackspaces(field, r.backspaces);
                field.append(r.out);
            } else if (r.handled) {
                // Engine tra ve 0: he thong tu chen ky tu goc.
                field.append(wrapped);
            }
        }
        return raw + "\t" + unwrapField(field.toString()) + "\t" + dump(field.toString());
    }

    static class Unwrapper implements UnikeyEngine.KeyWrapper {
        @Override
        public int unwrapCode(int keyCode) {
            if (keyCode >= WRAP_BASE && keyCode < WRAP_BASE + WRAP_MAX
                    && WRAP_MAP[keyCode - WRAP_BASE] != 0) {
                return WRAP_MAP[keyCode - WRAP_BASE];
            }
            return keyCode;
        }
    }

    private static void applyBackspaces(StringBuilder field, int n) {
        if (n > 0) {
            int len = field.length();
            field.setLength(Math.max(0, len - n));
        }
    }

    private static String unwrapField(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(unwrap(s.charAt(i)));
        }
        return sb.toString();
    }

    private static char unwrap(char c) {
        if (c >= WRAP_BASE && c < WRAP_BASE + WRAP_MAX && WRAP_MAP[c - WRAP_BASE] != 0) {
            return (char) WRAP_MAP[c - WRAP_BASE];
        }
        return c;
    }

    private static String dump(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("U+%04X", (int) unwrap(s.charAt(i))));
        }
        return sb.toString();
    }
}