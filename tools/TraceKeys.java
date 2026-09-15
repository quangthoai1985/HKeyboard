import com.hkeyboard.vni.UnikeyEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** In chi tiet tung phim cho mot chuoi (co wrapper nhu DiffDriver). */
public class TraceKeys {
    private static final int WRAP_BASE = 0xE000;
    private static final int WRAP_MAX = 4096;
    private static int WRAP_COUNT = 0;
    private static final int[] WRAP_MAP = new int[WRAP_MAX];

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("=== '" + line + "' ===");
            UnikeyEngine e = new UnikeyEngine();
            e.setKeyWrapper(new Unwrapper());
            StringBuilder field = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == 'b') {
                    i++;
                    UnikeyEngine.Result r = e.processBackspace();
                    System.out.println("  <BS> backs=" + r.backspaces + " out=" + show(r.out)
                            + " handled=" + r.handled + " -> field='" + unwrapField(field) + "'");
                    if (r.out != null) {
                        applyBackspaces(field, r.backspaces);
                        field.append(r.out);
                    }
                    continue;
                }
                WRAP_COUNT++;
                if (WRAP_COUNT >= WRAP_MAX) {
                    WRAP_COUNT = 1;
                }
                WRAP_MAP[WRAP_COUNT] = c;
                char wrapped = (char) (WRAP_BASE + WRAP_COUNT);
                UnikeyEngine.Result r = e.process(wrapped);
                System.out.println("  key '" + c + "' id=" + WRAP_COUNT + " backs=" + r.backspaces
                        + " out=" + show(r.out) + " handled=" + r.handled);
                if (r.out != null) {
                    applyBackspaces(field, r.backspaces);
                    field.append(r.out);
                } else if (r.handled) {
                    field.append(wrapped);
                }
                System.out.println("     field='" + unwrapField(field) + "'");
            }
        }
    }

    private static String show(String s) {
        return s == null ? "null" : "'" + unwrapField(s) + "'";
    }

    static class Unwrapper implements UnikeyEngine.KeyWrapper {
        @Override
        public char unwrap(char keyCode) {
            if (keyCode >= WRAP_BASE && keyCode < WRAP_BASE + WRAP_MAX
                    && WRAP_MAP[keyCode - WRAP_BASE] != 0) {
                return (char) WRAP_MAP[keyCode - WRAP_BASE];
            }
            return keyCode;
        }
    }

    private static void applyBackspaces(StringBuilder field, int n) {
        if (n > 0) {
            field.setLength(Math.max(0, field.length() - n));
        }
    }

    private static String unwrapField(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= WRAP_BASE && c < WRAP_BASE + WRAP_MAX && WRAP_MAP[c - WRAP_BASE] != 0
                    ? (char) WRAP_MAP[c - WRAP_BASE] : c);
        }
        return sb.toString();
    }
}