package com.hkeyboard.vni;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Kiểm tra {@link UnikeyEngine} ở cả hai kiểu gõ Telex và VNI.
 *
 * <p>MỌI giá trị mong đợi trong file này đều lấy từ chính bộ máy C++ của
 * UniKey 1.0.4 (tools/ref, tuỳ chọn mặc định: freeMarking=1, modernStyle=0,
 * spellCheckEnabled=1), không phải do suy đoán. Đối chiếu toàn diện (hàng
 * trăm nghìn chuỗi phím) xem tools/README.md.
 */
public class UnikeyEngineTest {

    private static final char BS = '\b';

    /** Gõ chuỗi phím vào engine, mô phỏng đúng cách HKeyboardService áp dụng kết quả. */
    private static String type(UnikeyEngine engine, String keys) {
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (c == BS) {
                UnikeyEngine.Result r = engine.processBackspace();
                if (r.consumed) {
                    delete(field, r.backspaces);
                    if (r.out != null) {
                        field.append(r.out);
                    }
                } else if (r.backspaces > 0) {
                    delete(field, r.backspaces);
                } else {
                    delete(field, 1);
                }
                continue;
            }
            UnikeyEngine.Result r = engine.process(c);
            if (r.out != null) {
                delete(field, r.backspaces);
                field.append(r.out);
            } else if (r.handled) {
                field.append(c);
            }
        }
        return field.toString();
    }

    private static void delete(StringBuilder sb, int n) {
        sb.setLength(Math.max(0, sb.length() - n));
    }

    private static String telex(String keys) {
        return type(new UnikeyEngine(new UnikeyEngine.Options(),
                UnikeyEngine.InputMethod.TELEX), keys);
    }

    private static String vni(String keys) {
        return type(new UnikeyEngine(new UnikeyEngine.Options(),
                UnikeyEngine.InputMethod.VNI), keys);
    }

    // ------------------------------------------------------------------
    // Telex
    // ------------------------------------------------------------------

    @Test
    public void telexCauVaTu() {
        assertEquals("Việt Nam", telex("Vieetj Nam"));
        assertEquals("Tôi là người Việt Nam", telex("Tooi laf nguwowif Vieetj Nam"));
        assertEquals("đường", telex("dduwowngf"));
        assertEquals("chương trình", telex("chuwowng trinhf"));
        assertEquals("tiếng Việt", telex("tieengs Vieetj"));
        assertEquals("đặc biệt", telex("ddawcj bieetj"));
        assertEquals("xin chào", telex("xin chaof"));
        assertEquals("đi học", telex("ddi hocj"));
        assertEquals("quyền", telex("quyeenf"));
        assertEquals("giữa", telex("giuwax"));
        assertEquals("Hướng", telex("Huwowngs"));
        assertEquals("VIỆT NAM", telex("VIEETJ NAM"));
    }

    @Test
    public void telexKieuCuVaDatDauTuDo() {
        assertEquals("hòa", telex("hoaf"));
        assertEquals("khỏe", telex("khoer"));
        // Dấu móc/thanh gõ sau phụ âm cuối (freeMarking)
        assertEquals("thương", telex("thuowng"));
        assertEquals("trường", telex("truowngf"));
        assertEquals("nước", telex("nuocws"));
        assertEquals("của", telex("cuar"));
        assertEquals("của", telex("cura"));
    }

    @Test
    public void telexDauChu() {
        assertEquals("â", telex("aa"));
        assertEquals("ê", telex("ee"));
        assertEquals("ô", telex("oo"));
        assertEquals("ă", telex("aw"));
        assertEquals("ơ", telex("ow"));
        assertEquals("ư", telex("uw"));
        assertEquals("ươ", telex("uow"));
        assertEquals("ăn", telex("awn"));
        assertEquals("đ", telex("dd"));
        assertEquals("Đ", telex("DD"));
        assertEquals("Đ", telex("Dd"));
    }

    @Test
    public void telexGoHaiLanDeHuy() {
        assertEquals("aa", telex("aaa"));
        assertEquals("dd", telex("ddd"));
        assertEquals("as", telex("ass"));
        assertEquals("à", telex("asf"));
        assertEquals("a", telex("asz"));
    }

    @Test
    public void telexPhimW() {
        assertEquals("ư", telex("w"));
        assertEquals("Ư", telex("W"));
        assertEquals("tư", telex("tw"));
        assertEquals("Trư", telex("Trw"));
        assertEquals("w", telex("ww"));
        assertEquals("ww", telex("www"));
        // Đúng như UniKey: từ tiếng Anh bắt đầu bằng w -> ư (gõ "ww" để ra w)
        assertEquals("ưindow", telex("window"));
    }

    @Test
    public void telexPhimNgoac() {
        assertEquals("ơ", telex("["));
        assertEquals("ư", telex("]"));
        assertEquals("Ơ", telex("{"));
        assertEquals("Ư", telex("}"));
        assertEquals("[", telex("[["));
        assertEquals("tơ", telex("t["));
        assertEquals("người", telex("ng]owif"));
    }

    @Test
    public void telexTuKhongPhaiTiengViet() {
        assertEquals("class", telex("class"));
        assertEquals("java8", telex("java8"));
        // Chữ số không phải phím dấu trong Telex
        assertEquals("a1", telex("a1"));
        assertEquals("Vie65t", telex("Vie65t"));
    }

    @Test
    public void telexCapsLock() {
        // Android: CapsLock bật -> phím w cho ký tự 'W'
        UnikeyEngine e = new UnikeyEngine(new UnikeyEngine.Options(),
                UnikeyEngine.InputMethod.TELEX);
        e.setCapsLockOn(true);
        assertEquals("Ư", type(e, "W"));
        e.reset();
        assertEquals("TƯ", type(e, "TW"));
        e.reset();
        assertEquals("VIỆT", type(e, "VIEETJ"));
        e.reset();
        // Phím ngoặc không đổi theo CapsLock nên UniKey tự đổi chữ hoa/thường
        assertEquals("Ơ", type(e, "["));
        e.reset();
        assertEquals("ơ", type(e, "{"));
    }

    // ------------------------------------------------------------------
    // VNI
    // ------------------------------------------------------------------

    @Test
    public void vniVanHoatDongNhuCu() {
        assertEquals("Việt Nam", vni("Vie65t Nam"));
        assertEquals("đường", vni("d9u7o7ng2"));
        assertEquals("â", vni("a6"));
        // Phím chữ không phải phím dấu trong VNI
        assertEquals("aa", vni("aa"));
        assertEquals("Vieetj", vni("Vieetj"));
    }

    @Test
    public void backspaceSauDauCauKhongLamSapBoGo() {
        // Bản 2.1 ném ArrayIndexOutOfBoundsException ở các ca này.
        assertEquals("(", vni("(a" + BS));
        assertEquals("\"", vni("\"o" + BS));
        assertEquals("x-", vni("x-a" + BS));
        assertEquals("(", telex("(a" + BS));
    }

    // ------------------------------------------------------------------
    // Chuyển kiểu gõ
    // ------------------------------------------------------------------

    @Test
    public void doiKieuGo() {
        UnikeyEngine e = new UnikeyEngine();
        assertEquals(UnikeyEngine.InputMethod.VNI, e.getInputMethod());
        assertEquals("â", type(e, "a6"));

        e.setInputMethod(UnikeyEngine.InputMethod.TELEX);
        assertEquals(UnikeyEngine.InputMethod.TELEX, e.getInputMethod());
        assertEquals("â", type(e, "aa"));

        e.setInputMethod(UnikeyEngine.InputMethod.VNI);
        assertEquals("aa", type(e, "aa"));
    }
}
