package com.hkeyboard.vni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Kiểm tra {@link UpdateChecker}.
 *
 * <p>Quan trọng nhất là {@code parseVersionCode}: nó phải khớp **chính xác**
 * công thức tính {@code versionCode} trong {@code version.properties} và trong
 * {@code tools/release.ps1}. Nếu lệch, app sẽ không bao giờ nhận ra bản mới.
 */
public class UpdateCheckerTest {

    @Test
    public void parseVersionCodeTheoQuyUoc() {
        assertEquals(20100, UpdateChecker.parseVersionCode("2.1"));
        assertEquals(20200, UpdateChecker.parseVersionCode("2.2"));
        assertEquals(30000, UpdateChecker.parseVersionCode("3.0"));
        assertEquals(20000, UpdateChecker.parseVersionCode("2"));
        assertEquals(20103, UpdateChecker.parseVersionCode("2.1.3"));
        assertEquals(10000, UpdateChecker.parseVersionCode("1.0"));
    }

    @Test
    public void parseVersionCodeBoHauTo() {
        assertEquals(20100, UpdateChecker.parseVersionCode("2.1-beta"));
        assertEquals(20100, UpdateChecker.parseVersionCode("2.1+build7"));
        assertEquals(20100, UpdateChecker.parseVersionCode(" 2.1 "));
    }

    @Test
    public void parseVersionCodeDauVaoKhongHopLe() {
        assertEquals(0, UpdateChecker.parseVersionCode(""));
        assertEquals(0, UpdateChecker.parseVersionCode(null));
        assertEquals(0, UpdateChecker.parseVersionCode("abc"));
    }

    /**
     * Bản đang cài là v2.1 (versionCode 20100). Tag GitHub v2.1 cũng cho ra
     * 20100, nên KHÔNG được báo có bản mới — đây chính là lỗi sẽ xảy ra nếu
     * versionCode không đồng bộ với tag.
     */
    @Test
    public void cungPhienBanThiKhongBaoCoBanMoi() {
        UpdateChecker.CheckResult same = new UpdateChecker.CheckResult(
                20100, "2.1", UpdateChecker.parseVersionCode("2.1"), "2.1",
                "https://example.com", null, "");
        assertFalse(same.hasUpdate());
    }

    @Test
    public void phienBanMoiHonThiBaoCoBanMoi() {
        UpdateChecker.CheckResult newer = new UpdateChecker.CheckResult(
                20100, "2.1", UpdateChecker.parseVersionCode("2.2"), "2.2",
                "https://example.com", null, "");
        assertTrue(newer.hasUpdate());
    }

    @Test
    public void phienBanCuHonThiKhongBao() {
        UpdateChecker.CheckResult older = new UpdateChecker.CheckResult(
                20100, "2.1", UpdateChecker.parseVersionCode("2.0"), "2.0",
                "https://example.com", null, "");
        assertFalse(older.hasUpdate());
    }
}
