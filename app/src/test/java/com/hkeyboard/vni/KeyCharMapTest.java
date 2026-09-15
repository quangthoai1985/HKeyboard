package com.hkeyboard.vni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Kiểm tra {@link KeyCharMap}:
 *  - các hằng số keycode chép tay phải khớp đúng {@code android.view.KeyEvent};
 *  - bảng ký tự dự phòng phải cho ra đúng {@code ! @ # $ % ^ & * ( )} và các
 *    dấu câu khác khi giữ Shift.
 */
public class KeyCharMapTest {

    @Test
    public void hangSoKeycodeKhopVoiAndroid() {
        assertEquals(KeyEvent.KEYCODE_0, KeyCharMap.KEYCODE_0);
        assertEquals(KeyEvent.KEYCODE_9, KeyCharMap.KEYCODE_9);
        assertEquals(KeyEvent.KEYCODE_STAR, KeyCharMap.KEYCODE_STAR);
        assertEquals(KeyEvent.KEYCODE_POUND, KeyCharMap.KEYCODE_POUND);
        assertEquals(KeyEvent.KEYCODE_A, KeyCharMap.KEYCODE_A);
        assertEquals(KeyEvent.KEYCODE_Z, KeyCharMap.KEYCODE_Z);
        assertEquals(KeyEvent.KEYCODE_COMMA, KeyCharMap.KEYCODE_COMMA);
        assertEquals(KeyEvent.KEYCODE_PERIOD, KeyCharMap.KEYCODE_PERIOD);
        assertEquals(KeyEvent.KEYCODE_SPACE, KeyCharMap.KEYCODE_SPACE);
        assertEquals(KeyEvent.KEYCODE_GRAVE, KeyCharMap.KEYCODE_GRAVE);
        assertEquals(KeyEvent.KEYCODE_MINUS, KeyCharMap.KEYCODE_MINUS);
        assertEquals(KeyEvent.KEYCODE_EQUALS, KeyCharMap.KEYCODE_EQUALS);
        assertEquals(KeyEvent.KEYCODE_LEFT_BRACKET, KeyCharMap.KEYCODE_LEFT_BRACKET);
        assertEquals(KeyEvent.KEYCODE_RIGHT_BRACKET, KeyCharMap.KEYCODE_RIGHT_BRACKET);
        assertEquals(KeyEvent.KEYCODE_BACKSLASH, KeyCharMap.KEYCODE_BACKSLASH);
        assertEquals(KeyEvent.KEYCODE_SEMICOLON, KeyCharMap.KEYCODE_SEMICOLON);
        assertEquals(KeyEvent.KEYCODE_APOSTROPHE, KeyCharMap.KEYCODE_APOSTROPHE);
        assertEquals(KeyEvent.KEYCODE_SLASH, KeyCharMap.KEYCODE_SLASH);
        assertEquals(KeyEvent.KEYCODE_AT, KeyCharMap.KEYCODE_AT);
        assertEquals(KeyEvent.KEYCODE_PLUS, KeyCharMap.KEYCODE_PLUS);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_0, KeyCharMap.KEYCODE_NUMPAD_0);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_9, KeyCharMap.KEYCODE_NUMPAD_9);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyCharMap.KEYCODE_NUMPAD_DIVIDE);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyCharMap.KEYCODE_NUMPAD_MULTIPLY);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyCharMap.KEYCODE_NUMPAD_SUBTRACT);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_ADD, KeyCharMap.KEYCODE_NUMPAD_ADD);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_DOT, KeyCharMap.KEYCODE_NUMPAD_DOT);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_COMMA, KeyCharMap.KEYCODE_NUMPAD_COMMA);
        assertEquals(KeyEvent.KEYCODE_NUMPAD_EQUALS, KeyCharMap.KEYCODE_NUMPAD_EQUALS);
    }

    /** Đây chính là lỗi người dùng báo: Shift + hàng phím số. */
    @Test
    public void shiftHangPhimSo() {
        char[] expected = {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')'};
        for (int i = 0; i < 10; i++) {
            int keyCode = KeyEvent.KEYCODE_0 + i;
            assertEquals("Shift+" + i, expected[i], KeyCharMap.charOf(keyCode, true));
            assertEquals("" + i, (char) ('0' + i), KeyCharMap.charOf(keyCode, false));
        }
    }

    @Test
    public void khongShiftHangPhimSo() {
        for (int i = 0; i < 10; i++) {
            int keyCode = KeyEvent.KEYCODE_0 + i;
            assertEquals('0' + i, KeyCharMap.charOf(keyCode, false));
        }
    }

    @Test
    public void capKyTuCoVaKhongShift() {
        assertEquals(',', KeyCharMap.charOf(KeyEvent.KEYCODE_COMMA, false));
        assertEquals('<', KeyCharMap.charOf(KeyEvent.KEYCODE_COMMA, true));
        assertEquals('.', KeyCharMap.charOf(KeyEvent.KEYCODE_PERIOD, false));
        assertEquals('>', KeyCharMap.charOf(KeyEvent.KEYCODE_PERIOD, true));
        assertEquals('`', KeyCharMap.charOf(KeyEvent.KEYCODE_GRAVE, false));
        assertEquals('~', KeyCharMap.charOf(KeyEvent.KEYCODE_GRAVE, true));
        assertEquals('-', KeyCharMap.charOf(KeyEvent.KEYCODE_MINUS, false));
        assertEquals('_', KeyCharMap.charOf(KeyEvent.KEYCODE_MINUS, true));
        assertEquals('=', KeyCharMap.charOf(KeyEvent.KEYCODE_EQUALS, false));
        assertEquals('+', KeyCharMap.charOf(KeyEvent.KEYCODE_EQUALS, true));
        assertEquals('[', KeyCharMap.charOf(KeyEvent.KEYCODE_LEFT_BRACKET, false));
        assertEquals('{', KeyCharMap.charOf(KeyEvent.KEYCODE_LEFT_BRACKET, true));
        assertEquals(']', KeyCharMap.charOf(KeyEvent.KEYCODE_RIGHT_BRACKET, false));
        assertEquals('}', KeyCharMap.charOf(KeyEvent.KEYCODE_RIGHT_BRACKET, true));
        assertEquals('\\', KeyCharMap.charOf(KeyEvent.KEYCODE_BACKSLASH, false));
        assertEquals('|', KeyCharMap.charOf(KeyEvent.KEYCODE_BACKSLASH, true));
        assertEquals(';', KeyCharMap.charOf(KeyEvent.KEYCODE_SEMICOLON, false));
        assertEquals(':', KeyCharMap.charOf(KeyEvent.KEYCODE_SEMICOLON, true));
        assertEquals('\'', KeyCharMap.charOf(KeyEvent.KEYCODE_APOSTROPHE, false));
        assertEquals('"', KeyCharMap.charOf(KeyEvent.KEYCODE_APOSTROPHE, true));
        assertEquals('/', KeyCharMap.charOf(KeyEvent.KEYCODE_SLASH, false));
        assertEquals('?', KeyCharMap.charOf(KeyEvent.KEYCODE_SLASH, true));
    }

    @Test
    public void phimMotKyTu() {
        assertEquals(' ', KeyCharMap.charOf(KeyEvent.KEYCODE_SPACE, false));
        assertEquals(' ', KeyCharMap.charOf(KeyEvent.KEYCODE_SPACE, true));
        assertEquals('*', KeyCharMap.charOf(KeyEvent.KEYCODE_STAR, false));
        assertEquals('#', KeyCharMap.charOf(KeyEvent.KEYCODE_POUND, false));
        assertEquals('@', KeyCharMap.charOf(KeyEvent.KEYCODE_AT, false));
        assertEquals('+', KeyCharMap.charOf(KeyEvent.KEYCODE_PLUS, false));
    }

    @Test
    public void chuCaiCoVaKhongShift() {
        assertEquals('a', KeyCharMap.charOf(KeyEvent.KEYCODE_A, false));
        assertEquals('A', KeyCharMap.charOf(KeyEvent.KEYCODE_A, true));
        assertEquals('z', KeyCharMap.charOf(KeyEvent.KEYCODE_Z, false));
        assertEquals('Z', KeyCharMap.charOf(KeyEvent.KEYCODE_Z, true));
        for (int k = KeyEvent.KEYCODE_A; k <= KeyEvent.KEYCODE_Z; k++) {
            char lower = KeyCharMap.charOf(k, false) == 0
                    ? 0 : (char) KeyCharMap.charOf(k, false);
            assertTrue("keycode " + k, lower >= 'a' && lower <= 'z');
            assertEquals(Character.toUpperCase(lower),
                    (char) KeyCharMap.charOf(k, true));
        }
    }

    @Test
    public void numpadGiuShiftKhongDoi() {
        for (int i = 0; i < 10; i++) {
            int keyCode = KeyEvent.KEYCODE_NUMPAD_0 + i;
            assertEquals('0' + i, KeyCharMap.charOf(keyCode, false));
            assertEquals('0' + i, KeyCharMap.charOf(keyCode, true));
        }
        assertEquals('/', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_DIVIDE, true));
        assertEquals('*', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, true));
        assertEquals('-', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, true));
        assertEquals('+', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_ADD, true));
        assertEquals('.', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_DOT, true));
        assertEquals(',', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_COMMA, true));
        assertEquals('=', KeyCharMap.charOf(KeyEvent.KEYCODE_NUMPAD_EQUALS, true));
    }

    @Test
    public void phimKhongInDuoc() {
        assertEquals(-1, KeyCharMap.charOf(KeyEvent.KEYCODE_ENTER, false));
        assertEquals(-1, KeyCharMap.charOf(KeyEvent.KEYCODE_TAB, false));
        assertEquals(-1, KeyCharMap.charOf(KeyEvent.KEYCODE_DEL, false));
        assertEquals(-1, KeyCharMap.charOf(KeyEvent.KEYCODE_SHIFT_LEFT, false));
        assertEquals(-1, KeyCharMap.charOf(KeyEvent.KEYCODE_ESCAPE, false));
    }
}
