package com.hkeyboard.vni;

/**
 * Bảng ánh xạ keycode → ký tự, dự phòng khi hệ thống không cung cấp được bảng
 * ký tự của thiết bị.
 *
 * <p>Lớp này cố tình <b>không</b> phụ thuộc Android (không import
 * {@code android.view.KeyEvent}) để có thể kiểm thử trên JVM thường. Các hằng
 * số keycode dưới đây chép đúng giá trị của {@code android.view.KeyEvent};
 * {@link #verifyKeyCodes(int[])} dùng để đối chiếu lại với Android ở runtime.
 *
 * <p>Vì sao cần lớp này: {@code KeyEvent.getUnicodeChar()} phụ thuộc bảng ký tự
 * của thiết bị ({@code KeyCharacterMap}). Một số bàn phím rời và một số ROM trả
 * về 0, khiến các phím như {@code ! @ # $ % ^ & * ( )} không gõ được. Bảng này
 * bảo đảm các ký tự đó luôn gõ được.
 */
public final class KeyCharMap {

    // ---- Giá trị keycode (chép từ android.view.KeyEvent) ----
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_STAR = 17;
    public static final int KEYCODE_POUND = 18;
    public static final int KEYCODE_A = 29;
    public static final int KEYCODE_Z = 54;
    public static final int KEYCODE_COMMA = 55;
    public static final int KEYCODE_PERIOD = 56;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_GRAVE = 68;
    public static final int KEYCODE_MINUS = 69;
    public static final int KEYCODE_EQUALS = 70;
    public static final int KEYCODE_LEFT_BRACKET = 71;
    public static final int KEYCODE_RIGHT_BRACKET = 72;
    public static final int KEYCODE_BACKSLASH = 73;
    public static final int KEYCODE_SEMICOLON = 74;
    public static final int KEYCODE_APOSTROPHE = 75;
    public static final int KEYCODE_SLASH = 76;
    public static final int KEYCODE_AT = 77;
    public static final int KEYCODE_PLUS = 81;
    public static final int KEYCODE_NUMPAD_0 = 144;
    public static final int KEYCODE_NUMPAD_9 = 153;
    public static final int KEYCODE_NUMPAD_DIVIDE = 154;
    public static final int KEYCODE_NUMPAD_MULTIPLY = 155;
    public static final int KEYCODE_NUMPAD_SUBTRACT = 156;
    public static final int KEYCODE_NUMPAD_ADD = 157;
    public static final int KEYCODE_NUMPAD_DOT = 158;
    public static final int KEYCODE_NUMPAD_COMMA = 159;
    public static final int KEYCODE_NUMPAD_EQUALS = 161;

    /** Ký tự khi giữ Shift trên hàng phím số (bố cục US). */
    private static final char[] SHIFTED_DIGITS =
            {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')'};

    private KeyCharMap() {
    }

    /**
     * Ký tự mà một phím tạo ra khi không có bảng ký tự của thiết bị.
     *
     * @param keyCode keycode của phím
     * @param shift   có đang giữ Shift hay không
     * @return mã Unicode, hoặc -1 nếu phím không tạo ký tự nào
     */
    public static int charOf(int keyCode, boolean shift) {
        // Hàng phím số trên: ký tự "có Shift" không theo quy luật số học nào
        // nên phải tra bảng.
        if (keyCode >= KEYCODE_0 && keyCode <= KEYCODE_9) {
            int digit = keyCode - KEYCODE_0;
            return shift ? SHIFTED_DIGITS[digit] : '0' + digit;
        }

        // Khối phím số bên phải: giữ Shift không đổi ký tự.
        if (keyCode >= KEYCODE_NUMPAD_0 && keyCode <= KEYCODE_NUMPAD_9) {
            return '0' + (keyCode - KEYCODE_NUMPAD_0);
        }

        // Chữ cái a..z (keycode liền nhau: 29..54).
        if (keyCode >= KEYCODE_A && keyCode <= KEYCODE_Z) {
            int lower = 'a' + (keyCode - KEYCODE_A);
            return shift ? Character.toUpperCase(lower) : lower;
        }

        switch (keyCode) {
            // --- phím có hai ký tự (không Shift / có Shift) ---
            case KEYCODE_COMMA:         return shift ? '<' : ',';
            case KEYCODE_PERIOD:        return shift ? '>' : '.';
            case KEYCODE_GRAVE:         return shift ? '~' : '`';
            case KEYCODE_MINUS:         return shift ? '_' : '-';
            case KEYCODE_EQUALS:        return shift ? '+' : '=';
            case KEYCODE_LEFT_BRACKET:  return shift ? '{' : '[';
            case KEYCODE_RIGHT_BRACKET: return shift ? '}' : ']';
            case KEYCODE_BACKSLASH:     return shift ? '|' : '\\';
            case KEYCODE_SEMICOLON:     return shift ? ':' : ';';
            case KEYCODE_APOSTROPHE:    return shift ? '"' : '\'';
            case KEYCODE_SLASH:         return shift ? '?' : '/';

            // --- phím chỉ có một ký tự ---
            case KEYCODE_SPACE:  return ' ';
            case KEYCODE_STAR:   return '*';
            case KEYCODE_POUND:  return '#';
            case KEYCODE_AT:     return '@';
            case KEYCODE_PLUS:   return '+';

            // --- khối phím số bên phải ---
            case KEYCODE_NUMPAD_DIVIDE:   return '/';
            case KEYCODE_NUMPAD_MULTIPLY: return '*';
            case KEYCODE_NUMPAD_SUBTRACT: return '-';
            case KEYCODE_NUMPAD_ADD:      return '+';
            case KEYCODE_NUMPAD_DOT:      return '.';
            case KEYCODE_NUMPAD_COMMA:    return ',';
            case KEYCODE_NUMPAD_EQUALS:   return '=';

            default:
                return -1;
        }
    }
}
