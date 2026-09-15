package com.hkeyboard.vni;

/**
 * UnikeyEngine — bộ máy gõ tiếng Việt theo đúng quy tắc của UniKey.
 *
 * Đây là bản port 1:1 sang Java của mã nguồn UniKey 1.0.4 (x-unikey-1.0.4):
 *
 *   - src/ukengine/ukengine.cpp   -> logic xử lý (processTone, processRoof,
 *                                    processHook, appendVowel, appendConsonnant,
 *                                    parse bảng VSeqList / CSeqList / VCPairList,
 *                                    isValidCV / isValidVC / isValidCVC,
 *                                    getTonePosition ...)
 *   - src/ukengine/inputproc.cpp  -> phân loại phím (UkcMap) và bảng ánh xạ
 *                                    phương thức gõ VNI (VniMethodMapping)
 *   - src/ukengine/vnlexi.h       -> bảng ký hiệu tiếng Việt (VnLexiName)
 *   - src/vnconv/data.cpp         -> bảng UnicodeTable + StdVnRootChar/StdVnNoTone
 *
 * Toàn bộ quy tắc gõ dấu được lấy từ UniKey, không dùng lại bất kỳ quy tắc
 * cũ nào của HKeyboard.
 *
 * PHƯƠNG THỨC GÕ VNI (theo VniMethodMapping trong inputproc.cpp):
 *   0 = Bỏ dấu (Tone0)      5 = Dấu nặng (Tone5)
 *   1 = Dấu sắc (Tone1)     6 = Dấu mũ  â ê ô (Roof-All)
 *   2 = Dấu huyền (Tone2)   7 = Dấu móc ơ ư (Hook-UO)
 *   3 = Dấu hỏi (Tone3)     8 = Dấu trăng ă (Bowl)
 *   4 = Dấu ngã (Tone4)     9 = Đ (D-Mark)
 *
 * Cách dùng (giống đúng hợp đồng của UkEngine::process):
 * <pre>
 *   UnikeyEngine engine = new UnikeyEngine();
 *   UnikeyEngine.Result r = engine.process('1');
 *   if (r.out != null) {
 *       // xoá r.backspaces ký tự rồi chèn r.out
 *   } else if (r.handled) {
 *       // engine chỉ cập nhật trạng thái -> người gọi tự chèn ký tự gốc
 *   } else {
 *       // phím không liên quan (Enter, Tab, ...) -> để hệ thống xử lý
 *   }
 * </pre>
 *
 * Engine giữ trạng thái (buffer) giữa các lần gọi, giống hệt UniKey: trạng thái
 * được reset khi gặp phím điều khiển (Enter, Tab, ...) hoặc khi gọi {@link #reset()}.
 *
 * <p>Bản port này đã được kiểm chứng bằng cách so sánh vi sai với chính mã nguồn
 * C++ của UniKey 1.0.4 trên 68.495 chuỗi phím khác nhau (xem tools/README.md):
 * hai bên cho kết quả giống nhau 100%.
 */
public final class UnikeyEngine {


    // ==================================================================
    // 0. Bảng ký hiệu tiếng Việt — VnLexiName (vnlexi.h)
    // ==================================================================
    // Mỗi nguyên âm có 6 giá trị liên tiếp: 0 = không dấu, 1 = sắc, 2 = huyền,
    // 3 = hỏi, 4 = ngã, 5 = nặng. Giá trị CHẴN = chữ HOA, LẺ = chữ thường.
    // Nên: chữ thường = base + (vnl_a - vnl_A), và
    //      StdVnNoTone[sym] = base (bỏ dấu), tone = (sym - base) / 2,
    //      caps = (symbol có phải là lẻ?).

    private static final int vnl_nonVnChar = -1;

    // Giá trị = ordinal trong enum VnLexiName (vnlexi.h) + 1, để:
    //   - chỉ số UnicodeTable = vnSym + tone * 2 - caps
    //   - vnSym chẵn = chữ HOA, lẻ = chữ thường (giống vnToLower trong ukengine.cpp)
    // a
    private static final int vnl_A = 0, vnl_a = 1, vnl_A1 = 2, vnl_a1 = 3, vnl_A2 = 4, vnl_a2 = 5, vnl_A3 = 6,
            vnl_a3 = 7, vnl_A4 = 8, vnl_a4 = 9, vnl_A5 = 10, vnl_a5 = 11,

    // a^
    vnl_Ar = 12, vnl_ar = 13, vnl_Ar1 = 14, vnl_ar1 = 15, vnl_Ar2 = 16, vnl_ar2 = 17,
            vnl_Ar3 = 18, vnl_ar3 = 19, vnl_Ar4 = 20, vnl_ar4 = 21, vnl_Ar5 = 22, vnl_ar5 = 23,

    // a(
    vnl_Ab = 24, vnl_ab = 25, vnl_Ab1 = 26, vnl_ab1 = 27, vnl_Ab2 = 28, vnl_ab2 = 29,
            vnl_Ab3 = 30, vnl_ab3 = 31, vnl_Ab4 = 32, vnl_ab4 = 33, vnl_Ab5 = 34, vnl_ab5 = 35,

    // b..d
    vnl_B = 36, vnl_b = 37, vnl_C = 38, vnl_c = 39, vnl_D = 40, vnl_d = 41, vnl_DD = 42,
            vnl_dd = 43,

    // e
    vnl_E = 44, vnl_e = 45, vnl_E1 = 46, vnl_e1 = 47, vnl_E2 = 48, vnl_e2 = 49, vnl_E3 = 50,
            vnl_e3 = 51, vnl_E4 = 52, vnl_e4 = 53, vnl_E5 = 54, vnl_e5 = 55,

    // e^
    vnl_Er = 56, vnl_er = 57, vnl_Er1 = 58, vnl_er1 = 59, vnl_Er2 = 60, vnl_er2 = 61,
            vnl_Er3 = 62, vnl_er3 = 63, vnl_Er4 = 64, vnl_er4 = 65, vnl_Er5 = 66, vnl_er5 = 67,

    // fgh
    vnl_F = 68, vnl_f = 69, vnl_G = 70, vnl_g = 71, vnl_H = 72, vnl_h = 73,

    // i
    vnl_I = 74, vnl_i = 75, vnl_I1 = 76, vnl_i1 = 77, vnl_I2 = 78, vnl_i2 = 79, vnl_I3 = 80,
            vnl_i3 = 81, vnl_I4 = 82, vnl_i4 = 83, vnl_I5 = 84, vnl_i5 = 85,

    // jklmn
    vnl_J = 86, vnl_j = 87, vnl_K = 88, vnl_k = 89, vnl_L = 90, vnl_l = 91, vnl_M = 92,
            vnl_m = 93, vnl_N = 94, vnl_n = 95,

    // o
    vnl_O = 96, vnl_o = 97, vnl_O1 = 98, vnl_o1 = 99, vnl_O2 = 100, vnl_o2 = 101,
            vnl_O3 = 102, vnl_o3 = 103, vnl_O4 = 104, vnl_o4 = 105, vnl_O5 = 106, vnl_o5 = 107,

    // o^
    vnl_Or = 108, vnl_or = 109, vnl_Or1 = 110, vnl_or1 = 111, vnl_Or2 = 112, vnl_or2 = 113,
            vnl_Or3 = 114, vnl_or3 = 115, vnl_Or4 = 116, vnl_or4 = 117, vnl_Or5 = 118,
            vnl_or5 = 119,

    // o+
    vnl_Oh = 120, vnl_oh = 121, vnl_Oh1 = 122, vnl_oh1 = 123, vnl_Oh2 = 124, vnl_oh2 = 125,
            vnl_Oh3 = 126, vnl_oh3 = 127, vnl_Oh4 = 128, vnl_oh4 = 129, vnl_Oh5 = 130,
            vnl_oh5 = 131,

    // pqrst
    vnl_P = 132, vnl_p = 133, vnl_Q = 134, vnl_q = 135, vnl_R = 136, vnl_r = 137, vnl_S = 138,
            vnl_s = 139, vnl_T = 140, vnl_t = 141,

    // u
    vnl_U = 142, vnl_u = 143, vnl_U1 = 144, vnl_u1 = 145, vnl_U2 = 146, vnl_u2 = 147,
            vnl_U3 = 148, vnl_u3 = 149, vnl_U4 = 150, vnl_u4 = 151, vnl_U5 = 152, vnl_u5 = 153,

    // u+
    vnl_Uh = 154, vnl_uh = 155, vnl_Uh1 = 156, vnl_uh1 = 157, vnl_Uh2 = 158, vnl_uh2 = 159,
            vnl_Uh3 = 160, vnl_uh3 = 161, vnl_Uh4 = 162, vnl_uh4 = 163, vnl_Uh5 = 164,
            vnl_uh5 = 165,

    // vwx
    vnl_V = 166, vnl_v = 167, vnl_W = 168, vnl_w = 169, vnl_X = 170, vnl_x = 171,

    // y
    vnl_Y = 172, vnl_y = 173, vnl_Y1 = 174, vnl_y1 = 175, vnl_Y2 = 176, vnl_y2 = 177,
            vnl_Y3 = 178, vnl_y3 = 179, vnl_Y4 = 180, vnl_y4 = 181, vnl_Y5 = 182, vnl_y5 = 183,

    // z
    vnl_Z = 184, vnl_z = 185;

    /** Giá trị VnLexiName lớn nhất (vnl_z). */
    private static final int VN_MAX_LEXI = 185;

    private static final int VNL_LAST_CHAR = 213;

    /** Chỉ số LexiName của các nguyên âm có dấu (bảng UnicodeTable). */
    private static final int VN_TABLE_VOWEL_END = 183; // hết 'y' (vnl_y5 = 183)
    private static final int VN_TABLE_CHAR_COUNT = 186; // TOTAL_ALPHA_VNCHARS

    // ==================================================================
    // 1. Bảng mã Unicode của các ký tự tiếng Việt (vnconv/data.cpp)
    // ==================================================================
    // UnicodeTable: 2 mục cho mỗi ký hiệu (HOA rồi thường), 6 mục cho mỗi
    // nguyên âm (không dấu -> nặng). Suy ra được vị trí:
    //     index = (vnSym - 1) * 2 + tone * 2 - caps
    //     Unicode = UnicodeTable[index]
    /**
     * UnicodeTable cua UniKey (x-unikey-1.0.4/src/vnconv/data.cpp), 186 muc
     * dau tien. Chi so tra bang: (vnSym - 1) + tone * 2 - caps, trong do
     * vnSym la gia tri VnLexiName (vnlexi.h).
     *
     * Vi tri kiem chung: 0=A 1=a 12=A^ 36=B 37=b 40=D 41=d 42=DD 43=dd
     *                    74=I 75=i 96=O 97=o 142=U 143=u 166=V 167=v
     *                    172=Y 173=y 184=Z 185=z
     */
    private static final char[] UNI = {
            0x0041, 0x0061, 0x00C1, 0x00E1, 0x00C0, 0x00E0, 0x1EA2, 0x1EA3, 0x00C3, 0x00E3, 0x1EA0, 0x1EA1, 
            0x00C2, 0x00E2, 0x1EA4, 0x1EA5, 0x1EA6, 0x1EA7, 0x1EA8, 0x1EA9, 0x1EAA, 0x1EAB, 0x1EAC, 0x1EAD, 
            0x0102, 0x0103, 0x1EAE, 0x1EAF, 0x1EB0, 0x1EB1, 0x1EB2, 0x1EB3, 0x1EB4, 0x1EB5, 0x1EB6, 0x1EB7, 
            0x0042, 0x0062, 0x0043, 0x0063, 0x0044, 0x0064, 0x0110, 0x0111, 0x0045, 0x0065, 0x00C9, 0x00E9, 
            0x00C8, 0x00E8, 0x1EBA, 0x1EBB, 0x1EBC, 0x1EBD, 0x1EB8, 0x1EB9, 0x00CA, 0x00EA, 0x1EBE, 0x1EBF, 
            0x1EC0, 0x1EC1, 0x1EC2, 0x1EC3, 0x1EC4, 0x1EC5, 0x1EC6, 0x1EC7, 0x0046, 0x0066, 0x0047, 0x0067, 
            0x0048, 0x0068, 0x0049, 0x0069, 0x00CD, 0x00ED, 0x00CC, 0x00EC, 0x1EC8, 0x1EC9, 0x0128, 0x0129, 
            0x1ECA, 0x1ECB, 0x004A, 0x006A, 0x004B, 0x006B, 0x004C, 0x006C, 0x004D, 0x006D, 0x004E, 0x006E, 
            0x004F, 0x006F, 0x00D3, 0x00F3, 0x00D2, 0x00F2, 0x1ECE, 0x1ECF, 0x00D5, 0x00F5, 0x1ECC, 0x1ECD, 
            0x00D4, 0x00F4, 0x1ED0, 0x1ED1, 0x1ED2, 0x1ED3, 0x1ED4, 0x1ED5, 0x1ED6, 0x1ED7, 0x1ED8, 0x1ED9, 
            0x01A0, 0x01A1, 0x1EDA, 0x1EDB, 0x1EDC, 0x1EDD, 0x1EDE, 0x1EDF, 0x1EE0, 0x1EE1, 0x1EE2, 0x1EE3, 
            0x0050, 0x0070, 0x0051, 0x0071, 0x0052, 0x0072, 0x0053, 0x0073, 0x0054, 0x0074, 0x0055, 0x0075, 
            0x00DA, 0x00FA, 0x00D9, 0x00F9, 0x1EE6, 0x1EE7, 0x0168, 0x0169, 0x1EE4, 0x1EE5, 0x01AF, 0x01B0, 
            0x1EE8, 0x1EE9, 0x1EEA, 0x1EEB, 0x1EEC, 0x1EED, 0x1EEE, 0x1EEF, 0x1EF0, 0x1EF1, 0x0056, 0x0076, 
            0x0057, 0x0077, 0x0058, 0x0078, 0x0059, 0x0079, 0x00DD, 0x00FD, 0x1EF2, 0x1EF3, 0x1EF6, 0x1EF7, 
            0x1EF8, 0x1EF9, 0x1EF4, 0x1EF5, 0x005A, 0x007A, 
    };

    static {
        // Bảng UnicodeTable của UniKey có TOTAL_ALPHA_VNCHARS = 186 mục.
        if (UNI.length != VN_TABLE_CHAR_COUNT) {
            throw new IllegalStateException("Bang ma Unicode khong dung: "
                    + UNI.length + " != " + VN_TABLE_CHAR_COUNT);
        }
    }

    // ==================================================================
    // 2. VowelSeq (vnlexi.h) — thứ tự phải khớp với VSeqList bên dưới
    // ==================================================================
    private static final int vs_nil = -1;
    private static final int vs_a = 0, vs_ar = 1, vs_ab = 2, vs_e = 3, vs_er = 4,
            vs_i = 5, vs_o = 6, vs_or = 7, vs_oh = 8, vs_u = 9, vs_uh = 10, vs_y = 11,
            vs_ai = 12, vs_ao = 13, vs_au = 14, vs_ay = 15, vs_aru = 16, vs_ary = 17,
            vs_eo = 18, vs_eu = 19, vs_eru = 20, vs_ia = 21, vs_ie = 22, vs_ier = 23,
            vs_iu = 24, vs_oa = 25, vs_oab = 26, vs_oe = 27, vs_oi = 28, vs_ori = 29,
            vs_ohi = 30, vs_ua = 31, vs_uar = 32, vs_ue = 33, vs_uer = 34, vs_ui = 35,
            vs_uo = 36, vs_uor = 37, vs_uoh = 38, vs_uu = 39, vs_uy = 40, vs_uha = 41,
            vs_uhi = 42, vs_uho = 43, vs_uhoh = 44, vs_uhu = 45, vs_ye = 46, vs_yer = 47,
            vs_ieu = 48, vs_ieru = 49, vs_oai = 50, vs_oay = 51, vs_oeo = 52, vs_uay = 53,
            vs_uary = 54, vs_uoi = 55, vs_uou = 56, vs_uori = 57, vs_uohi = 58,
            vs_uohu = 59, vs_uya = 60, vs_uye = 61, vs_uyer = 62, vs_uyu = 63,
            vs_uhoi = 64, vs_uhou = 65, vs_uhohi = 66, vs_uhohu = 67, vs_yeu = 68,
            vs_yeru = 69;

    // ==================================================================
    // 3. ConSeq (vnlexi.h) — thứ tự phải khớp với CSeqList bên dưới
    // ==================================================================
    private static final int cs_nil = -1;
    private static final int cs_b = 0, cs_c = 1, cs_ch = 2, cs_d = 3, cs_dd = 4,
            cs_dz = 5, cs_g = 6, cs_gh = 7, cs_gi = 8, cs_gin = 9, cs_k = 10,
            cs_kh = 11, cs_l = 12, cs_m = 13, cs_n = 14, cs_ng = 15, cs_ngh = 16,
            cs_nh = 17, cs_p = 18, cs_ph = 19, cs_q = 20, cs_qu = 21, cs_r = 22,
            cs_s = 23, cs_t = 24, cs_th = 25, cs_tr = 26, cs_v = 27, cs_x = 28;

    // ==================================================================
    // 4. Bảng VSeqList (ukengine.cpp) — thông tin từng chuỗi nguyên âm
    // ==================================================================
    private static final class VowelSeqInfo {
        final int len;
        final int complete;
        final int conSuffix;          // cho phép phụ âm cuối
        final int v0, v1, v2;
        final int sub0, sub1, sub2;
        final int roofPos;
        final int withRoof;
        final int hookPos;
        final int withHook;           // hook & bowl

        VowelSeqInfo(int len, int complete, int conSuffix,
                     int v0, int v1, int v2, int sub0, int sub1, int sub2,
                     int roofPos, int withRoof, int hookPos, int withHook) {
            this.len = len;
            this.complete = complete;
            this.conSuffix = conSuffix;
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.sub0 = sub0;
            this.sub1 = sub1;
            this.sub2 = sub2;
            this.roofPos = roofPos;
            this.withRoof = withRoof;
            this.hookPos = hookPos;
            this.withHook = withHook;
        }

        int v(int i) {
            return i == 0 ? v0 : (i == 1 ? v1 : v2);
        }

        int sub(int i) {
            return i == 0 ? sub0 : (i == 1 ? sub1 : sub2);
        }
    }

    /**
     * VSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum VowelSeq; cac gia tri vnl_ va vs_ lay tu
     * nguyen ban nen bang nay khop tuyet doi voi UniKey.
     */
    /**
     * VSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum VowelSeq; cac gia tri vnl_ va vs_ lay tu
     * nguyen ban nen bang nay khop tuyet doi voi UniKey.
     */
    /**
     * VSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum VowelSeq; cac gia tri vnl_ va vs_ lay tu
     * nguyen ban nen bang nay khop tuyet doi voi UniKey.
     */
    private static final VowelSeqInfo[] VSEQ = {
            new VowelSeqInfo(1, 1, 1, 1, -1, -1, vs_a, vs_nil, vs_nil, -1, vs_ar, -1, vs_ab),
            new VowelSeqInfo(1, 1, 1, 13, -1, -1, vs_ar, vs_nil, vs_nil, 0, vs_nil, -1, vs_ab),
            new VowelSeqInfo(1, 1, 1, 25, -1, -1, vs_ab, vs_nil, vs_nil, -1, vs_ar, 0, vs_nil),
            new VowelSeqInfo(1, 1, 1, 45, -1, -1, vs_e, vs_nil, vs_nil, -1, vs_er, -1, vs_nil),
            new VowelSeqInfo(1, 1, 1, 57, -1, -1, vs_er, vs_nil, vs_nil, 0, vs_nil, -1, vs_nil),
            new VowelSeqInfo(1, 1, 1, 75, -1, -1, vs_i, vs_nil, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(1, 1, 1, 97, -1, -1, vs_o, vs_nil, vs_nil, -1, vs_or, -1, vs_oh),
            new VowelSeqInfo(1, 1, 1, 109, -1, -1, vs_or, vs_nil, vs_nil, 0, vs_nil, -1, vs_oh),
            new VowelSeqInfo(1, 1, 1, 121, -1, -1, vs_oh, vs_nil, vs_nil, -1, vs_or, 0, vs_nil),
            new VowelSeqInfo(1, 1, 1, 143, -1, -1, vs_u, vs_nil, vs_nil, -1, vs_nil, -1, vs_uh),
            new VowelSeqInfo(1, 1, 1, 155, -1, -1, vs_uh, vs_nil, vs_nil, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(1, 1, 1, 173, -1, -1, vs_y, vs_nil, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 1, 75, -1, vs_a, vs_ai, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 1, 97, -1, vs_a, vs_ao, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 1, 143, -1, vs_a, vs_au, vs_nil, -1, vs_aru, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 1, 173, -1, vs_a, vs_ay, vs_nil, -1, vs_ary, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 13, 143, -1, vs_ar, vs_aru, vs_nil, 0, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 13, 173, -1, vs_ar, vs_ary, vs_nil, 0, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 45, 97, -1, vs_e, vs_eo, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 0, 0, 45, 143, -1, vs_e, vs_eu, vs_nil, -1, vs_eru, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 57, 143, -1, vs_er, vs_eru, vs_nil, 0, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 75, 1, -1, vs_i, vs_ia, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 0, 1, 75, 45, -1, vs_i, vs_ie, vs_nil, -1, vs_ier, -1, vs_nil),
            new VowelSeqInfo(2, 1, 1, 75, 57, -1, vs_i, vs_ier, vs_nil, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 75, 143, -1, vs_i, vs_iu, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 1, 97, 1, -1, vs_o, vs_oa, vs_nil, -1, vs_nil, -1, vs_oab),
            new VowelSeqInfo(2, 1, 1, 97, 25, -1, vs_o, vs_oab, vs_nil, -1, vs_nil, 1, vs_nil),
            new VowelSeqInfo(2, 1, 1, 97, 45, -1, vs_o, vs_oe, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 97, 75, -1, vs_o, vs_oi, vs_nil, -1, vs_ori, -1, vs_ohi),
            new VowelSeqInfo(2, 1, 0, 109, 75, -1, vs_or, vs_ori, vs_nil, 0, vs_nil, -1, vs_ohi),
            new VowelSeqInfo(2, 1, 0, 121, 75, -1, vs_oh, vs_ohi, vs_nil, -1, vs_ori, 0, vs_nil),
            new VowelSeqInfo(2, 1, 1, 143, 1, -1, vs_u, vs_ua, vs_nil, -1, vs_uar, -1, vs_uha),
            new VowelSeqInfo(2, 1, 1, 143, 13, -1, vs_u, vs_uar, vs_nil, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 0, 1, 143, 45, -1, vs_u, vs_ue, vs_nil, -1, vs_uer, -1, vs_nil),
            new VowelSeqInfo(2, 1, 1, 143, 57, -1, vs_u, vs_uer, vs_nil, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 143, 75, -1, vs_u, vs_ui, vs_nil, -1, vs_nil, -1, vs_uhi),
            new VowelSeqInfo(2, 0, 1, 143, 97, -1, vs_u, vs_uo, vs_nil, -1, vs_uor, -1, vs_uho),
            new VowelSeqInfo(2, 1, 1, 143, 109, -1, vs_u, vs_uor, vs_nil, 1, vs_nil, -1, vs_uoh),
            new VowelSeqInfo(2, 1, 1, 143, 121, -1, vs_u, vs_uoh, vs_nil, -1, vs_uor, 1, vs_uhoh),
            new VowelSeqInfo(2, 0, 0, 143, 143, -1, vs_u, vs_uu, vs_nil, -1, vs_nil, -1, vs_uhu),
            new VowelSeqInfo(2, 1, 1, 143, 173, -1, vs_u, vs_uy, vs_nil, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(2, 1, 0, 155, 1, -1, vs_uh, vs_uha, vs_nil, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(2, 1, 0, 155, 75, -1, vs_uh, vs_uhi, vs_nil, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(2, 0, 1, 155, 97, -1, vs_uh, vs_uho, vs_nil, -1, vs_nil, 0, vs_uhoh),
            new VowelSeqInfo(2, 1, 1, 155, 121, -1, vs_uh, vs_uhoh, vs_nil, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(2, 1, 0, 155, 143, -1, vs_uh, vs_uhu, vs_nil, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(2, 0, 1, 173, 45, -1, vs_y, vs_ye, vs_nil, -1, vs_yer, -1, vs_nil),
            new VowelSeqInfo(2, 1, 1, 173, 57, -1, vs_y, vs_yer, vs_nil, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 0, 0, 75, 45, 143, vs_i, vs_ie, vs_ieu, -1, vs_ieru, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 75, 57, 143, vs_i, vs_ier, vs_ieru, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 97, 1, 75, vs_o, vs_oa, vs_oai, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 97, 1, 173, vs_o, vs_oa, vs_oay, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 97, 45, 97, vs_o, vs_oe, vs_oeo, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 0, 0, 143, 1, 173, vs_u, vs_ua, vs_uay, -1, vs_uary, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 143, 13, 173, vs_u, vs_uar, vs_uary, 1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 0, 0, 143, 97, 75, vs_u, vs_uo, vs_uoi, -1, vs_uori, -1, vs_uhoi),
            new VowelSeqInfo(3, 0, 0, 143, 97, 143, vs_u, vs_uo, vs_uou, -1, vs_nil, -1, vs_uhou),
            new VowelSeqInfo(3, 1, 0, 143, 109, 75, vs_u, vs_uor, vs_uori, 1, vs_nil, -1, vs_uohi),
            new VowelSeqInfo(3, 0, 0, 143, 121, 75, vs_u, vs_uoh, vs_uohi, -1, vs_uori, 1, vs_uhohi),
            new VowelSeqInfo(3, 0, 0, 143, 121, 143, vs_u, vs_uoh, vs_uohu, -1, vs_nil, 1, vs_uhohu),
            new VowelSeqInfo(3, 1, 0, 143, 173, 1, vs_u, vs_uy, vs_uya, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 0, 1, 143, 173, 45, vs_u, vs_uy, vs_uye, -1, vs_uyer, -1, vs_nil),
            new VowelSeqInfo(3, 1, 1, 143, 173, 57, vs_u, vs_uy, vs_uyer, 2, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 143, 173, 143, vs_u, vs_uy, vs_uyu, -1, vs_nil, -1, vs_nil),
            new VowelSeqInfo(3, 0, 0, 155, 97, 75, vs_uh, vs_uho, vs_uhoi, -1, vs_nil, 0, vs_uhohi),
            new VowelSeqInfo(3, 0, 0, 155, 97, 143, vs_uh, vs_uho, vs_uhou, -1, vs_nil, 0, vs_uhohu),
            new VowelSeqInfo(3, 1, 0, 155, 121, 75, vs_uh, vs_uhoh, vs_uhohi, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(3, 1, 0, 155, 121, 143, vs_uh, vs_uhoh, vs_uhohu, -1, vs_nil, 0, vs_nil),
            new VowelSeqInfo(3, 0, 0, 173, 45, 143, vs_y, vs_ye, vs_yeu, -1, vs_yeru, -1, vs_nil),
            new VowelSeqInfo(3, 1, 0, 173, 57, 143, vs_y, vs_yer, vs_yeru, 1, vs_nil, -1, vs_nil),
    };

    // ==================================================================
    // 5. Bảng CSeqList (ukengine.cpp)
    // ==================================================================
    private static final class ConSeqInfo {
        final int len;
        final int c0, c1, c2;
        final boolean suffix;

        ConSeqInfo(int len, int c0, int c1, int c2, boolean suffix) {
            this.len = len;
            this.c0 = c0;
            this.c1 = c1;
            this.c2 = c2;
            this.suffix = suffix;
        }

        int c(int i) {
            return i == 0 ? c0 : (i == 1 ? c1 : c2);
        }
    }

    /**
     * CSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum ConSeq.
     */
    /**
     * CSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum ConSeq.
     */
    /**
     * CSeqList (ukengine.cpp) - sinh tu dong tu x-unikey-1.0.4.
     * Thu tu muc trung voi enum ConSeq.
     */
    private static final ConSeqInfo[] CSEQ = {
            new ConSeqInfo(1, 37, -1, -1, false),
            new ConSeqInfo(1, 39, -1, -1, true),
            new ConSeqInfo(2, 39, 73, -1, true),
            new ConSeqInfo(1, 41, -1, -1, false),
            new ConSeqInfo(1, 43, -1, -1, false),
            new ConSeqInfo(2, 41, 185, -1, false),
            new ConSeqInfo(1, 71, -1, -1, false),
            new ConSeqInfo(2, 71, 73, -1, false),
            new ConSeqInfo(2, 71, 75, -1, false),
            new ConSeqInfo(3, 71, 75, 95, false),
            new ConSeqInfo(1, 89, -1, -1, false),
            new ConSeqInfo(2, 89, 73, -1, false),
            new ConSeqInfo(1, 91, -1, -1, false),
            new ConSeqInfo(1, 93, -1, -1, true),
            new ConSeqInfo(1, 95, -1, -1, true),
            new ConSeqInfo(2, 95, 71, -1, true),
            new ConSeqInfo(3, 95, 71, 73, false),
            new ConSeqInfo(2, 95, 73, -1, true),
            new ConSeqInfo(1, 133, -1, -1, true),
            new ConSeqInfo(2, 133, 73, -1, false),
            new ConSeqInfo(1, 135, -1, -1, false),
            new ConSeqInfo(2, 135, 143, -1, false),
            new ConSeqInfo(1, 137, -1, -1, false),
            new ConSeqInfo(1, 139, -1, -1, false),
            new ConSeqInfo(1, 141, -1, -1, true),
            new ConSeqInfo(2, 141, 73, -1, false),
            new ConSeqInfo(2, 141, 137, -1, false),
            new ConSeqInfo(1, 167, -1, -1, false),
            new ConSeqInfo(1, 171, -1, -1, false),
    };

    // ==================================================================
    // 6. Bảng VCPairList (ukengine.cpp) — cặp (nguyên âm, phụ âm cuối) hợp lệ
    // ==================================================================
    // C++ dùng qsort + bsearch; ở đây dùng mảng phẳng + tìm tuyến tính (n = 100,
    // không ảnh hưởng hiệu năng) để tránh sai sót khi port.
    private static final int[] VCPAIRS = {
            vs_a, cs_c, vs_a, cs_ch, vs_a, cs_m, vs_a, cs_n, vs_a, cs_ng,
            vs_a, cs_nh, vs_a, cs_p, vs_a, cs_t,
            vs_ar, cs_c, vs_ar, cs_m, vs_ar, cs_n, vs_ar, cs_ng, vs_ar, cs_p, vs_ar, cs_t,
            vs_ab, cs_c, vs_ab, cs_m, vs_ab, cs_n, vs_ab, cs_ng, vs_ab, cs_p, vs_ab, cs_t,

            vs_e, cs_c, vs_e, cs_ch, vs_e, cs_m, vs_e, cs_n, vs_e, cs_ng,
            vs_e, cs_nh, vs_e, cs_p, vs_e, cs_t,
            vs_er, cs_c, vs_er, cs_ch, vs_er, cs_m, vs_er, cs_n, vs_er, cs_nh,
            vs_er, cs_p, vs_er, cs_t,

            vs_i, cs_c, vs_i, cs_ch, vs_i, cs_m, vs_i, cs_n, vs_i, cs_nh, vs_i, cs_p, vs_i, cs_t,

            vs_o, cs_c, vs_o, cs_m, vs_o, cs_n, vs_o, cs_ng, vs_o, cs_p, vs_o, cs_t,
            vs_or, cs_c, vs_or, cs_m, vs_or, cs_n, vs_or, cs_ng, vs_or, cs_p, vs_or, cs_t,
            vs_oh, cs_m, vs_oh, cs_n, vs_oh, cs_p, vs_oh, cs_t,

            vs_u, cs_c, vs_u, cs_m, vs_u, cs_n, vs_u, cs_ng, vs_u, cs_p, vs_u, cs_t,
            vs_uh, cs_c, vs_uh, cs_m, vs_uh, cs_n, vs_uh, cs_ng, vs_uh, cs_t,

            vs_y, cs_t,
            vs_ie, cs_c, vs_ie, cs_m, vs_ie, cs_n, vs_ie, cs_ng, vs_ie, cs_p, vs_ie, cs_t,
            vs_ier, cs_c, vs_ier, cs_m, vs_ier, cs_n, vs_ier, cs_ng, vs_ier, cs_p, vs_ier, cs_t,

            vs_oa, cs_c, vs_oa, cs_ch, vs_oa, cs_m, vs_oa, cs_n, vs_oa, cs_ng,
            vs_oa, cs_nh, vs_oa, cs_p, vs_oa, cs_t,
            vs_oab, cs_c, vs_oab, cs_m, vs_oab, cs_n, vs_oab, cs_ng, vs_oab, cs_t,

            vs_oe, cs_n, vs_oe, cs_t,

            vs_ua, cs_n, vs_ua, cs_ng, vs_ua, cs_t,
            vs_uar, cs_n, vs_uar, cs_ng, vs_uar, cs_t,

            vs_ue, cs_c, vs_ue, cs_ch, vs_ue, cs_n, vs_ue, cs_nh,
            vs_uer, cs_c, vs_uer, cs_ch, vs_uer, cs_n, vs_uer, cs_nh,

            vs_uo, cs_c, vs_uo, cs_m, vs_uo, cs_n, vs_uo, cs_ng, vs_uo, cs_p, vs_uo, cs_t,
            vs_uor, cs_c, vs_uor, cs_m, vs_uor, cs_n, vs_uor, cs_ng, vs_uor, cs_t,
            vs_uho, cs_c, vs_uho, cs_m, vs_uho, cs_n, vs_uho, cs_ng, vs_uho, cs_p, vs_uho, cs_t,
            vs_uhoh, cs_c, vs_uhoh, cs_m, vs_uhoh, cs_n, vs_uhoh, cs_ng, vs_uhoh, cs_p, vs_uhoh, cs_t,

            vs_uy, cs_c, vs_uy, cs_ch, vs_uy, cs_n, vs_uy, cs_nh, vs_uy, cs_p, vs_uy, cs_t,

            vs_ye, cs_m, vs_ye, cs_n, vs_ye, cs_ng, vs_ye, cs_p, vs_ye, cs_t,
            vs_yer, cs_m, vs_yer, cs_n, vs_yer, cs_ng, vs_yer, cs_t,

            vs_uye, cs_n, vs_uye, cs_t,
            vs_uyer, cs_n, vs_uyer, cs_t
    };

    // ==================================================================
    // 7. Phân loại phím — UkcMap (inputproc.cpp)
    // ==================================================================
    private static final int ukcVn = 0;
    private static final int ukcWordBreak = 1;
    private static final int ukcNonVn = 2;
    private static final int ukcReset = 3;

    /**
     * WordBreakSyms trong inputproc.cpp (đã loại bỏ ~ ` ^).
     */
    private static final String WORD_BREAK_SYMS =
            ",;:.\"'!? <>=+-*/\\_@#$%&(){}[]|";

    private static final int[] UKC_MAP = new int[256];

    // ==================================================================
    // 8. Phương thức gõ VNI — VniMethodMapping (inputproc.cpp)
    // ==================================================================
    private static final int vneRoofAll = 0, vneRoof_a = 1, vneRoof_e = 2, vneRoof_o = 3,
            vneHookAll = 4, vneHook_uo = 5, vneHook_u = 6, vneHook_o = 7, vneBowl = 8,
            vneDd = 9,
            vneTone0 = 10, vneTone1 = 11, vneTone2 = 12, vneTone3 = 13, vneTone4 = 14,
            vneTone5 = 15,
            vne_telex_w = 16,
            vneMapChar = 17,
            vneEscChar = 18,
            vneNormal = 19,
            vneCount = 20;

    private static final int[] VNI_KEY_ACTION = new int[256];

    // ==================================================================
    // 9. Ánh xạ ký tự ASCII/Latin-1 -> VnLexiName (IsoVnLexiMap)
    // ==================================================================
    private static final int[] ISO_VN_LEXI = new int[256];

    /** StdVnNoTone: từ một ký hiệu có dấu -> ký hiệu gốc (không dấu). */
    private static final int[] STD_VN_NO_TONE = new int[VN_TABLE_CHAR_COUNT];

    /** IsVnVowel[sym]. */
    private static final boolean[] IS_VN_VOWEL = new boolean[VNL_LAST_CHAR];

    /** Ký tự Latin-1 không phải nguyên âm cũng là ký tự tiếng Việt (AscVnLexiList). */
    private static final int[] UKC_VN_LATIN1 = {0xC0, 0xC1, 0xC2, 0xC8, 0xC9, 0xCA, 0xCC, 0xCD,
            0xD2, 0xD3, 0xD4, 0xD5, 0xD9, 0xDA, 0xDD, 0xE0, 0xE1, 0xE2, 0xE3, 0xE8,
            0xE9, 0xEA, 0xEC, 0xED, 0xF2, 0xF3, 0xF4, 0xF5, 0xF9, 0xFA, 0xFD};

    static {
        setupUkcMap();
        setupVniKeyMap();
        setupIsoVnLexiMap();
        setupStdVnNoTone();
        engineClassInit();
    }

    //--------------------------------------------------------------
    private static void setupUkcMap() {
        for (int c = 0; c <= 32; c++) {
            UKC_MAP[c] = ukcReset;
        }
        for (int c = 33; c < 256; c++) {
            UKC_MAP[c] = ukcNonVn;
        }
        for (int c = 'a'; c <= 'z'; c++) {
            UKC_MAP[c] = ukcVn;
        }
        for (int c = 'A'; c <= 'Z'; c++) {
            UKC_MAP[c] = ukcVn;
        }
        // Các ký tự Latin-1 cũng là ký tự tiếng Việt (AscVnLexiList)
        for (int value : UKC_VN_LATIN1) {
            UKC_MAP[value] = ukcVn;
        }
        UKC_MAP['j'] = ukcNonVn;
        UKC_MAP['J'] = ukcNonVn;
        UKC_MAP['f'] = ukcNonVn;
        UKC_MAP['F'] = ukcNonVn;
        UKC_MAP['w'] = ukcNonVn;
        UKC_MAP['W'] = ukcNonVn;

        for (int i = 0; i < WORD_BREAK_SYMS.length(); i++) {
            UKC_MAP[WORD_BREAK_SYMS.charAt(i)] = ukcWordBreak;
        }
    }

    //--------------------------------------------------------------
    private static void setupVniKeyMap() {
        // Bảng VniMethodMapping
        final int[] keys = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        final int[] actions = {vneTone0, vneTone1, vneTone2, vneTone3, vneTone4,
                vneTone5, vneRoofAll, vneHook_uo, vneBowl, vneDd};
        java.util.Arrays.fill(VNI_KEY_ACTION, vneNormal);
        for (int i = 0; i < keys.length; i++) {
            VNI_KEY_ACTION[keys[i]] = actions[i];
        }
    }

    //--------------------------------------------------------------
    private static void setupIsoVnLexiMap() {
        java.util.Arrays.fill(ISO_VN_LEXI, vnl_nonVnChar);
        // AZLexiUpper / AZLexiLower (inputproc.cpp). Lưu ý: các giá trị
        // VnLexiName KHÔNG liền nhau theo thứ tự a-z, nên phải liệt kê rõ.
        final int[] lower = {
                vnl_a, vnl_b, vnl_c, vnl_d, vnl_e, vnl_f, vnl_g, vnl_h, vnl_i, vnl_j,
                vnl_k, vnl_l, vnl_m, vnl_n, vnl_o, vnl_p, vnl_q, vnl_r, vnl_s, vnl_t,
                vnl_u, vnl_v, vnl_w, vnl_x, vnl_y, vnl_z};
        final int[] upper = {
                vnl_A, vnl_B, vnl_C, vnl_D, vnl_E, vnl_F, vnl_G, vnl_H, vnl_I, vnl_J,
                vnl_K, vnl_L, vnl_M, vnl_N, vnl_O, vnl_P, vnl_Q, vnl_R, vnl_S, vnl_T,
                vnl_U, vnl_V, vnl_W, vnl_X, vnl_Y, vnl_Z};
        for (int i = 0; i < 26; i++) {
            ISO_VN_LEXI['a' + i] = lower[i];
            ISO_VN_LEXI['A' + i] = upper[i];
        }
        // AscVnLexiList: ký tự Latin-1 có dấu
        ISO_VN_LEXI[0xC0] = vnl_A2;
        ISO_VN_LEXI[0xC1] = vnl_A1;
        ISO_VN_LEXI[0xC2] = vnl_Ar;
        ISO_VN_LEXI[0xC8] = vnl_E2;
        ISO_VN_LEXI[0xC9] = vnl_E1;
        ISO_VN_LEXI[0xCA] = vnl_Er;
        ISO_VN_LEXI[0xCC] = vnl_I2;
        ISO_VN_LEXI[0xCD] = vnl_I1;
        ISO_VN_LEXI[0xD2] = vnl_O2;
        ISO_VN_LEXI[0xD3] = vnl_O1;
        ISO_VN_LEXI[0xD4] = vnl_Or;
        ISO_VN_LEXI[0xD5] = vnl_O4;
        ISO_VN_LEXI[0xD9] = vnl_U2;
        ISO_VN_LEXI[0xDA] = vnl_U1;
        ISO_VN_LEXI[0xDD] = vnl_Y1;
        ISO_VN_LEXI[0xE0] = vnl_a2;
        ISO_VN_LEXI[0xE1] = vnl_a1;
        ISO_VN_LEXI[0xE2] = vnl_ar;
        ISO_VN_LEXI[0xE3] = vnl_a4;
        ISO_VN_LEXI[0xE8] = vnl_e2;
        ISO_VN_LEXI[0xE9] = vnl_e1;
        ISO_VN_LEXI[0xEA] = vnl_er;
        ISO_VN_LEXI[0xEC] = vnl_i2;
        ISO_VN_LEXI[0xED] = vnl_i1;
        ISO_VN_LEXI[0xF2] = vnl_o2;
        ISO_VN_LEXI[0xF3] = vnl_o1;
        ISO_VN_LEXI[0xF4] = vnl_or;
        ISO_VN_LEXI[0xF5] = vnl_o4;
        ISO_VN_LEXI[0xF9] = vnl_u2;
        ISO_VN_LEXI[0xFA] = vnl_u1;
        ISO_VN_LEXI[0xFD] = vnl_y1;
        // đ, Đ (trong Latin-1: 0xD0, 0xF0)
        ISO_VN_LEXI[0xD0] = vnl_DD;
        ISO_VN_LEXI[0xF0] = vnl_dd;
    }

    //--------------------------------------------------------------
    // StdVnNoTone trong data.cpp: mỗi họ nguyên âm chiếm 12 mục liên tiếp
    // (HOA/thường x 6 dấu) và ánh xạ về CHÍNH ký hiệu HOA hoặc thường của
    // họ đó — không phải về cùng một ký hiệu. Ví dụ họ 'a' là:
    //   A a Á á À à Ả ả Ã ã Ạ ạ  ->  A a A a A a A a A a A a
    private static void setupStdVnNoTone() {
        // Điểm bắt đầu 12 mục của từng họ nguyên âm (xem StdVnNoTone data.cpp).
        final int[] vowelFamilyStart = {
                vnl_A, vnl_Ar, vnl_Ab,     // a, â, ă
                vnl_E, vnl_Er,             // e, ê
                vnl_I,                     // i
                vnl_O, vnl_Or, vnl_Oh,     // o, ô, ơ
                vnl_U, vnl_Uh,             // u, ư
                vnl_Y                      // y
        };
        for (int base : vowelFamilyStart) {
            for (int j = 0; j < 12; j++) {
                STD_VN_NO_TONE[base + j] = base + (j & 1);
            }
        }
        // Các ký tự không phải nguyên âm (b, c, d, đ, f, g, ...) ánh xạ tới chính nó.
        boolean[] assigned = new boolean[VN_TABLE_CHAR_COUNT];
        for (int base : vowelFamilyStart) {
            for (int j = 0; j < 12; j++) {
                assigned[base + j] = true;
            }
        }
        for (int s = 0; s < VN_TABLE_CHAR_COUNT; s++) {
            if (!assigned[s]) {
                STD_VN_NO_TONE[s] = s;
            }
        }
    }

    //--------------------------------------------------------------
    // engineClassInit (ukengine.cpp): dựng IsVnVowel
    private static void engineClassInit() {
        // Chỉ a e i o u y (và các biến thể dấu) mới là nguyên âm
        java.util.Arrays.fill(IS_VN_VOWEL, false);
        int[] vowelBases = {vnl_A, vnl_Ar, vnl_Ab, vnl_E, vnl_Er, vnl_I,
                vnl_O, vnl_Or, vnl_Oh, vnl_U, vnl_Uh, vnl_Y};
        for (int base : vowelBases) {
            for (int j = 0; j < 12; j++) {
                IS_VN_VOWEL[base + j] = true;
            }
        }
    }

    /** Ký hiệu có phải là nguyên âm tiếng Việt (IsVnVowel). */
    private static boolean isVnVowel(int sym) {
        return sym >= 0 && sym < VNL_LAST_CHAR && IS_VN_VOWEL[sym];
    }

    /** IsoToVnLexi (inputproc.cpp). */
    private static int isoToVnLexi(int keyCode) {
        return (keyCode < 0 || keyCode >= 256) ? vnl_nonVnChar : ISO_VN_LEXI[keyCode];
    }

    /** vnToLower (ukengine.cpp) — giá trị chẵn -> +1 (chữ thường). */
    private static int vnToLower(int x) {
        if (x == vnl_nonVnChar) {
            return x;
        }
        if ((x & 0x01) == 0) { // even = chữ HOA
            return x + 1;
        }
        return x;
    }

    /** StdVnToUpper: giá trị lẻ -> -1 (chữ HOA). */
    private static int stdVnToUpper(int x) {
        if (x == vnl_nonVnChar) {
            return x;
        }
        if ((x & 0x01) != 0) {
            return x - 1;
        }
        return x;
    }

    //=================================================================
    // VowelSeqInfo helpers
    //=================================================================
    private static VowelSeqInfo vInfo(int vs) {
        return VSEQ[vs];
    }

    /** lookupVSeq (ukengine.cpp) — tìm chuỗi nguyên âm. */
    private static int lookupVSeq(int v1, int v2, int v3) {
        for (int i = 0; i < VSEQ.length; i++) {
            VowelSeqInfo info = VSEQ[i];
            if (info.v0 == v1 && info.v1 == v2 && info.v2 == v3) {
                return i;
            }
        }
        return vs_nil;
    }

    private static int lookupVSeq(int v1) {
        return lookupVSeq(v1, vnl_nonVnChar, vnl_nonVnChar);
    }

    private static int lookupVSeq(int v1, int v2) {
        return lookupVSeq(v1, v2, vnl_nonVnChar);
    }

    /** lookupCSeq (ukengine.cpp) — tìm chuỗi phụ âm. */
    private static int lookupCSeq(int c1, int c2, int c3) {
        for (int i = 0; i < CSEQ.length; i++) {
            ConSeqInfo info = CSEQ[i];
            if (info.c0 == c1 && info.c1 == c2 && info.c2 == c3) {
                return i;
            }
        }
        return cs_nil;
    }

    private static int lookupCSeq(int c1) {
        return lookupCSeq(c1, vnl_nonVnChar, vnl_nonVnChar);
    }

    private static int lookupCSeq(int c1, int c2) {
        return lookupCSeq(c1, c2, vnl_nonVnChar);
    }

    //=================================================================
    // Kiểm tra hợp lệ (ukengine.cpp)
    //=================================================================
    private static boolean isValidCV(int c, int v) {
        if (c == cs_nil || v == vs_nil) {
            return true;
        }
        VowelSeqInfo vSeqInfo = VSEQ[v];
        if ((c == cs_gi && vSeqInfo.v0 == vnl_i)
                || (c == cs_qu && vSeqInfo.v0 == vnl_u)) {
            return false; // gi không đi với i, qu không đi với u
        }
        if (c == cs_k) {
            int[] kVseq = {vs_e, vs_i, vs_y, vs_er, vs_eo, vs_eu,
                    vs_eru, vs_ia, vs_ie, vs_ier, vs_ieu, vs_ieru, vs_nil};
            int i = 0;
            while (kVseq[i] != vs_nil && kVseq[i] != v) {
                i++;
            }
            return kVseq[i] != vs_nil;
        }
        return true;
    }

    private static boolean isValidVC(int v, int c) {
        if (v == vs_nil || c == cs_nil) {
            return true;
        }
        VowelSeqInfo vSeqInfo = VSEQ[v];
        if (vSeqInfo.conSuffix == 0) {
            return false;
        }
        ConSeqInfo cSeqInfo = CSEQ[c];
        if (!cSeqInfo.suffix) {
            return false;
        }
        return vcPairExists(v, c);
    }

    private static boolean vcPairExists(int v, int c) {
        for (int i = 0; i < VCPAIRS.length; i += 2) {
            if (VCPAIRS[i] == v && VCPAIRS[i + 1] == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidCVC(int c1, int v, int c2) {
        if (v == vs_nil) {
            return (c1 == cs_nil || c2 != cs_nil);
        }
        if (c1 == cs_nil) {
            return isValidVC(v, c2);
        }
        if (c2 == cs_nil) {
            return isValidCV(c1, v);
        }
        boolean okCV = isValidCV(c1, v);
        boolean okVC = isValidVC(v, c2);
        if (okCV && okVC) {
            return true;
        }
        if (!okVC) {
            // quyn, quynh
            if (c1 == cs_qu && v == vs_y && (c2 == cs_n || c2 == cs_nh)) {
                return true;
            }
            // gieng, giềng
            if (c1 == cs_gi && (v == vs_e || v == vs_er) && (c2 == cs_n || c2 == cs_ng)) {
                return true;
            }
        }
        return false;
    }

    //=================================================================
    // WordInfo (struct WordInfo trong ukengine.h)
    //=================================================================
    private static final int vnw_nonVn = 0;
    private static final int vnw_empty = 1;
    private static final int vnw_c = 2;
    private static final int vnw_v = 3;
    private static final int vnw_cv = 4;
    private static final int vnw_vc = 5;
    private static final int vnw_cvc = 6;

    private static final class WordInfo {
        int form = vnw_empty;
        int c1Offset = -1, vOffset = -1, c2Offset = -1;
        int vseq = vs_nil;
        int cseq = cs_nil;
        int caps = 0;
        int tone = 0;
        int vnSym = vnl_nonVnChar;
        int keyCode = 0;

        void clear() {
            form = vnw_empty;
            c1Offset = -1;
            vOffset = -1;
            c2Offset = -1;
            vseq = vs_nil;
            cseq = cs_nil;
            caps = 0;
            tone = 0;
        }
    }

    //=================================================================
    // Tùy chọn — UnikeyOptions (keycons.h)
    //=================================================================
    public static final class Options {
        /** Cho phép đặt dấu móc/mũ/dấu thanh ở bất k đâu sau ký tự gốc. */
        public boolean freeMarking = true;
        /** Kiểu mới: "hoà", "khoẻ" (true) / kiểu cũ: "hòa", "khỏe" (false). */
        public boolean modernStyle = false;
        /** Bật kiểm tra chính tả (chỉ gõ dấu khi chuỗi nguyên âm hợp lệ). */
        public boolean spellCheckEnabled = true;
        /** Không dùng macro. */
        public boolean macroEnabled = false;
        /** Không tự khôi phục phím gõ khi từ không phải tiếng Việt. */
        public boolean autoNonVnRestore = false;
    }

    //=================================================================
    // Kết quả trả về của process()
    //=================================================================
    public static final class Result {
        /** Số ký tự cần xoá trước con trỏ (tính theo code point). */
        public final int backspaces;
        /** Chuỗi ký tự mới cần chèn vào vị trí con trỏ. */
        public final String out;
        /**
         * true nếu engine đã tiêu thụ phím này và người gọi phải tự chèn ký tự
         * (dùng khi {@link #out} rỗng).
         */
        public final boolean consumed;
        /**
         * true nếu phím này do engine quản lý trạng thái: người gọi phải tự
         * chèn ký tự gốc khi {@link #out} rỗng (ví dụ dấu cách, dấu câu,
         * chữ số đứng sau dấu cách). false nghĩa là phím không liên quan
         * (Enter, Tab, phím chức năng) và nên để hệ thống xử lý.
         */
        public final boolean handled;

        Result(int backspaces, String out, boolean consumed, boolean handled) {
            this.backspaces = backspaces;
            this.out = out;
            this.consumed = consumed;
            this.handled = handled;
        }
    }

    //=================================================================
    // Trạng thái engine
    //=================================================================
    private static final int MAX_UK_ENGINE = 128;

    private final Options options;
    private final WordInfo[] buffer = new WordInfo[MAX_UK_ENGINE];
    private final int bufferSize = MAX_UK_ENGINE;

    private int current = -1;
    private int changePos = 0;
    private int backs = 0;
    private boolean singleMode = false;
    private boolean toEscape = false;
    private boolean vietKey = true;

    public UnikeyEngine() {
        this(new Options());
    }

    public UnikeyEngine(Options options) {
        this.options = options;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = new WordInfo();
        }
    }

    public Options getOptions() {
        return options;
    }

    /** vietKey: bật/tắt chế độ gõ tiếng Việt. */
    public void setVietKey(boolean vietKey) {
        this.vietKey = vietKey;
    }

    //=================================================================
    // Hook "key wrapper" — chỉ dùng cho kiểm thử đối chiếu với UniKey gốc.
    //
    // Vì engine giữ trạng thái của cả một từ, đầu ra của nó có thể trùng với
    // ký tự người dùng vừa gõ (gõ 'v' 'i' -> engine trả về "vi"), nên không thể
    // phân biệt "ký tự do người dùng gõ" với "bản ghi do engine sinh ra".
    // Hook này cho phép bọc mỗi ký tự gõ vào một ký hiệu riêng trước khi đưa
    // vào engine, và gỡ ra khi ghi đầu ra — nhờ đó so sánh được chính xác.
    //
    // Mặc định null: mọi ký tự được xử lý nguyên bản.
    //=================================================================
    public interface KeyWrapper {
        /** Giải mã mã ký tự (có thể là ký hiệu đã bọc) về mã ký tự gốc. */
        int unwrapCode(int keyCode);
    }

    private KeyWrapper keyWrapper;

    public void setKeyWrapper(KeyWrapper wrapper) {
        this.keyWrapper = wrapper;
    }

    //=================================================================
    // Điểm vào chính — UkEngine::process (ukengine.cpp)
    //=================================================================

    /**
     * Xử lý một phím.
     *
     * @param keyCode mã ký tự ASCII/Unicode của phím (ví dụ 'a', '1', ' ')
     * @return kết quả cần áp dụng vào InputConnection
     */
    public Result process(int keyCode) {
        prepareBuffer();
        backs = 0;
        changePos = current + 1;

        UkKeyEvent ev = keyCodeToEvent(keyCode);

        int ret;
        if (!toEscape) {
            ret = dispatch(ev);
        } else {
            toEscape = false;
            if (current < 0 || ev.evType == vneNormal || ev.evType == vneEscChar) {
                ret = processAppend(ev);
            } else {
                current--;
                processAppend(ev);
                markChange(current);
                ret = 1;
            }
        }

        if (vietKey && current >= 0 && buffer[current].form == vnw_nonVn
                && ev.chType == ukcVn
                && (!options.spellCheckEnabled || singleMode)) {
            // Kiểm tra chính tả thất bại, nhưng vì đang ở chế độ không kiểm tra
            // chính tả, ta coi ký tự mới là bắt đầu của một từ mới.
            ret = processNoSpellCheck(ev);
        }

        if (ret == 0) {
            // Không ghi ra buffer. Với phím bình thường/ký tự ngoài tiếng Việt,
            // engine vẫn quản lý trạng thái nên người gọi phải tự chèn ký tự gốc.
            boolean handled = ev.chType != ukcReset;
            return new Result(0, null, false, handled);
        }

        if (keyWrapper != null) {
            for (int i = changePos; i <= current; i++) {
                WordInfo w = buffer[i];
                w.vnSym = keyWrapper.unwrapCode(w.vnSym);
                w.keyCode = keyWrapper.unwrapCode(w.keyCode);
            }
        }

        // writeOutput: từ changePos đến current
        StringBuilder sb = new StringBuilder();
        for (int i = changePos; i <= current; i++) {
            appendOutputChar(sb, buffer[i]);
        }
        return new Result(backs, sb.toString(), true, true);
    }

    /**
     * Xử lý phím Backspace — UkEngine::processBackspace (ukengine.cpp).
     *
     * @return kết quả; nếu {@code handled == false} thì ứng dụng tự xử lý
     *         phím Backspace (engine đã đồng bộ trạng thái).
     */
    public Result processBackspace() {
        if (!vietKey || current < 0) {
            return new Result(0, null, false, false);
        }

        backs = 0;
        changePos = current + 1;
        markChange(current);

        if (current == 0
                || buffer[current].form == vnw_empty
                || buffer[current].form == vnw_nonVn
                || buffer[current].form == vnw_c
                || buffer[current - 1].form == vnw_c
                || buffer[current - 1].form == vnw_cvc
                || buffer[current - 1].form == vnw_vc) {
            current--;
            // UkEngine::processBackspace: return (backs > 1)
            return new Result(backs, null, backs > 1, false);
        }

        int vEnd = current - buffer[current].vOffset;
        int vs = buffer[vEnd].vseq;
        int vStart = vEnd - (vseqLen(vs) - 1);
        int newVs = buffer[current - 1].vseq;
        int curTonePos = vStart + getTonePosition(vs, vEnd == current);
        int newTonePos = vStart + getTonePosition(newVs, true);
        int tone = buffer[curTonePos].tone;

        if (tone == 0 || curTonePos == newTonePos
                || (curTonePos == current && buffer[current].tone != 0)) {
            current--;
            // UkEngine::processBackspace: return (backs > 1)
            return new Result(backs, null, backs > 1, false);
        }

        markChange(newTonePos);
        buffer[newTonePos].tone = tone;
        markChange(curTonePos);
        buffer[curTonePos].tone = 0;
        current--;

        StringBuilder sb = new StringBuilder();
        for (int i = changePos; i <= current; i++) {
            appendOutputChar(sb, buffer[i]);
        }
        return new Result(backs, sb.toString(), true, true);
    }

    /** UkEngine::reset(). */
    public void reset() {
        current = -1;
        singleMode = false;
        toEscape = false;
    }

    /**
     * UkEngine::resetKeyBuf().
     *
     * <p>Bản Java không dựng lại buffer phím gõ (chỉ dùng cho
     * {@code autoNonVnRestore}, mặc định tắt), nên chỉ cần trả về trạng thái
     * tương đương khi bộ đệm phím rỗng.
     */
    public void resetKeyBuf() {
        toEscape = false;
    }

    /** UkEngine::setSingleMode(). */
    public void setSingleMode() {
        singleMode = true;
    }

    //=================================================================
    // UkKeyEvent + dispatch (inputproc.cpp / ukengine.cpp)
    //=================================================================
    private static final class UkKeyEvent {
        int evType;
        int chType;
        int vnSym;
        int keyCode;
        int tone;
    }

    /** UkInputProcessor::keyCodeToEvent (inputproc.cpp). */
    private UkKeyEvent keyCodeToEvent(int keyCode) {
        if (keyWrapper != null) {
            keyCode = keyWrapper.unwrapCode(keyCode);
        }
        UkKeyEvent ev = new UkKeyEvent();
        ev.keyCode = keyCode;
        ev.tone = 0;
        if (keyCode > 255) {
            ev.evType = vneNormal;
            ev.vnSym = vnl_nonVnChar;
            ev.chType = ukcNonVn;
        } else {
            ev.chType = UKC_MAP[keyCode];
            ev.evType = VNI_KEY_ACTION[keyCode];
            if (ev.evType >= vneTone0 && ev.evType <= vneTone5) {
                ev.tone = ev.evType - vneTone0;
            }
            if (ev.evType >= vneCount) {
                ev.chType = ukcVn;
                ev.vnSym = ev.evType - vneCount;
                ev.evType = vneMapChar;
            } else {
                ev.vnSym = isoToVnLexi(keyCode);
            }
        }
        return ev;
    }

    /** UkKeyProcList (ukengine.cpp). */
    private int dispatch(UkKeyEvent ev) {
        switch (ev.evType) {
            case vneRoofAll:
            case vneRoof_a:
            case vneRoof_e:
            case vneRoof_o:
                return processRoof(ev);
            case vneHookAll:
            case vneHook_uo:
            case vneHook_u:
            case vneHook_o:
            case vneBowl:
                return processHook(ev);
            case vneDd:
                return processDd(ev);
            case vneTone0:
            case vneTone1:
            case vneTone2:
            case vneTone3:
            case vneTone4:
            case vneTone5:
                return processTone(ev);
            case vne_telex_w:
                // Telex 'w' -> coi như Hook-All
                ev.evType = vneHookAll;
                return processHook(ev);
            case vneMapChar:
                return processMapChar(ev);
            case vneEscChar:
                return processEscChar(ev);
            default:
                return processAppend(ev);
        }
    }

    //=================================================================
    // processRoof — dấu mũ â ê ô (ukengine.cpp)
    //=================================================================
    private int processRoof(UkKeyEvent ev) {
        if (!vietKey || current < 0 || buffer[current].vOffset < 0) {
            return processAppend(ev);
        }

        int target;
        switch (ev.evType) {
            case vneRoof_a:
                target = vnl_ar;
                break;
            case vneRoof_e:
                target = vnl_er;
                break;
            case vneRoof_o:
                target = vnl_or;
                break;
            default:
                target = vnl_nonVnChar;
        }

        int vs, newVs;
        int i, vStart, vEnd;
        int curTonePos, newTonePos, tone;
        int localChangePos;
        boolean roofRemoved = false;

        vEnd = current - buffer[current].vOffset;
        vs = buffer[vEnd].vseq;
        vStart = vEnd - (vseqLen(vs) - 1);
        curTonePos = vStart + getTonePosition(vs, vEnd == current);
        tone = buffer[curTonePos].tone;

        boolean doubleChangeUO = false;
        if (vs == vs_uho || vs == vs_uhoh || vs == vs_uhoi || vs == vs_uhohi) {
            // u+o+ -> uô, u+o -> uô, u+o+i -> uôi, u+oi -> uôi
            newVs = lookupVSeq(vnl_u, vnl_or, vInfo(vs).v2);
            doubleChangeUO = true;
        } else {
            newVs = vInfo(vs).withRoof;
        }

        VowelSeqInfo pInfo;
        if (newVs == vs_nil) {
            if (vInfo(vs).roofPos == -1) {
                return processAppend(ev); // không áp dụng được dấu mũ
            }
            // Đã có dấu mũ -> bỏ dấu mũ
            int curCh = buffer[vStart + vInfo(vs).roofPos].vnSym;
            if (target != vnl_nonVnChar && curCh != target) {
                return processAppend(ev);
            }
            int newCh = (curCh == vnl_ar) ? vnl_a : ((curCh == vnl_er) ? vnl_e : vnl_o);
            localChangePos = vStart + vInfo(vs).roofPos;

            if (!options.freeMarking && localChangePos != current) {
                return processAppend(ev);
            }

            markChange(localChangePos);
            buffer[localChangePos].vnSym = newCh;

            if (vInfo(vs).len == 3) {
                newVs = lookupVSeq(buffer[vStart].vnSym, buffer[vStart + 1].vnSym,
                        buffer[vStart + 2].vnSym);
            } else if (vInfo(vs).len == 2) {
                newVs = lookupVSeq(buffer[vStart].vnSym, buffer[vStart + 1].vnSym);
            } else {
                newVs = lookupVSeq(buffer[vStart].vnSym);
            }
            pInfo = vInfo(newVs);
            roofRemoved = true;
        } else {
            pInfo = vInfo(newVs);
            if (target != vnl_nonVnChar && pInfo.v(pInfo.roofPos) != target) {
                return processAppend(ev);
            }

            // Kiểm tra hợp lệ của VC và CV mới
            int c1 = cs_nil;
            int c2 = cs_nil;
            if (buffer[current].c1Offset != -1) {
                c1 = buffer[current - buffer[current].c1Offset].cseq;
            }
            if (buffer[current].c2Offset != -1) {
                c2 = buffer[current - buffer[current].c2Offset].cseq;
            }
            if (!isValidCVC(c1, newVs, c2)) {
                return processAppend(ev);
            }

            if (doubleChangeUO) {
                localChangePos = vStart;
            } else {
                localChangePos = vStart + pInfo.roofPos;
            }
            if (!options.freeMarking && localChangePos != current) {
                return processAppend(ev);
            }
            markChange(localChangePos);
            if (doubleChangeUO) {
                buffer[vStart].vnSym = vnl_u;
                buffer[vStart + 1].vnSym = vnl_or;
            } else {
                buffer[localChangePos].vnSym = pInfo.v(pInfo.roofPos);
            }
        }

        for (i = 0; i < pInfo.len; i++) { // cập nhật các chuỗi con
            buffer[vStart + i].vseq = pInfo.sub(i);
        }

        // Kiểm tra dời dấu thanh
        newTonePos = vStart + getTonePosition(newVs, vEnd == current);
        if (curTonePos != newTonePos && tone != 0) {
            markChange(newTonePos);
            buffer[newTonePos].tone = tone;
            markChange(curTonePos);
            buffer[curTonePos].tone = 0;
        }

        if (roofRemoved) {
            singleMode = false;
            processAppend(ev);
        }

        return 1;
    }

    //=================================================================
    // processHook — dấu móc ơ ư và dấu trăng  (ukengine.cpp)
    //=================================================================
    private int processHook(UkKeyEvent ev) {
        if (!vietKey || current < 0 || buffer[current].vOffset < 0) {
            return processAppend(ev);
        }

        int vs, newVs;
        int i, vStart, vEnd;
        int curTonePos, newTonePos, tone;
        int localChangePos = -1;
        boolean hookRemoved = false;
        VowelSeqInfo pInfo;

        vEnd = current - buffer[current].vOffset;
        vs = buffer[vEnd].vseq;

        if (vseqLen(vs) > 1
                && ev.evType != vneBowl
                && (vInfo(vs).v0 == vnl_u || vInfo(vs).v0 == vnl_uh)
                && (vInfo(vs).v1 == vnl_o || vInfo(vs).v1 == vnl_oh
                || vInfo(vs).v1 == vnl_or)) {
            return processHookWithUO(ev);
        }

        vStart = vEnd - (vseqLen(vs) - 1);
        curTonePos = vStart + getTonePosition(vs, vEnd == current);
        tone = buffer[curTonePos].tone;

        newVs = vInfo(vs).withHook;
        if (newVs == vs_nil) {
            if (vInfo(vs).hookPos == -1) {
                return processAppend(ev); // không áp dụng được dấu móc
            }
            // Đã có dấu móc -> bỏ dấu móc
            int curCh = buffer[vStart + vInfo(vs).hookPos].vnSym;
            int newCh = (curCh == vnl_ab) ? vnl_a : ((curCh == vnl_uh) ? vnl_u : vnl_o);
            localChangePos = vStart + vInfo(vs).hookPos;
            if (!options.freeMarking && localChangePos != current) {
                return processAppend(ev);
            }

            switch (ev.evType) {
                case vneHook_u:
                    if (curCh != vnl_uh) {
                        return processAppend(ev);
                    }
                    break;
                case vneHook_o:
                    if (curCh != vnl_oh) {
                        return processAppend(ev);
                    }
                    break;
                case vneBowl:
                    if (curCh != vnl_ab) {
                        return processAppend(ev);
                    }
                    break;
                default:
                    if (ev.evType == vneHook_uo && curCh == vnl_ab) {
                        return processAppend(ev);
                    }
            }

            markChange(localChangePos);
            buffer[localChangePos].vnSym = newCh;

            if (vInfo(vs).len == 3) {
                newVs = lookupVSeq(buffer[vStart].vnSym, buffer[vStart + 1].vnSym,
                        buffer[vStart + 2].vnSym);
            } else if (vInfo(vs).len == 2) {
                newVs = lookupVSeq(buffer[vStart].vnSym, buffer[vStart + 1].vnSym);
            } else {
                newVs = lookupVSeq(buffer[vStart].vnSym);
            }
            pInfo = vInfo(newVs);
            hookRemoved = true;
        } else {
            pInfo = vInfo(newVs);

            switch (ev.evType) {
                case vneHook_u:
                    if (pInfo.v(pInfo.hookPos) != vnl_uh) {
                        return processAppend(ev);
                    }
                    break;
                case vneHook_o:
                    if (pInfo.v(pInfo.hookPos) != vnl_oh) {
                        return processAppend(ev);
                    }
                    break;
                case vneBowl:
                    if (pInfo.v(pInfo.hookPos) != vnl_ab) {
                        return processAppend(ev);
                    }
                    break;
                default: // vneHook_uo, vneHookAll
                    if (ev.evType == vneHook_uo && pInfo.v(pInfo.hookPos) == vnl_ab) {
                        return processAppend(ev);
                    }
            }

            int c1 = cs_nil;
            int c2 = cs_nil;
            if (buffer[current].c1Offset != -1) {
                c1 = buffer[current - buffer[current].c1Offset].cseq;
            }
            if (buffer[current].c2Offset != -1) {
                c2 = buffer[current - buffer[current].c2Offset].cseq;
            }
            if (!isValidCVC(c1, newVs, c2)) {
                return processAppend(ev);
            }

            localChangePos = vStart + pInfo.hookPos;
            if (!options.freeMarking && localChangePos != current) {
                return processAppend(ev);
            }

            markChange(localChangePos);
            buffer[localChangePos].vnSym = pInfo.v(pInfo.hookPos);
        }

        for (i = 0; i < pInfo.len; i++) { // cập nhật các chuỗi con
            buffer[vStart + i].vseq = pInfo.sub(i);
        }

        newTonePos = vStart + getTonePosition(newVs, vEnd == current);
        if (curTonePos != newTonePos && tone != 0) {
            markChange(newTonePos);
            buffer[newTonePos].tone = tone;
            markChange(curTonePos);
            buffer[curTonePos].tone = 0;
        }

        if (hookRemoved) {
            singleMode = false;
            processAppend(ev);
        }

        return 1;
    }

    /** processHookWithUO (ukengine.cpp) — chỉ được gọi từ processHook. */
    private int processHookWithUO(UkKeyEvent ev) {
        int vs, newVs;
        int i, vStart, vEnd;
        int curTonePos, newTonePos, tone;
        boolean hookRemoved = false;

        if (!options.freeMarking && buffer[current].vOffset != 0) {
            return processAppend(ev);
        }

        vEnd = current - buffer[current].vOffset;
        vs = buffer[vEnd].vseq;
        vStart = vEnd - (vseqLen(vs) - 1);
        curTonePos = vStart + getTonePosition(vs, vEnd == current);
        tone = buffer[curTonePos].tone;

        switch (ev.evType) {
            case vneHook_u:
                if (vInfo(vs).v0 == vnl_u) {
                    newVs = vInfo(vs).withHook;
                    markChange(vStart);
                    buffer[vStart].vnSym = vnl_uh;
                } else { // v[0] = vnl_uh -> uo
                    newVs = lookupVSeq(vnl_u, vnl_o, vInfo(vs).v2);
                    markChange(vStart);
                    buffer[vStart].vnSym = vnl_u;
                    buffer[vStart + 1].vnSym = vnl_o;
                    hookRemoved = true;
                }
                break;
            case vneHook_o:
                if (vInfo(vs).v1 == vnl_o || vInfo(vs).v1 == vnl_or) {
                    if (vEnd == current && vseqLen(vs) == 2
                            && buffer[current].form == vnw_cv
                            && buffer[current - 2].cseq == cs_th) {
                        // o|ô -> o+
                        newVs = vInfo(vs).withHook;
                        markChange(vStart + 1);
                        buffer[vStart + 1].vnSym = vnl_oh;
                    } else {
                        newVs = lookupVSeq(vnl_uh, vnl_oh, vInfo(vs).v2);
                        if (vInfo(vs).v0 == vnl_u) {
                            markChange(vStart);
                            buffer[vStart].vnSym = vnl_uh;
                            buffer[vStart + 1].vnSym = vnl_oh;
                        } else {
                            markChange(vStart + 1);
                            buffer[vStart + 1].vnSym = vnl_oh;
                        }
                    }
                } else { // v[1] = vnl_oh -> uo
                    newVs = lookupVSeq(vnl_u, vnl_o, vInfo(vs).v2);
                    if (vInfo(vs).v0 == vnl_uh) {
                        markChange(vStart);
                        buffer[vStart].vnSym = vnl_u;
                        buffer[vStart + 1].vnSym = vnl_o;
                    } else {
                        markChange(vStart + 1);
                        buffer[vStart + 1].vnSym = vnl_o;
                    }
                    hookRemoved = true;
                }
                break;
            default: // vneHookAll, vneHookUO
                if (vInfo(vs).v0 == vnl_u) {
                    if (vInfo(vs).v1 == vnl_o || vInfo(vs).v1 == vnl_or) {
                        // uo -> uô nếu đứng sau "th"
                        if ((vs == vs_uo || vs == vs_uor) && vEnd == current
                                && buffer[current].form == vnw_cv
                                && buffer[current - 2].cseq == cs_th) {
                            newVs = vs_uoh;
                            markChange(vStart + 1);
                            buffer[vStart + 1].vnSym = vnl_oh;
                        } else {
                            // uo -> ưo+
                            newVs = vInfo(vs).withHook;
                            markChange(vStart);
                            buffer[vStart].vnSym = vnl_uh;
                            newVs = vInfo(newVs).withHook;
                            buffer[vStart + 1].vnSym = vnl_oh;
                        }
                    } else { // uo+ -> ưo+
                        newVs = vInfo(vs).withHook;
                        markChange(vStart);
                        buffer[vStart].vnSym = vnl_uh;
                    }
                } else { // v[0] == vnl_uh
                    if (vInfo(vs).v1 == vnl_o) { // ưo -> ưo+
                        newVs = vInfo(vs).withHook;
                        markChange(vStart + 1);
                        buffer[vStart + 1].vnSym = vnl_oh;
                    } else { // v[1] == vnl_oh, ưo+ -> uo
                        newVs = lookupVSeq(vnl_u, vnl_o, vInfo(vs).v2);
                        markChange(vStart);
                        buffer[vStart].vnSym = vnl_u;
                        buffer[vStart + 1].vnSym = vnl_o;
                        hookRemoved = true;
                    }
                }
                break;
        }

        VowelSeqInfo p = vInfo(newVs);
        for (i = 0; i < p.len; i++) { // cập nhật các chuỗi con
            buffer[vStart + i].vseq = p.sub(i);
        }

        newTonePos = vStart + getTonePosition(newVs, vEnd == current);
        if (curTonePos != newTonePos && tone != 0) {
            markChange(newTonePos);
            buffer[newTonePos].tone = tone;
            markChange(curTonePos);
            buffer[curTonePos].tone = 0;
        }

        if (hookRemoved) {
            singleMode = false;
            processAppend(ev);
        }

        return 1;
    }

    //=================================================================
    // getTonePosition (ukengine.cpp) — vị trí đặt dấu thanh
    //=================================================================
    private int getTonePosition(int vs, boolean terminated) {
        VowelSeqInfo info = vInfo(vs);
        if (info.len == 1) {
            return 0;
        }
        if (info.roofPos != -1) {
            return info.roofPos;
        }
        if (info.hookPos != -1) {
            if (vs == vs_uhoh || vs == vs_uhohi || vs == vs_uhohu) {
                // ưo+, ưo+u, ưo+i
                return 1;
            }
            return info.hookPos;
        }
        if (info.len == 3) {
            return 1;
        }
        if (options.modernStyle
                && (vs == vs_oa || vs == vs_oe || vs == vs_uy)) {
            return 1;
        }
        return terminated ? 0 : 1;
    }

    //=================================================================
    // processTone (ukengine.cpp)
    //=================================================================
    private int processTone(UkKeyEvent ev) {
        if (current < 0 || !vietKey) {
            return processAppend(ev);
        }

        if (buffer[current].form == vnw_c
                && (buffer[current].cseq == cs_gi || buffer[current].cseq == cs_gin)) {
            int p = (buffer[current].cseq == cs_gi) ? current : current - 1;
            if (buffer[p].tone == 0 && ev.tone == 0) {
                return processAppend(ev);
            }
            markChange(p);
            if (buffer[p].tone == ev.tone) {
                buffer[p].tone = 0;
                singleMode = false;
                processAppend(ev);
                return 1;
            }
            buffer[p].tone = ev.tone;
            return 1;
        }

        if (buffer[current].vOffset < 0) {
            return processAppend(ev);
        }

        int vEnd = current - buffer[current].vOffset;
        int vs = buffer[vEnd].vseq;
        VowelSeqInfo info = vInfo(vs);
        if (options.spellCheckEnabled && !options.freeMarking && info.complete == 0) {
            return processAppend(ev);
        }

        if (buffer[current].form == vnw_vc || buffer[current].form == vnw_cvc) {
            int cs = buffer[current].cseq;
            if ((cs == cs_c || cs == cs_ch || cs == cs_p || cs == cs_t)
                    && (ev.tone == 2 || ev.tone == 3 || ev.tone == 4)) {
                return processAppend(ev); // c, ch, p, t không cho phép ` ? ~
            }
        }

        int toneOffset = getTonePosition(vs, vEnd == current);
        int tonePos = vEnd - (info.len - 1) + toneOffset;
        if (buffer[tonePos].tone == 0 && ev.tone == 0) {
            return processAppend(ev);
        }

        if (buffer[tonePos].tone == ev.tone) {
            markChange(tonePos);
            buffer[tonePos].tone = 0;
            singleMode = false;
            processAppend(ev);
            return 1;
        }

        markChange(tonePos);
        buffer[tonePos].tone = ev.tone;
        return 1;
    }

    //=================================================================
    // processDd (ukengine.cpp) — chữ Đ
    //=================================================================
    private int processDd(UkKeyEvent ev) {
        if (!vietKey || current < 0) {
            return processAppend(ev);
        }

        // dd cũng được phép trong chuỗi không phải tiếng Việt (viết tắt),
        // nhưng chỉ khi ký tự trước không phải nguyên âm
        if (buffer[current].form == vnw_nonVn
                && buffer[current].vnSym == vnl_d
                && (buffer[current - 1].vnSym == vnl_nonVnChar
                || !isVnVowel(buffer[current - 1].vnSym))) {
            singleMode = true;
            int pos = current;
            markChange(pos);
            buffer[pos].cseq = cs_dd;
            buffer[pos].vnSym = vnl_dd;
            buffer[pos].form = vnw_c;
            buffer[pos].c1Offset = 0;
            buffer[pos].c2Offset = -1;
            buffer[pos].vOffset = -1;
            return 1;
        }

        if (buffer[current].c1Offset < 0) {
            return processAppend(ev);
        }

        int pos = current - buffer[current].c1Offset;
        if (!options.freeMarking && pos != current) {
            return processAppend(ev);
        }

        if (buffer[pos].cseq == cs_d) {
            markChange(pos);
            buffer[pos].cseq = cs_dd;
            buffer[pos].vnSym = vnl_dd;
            return 1;
        }

        if (buffer[pos].cseq == cs_dd) {
            // bỏ dd
            markChange(pos);
            buffer[pos].cseq = cs_d;
            buffer[pos].vnSym = vnl_d;
            singleMode = false;
            processAppend(ev);
            return 1;
        }

        return processAppend(ev);
    }

    //=================================================================
    // processMapChar (ukengine.cpp) — dùng cho Telex W (không dùng ở VNI,
    // giữ lại cho đầy đủ cấu trúc của engine)
    //=================================================================
    private int processMapChar(UkKeyEvent ev) {
        int ret = processAppend(ev);
        if (!vietKey) {
            return ret;
        }
        if (current >= 0 && buffer[current].form != vnw_empty
                && buffer[current].form != vnw_nonVn) {
            return 1;
        }
        if (current < 0) {
            return 0;
        }

        current--;
        WordInfo entry = buffer[current];

        boolean undo = false;
        if (entry.form != vnw_empty && entry.form != vnw_nonVn) {
            int prevSym = entry.vnSym;
            if (entry.caps == 1) {
                prevSym = prevSym - 1;
            }
            if (prevSym == ev.vnSym) {
                if (entry.form != vnw_c) {
                    int vStart, vEnd, curTonePos, newTonePos, tone;
                    vEnd = current - entry.vOffset;
                    int vs = buffer[vEnd].vseq;
                    vStart = vEnd - vseqLen(vs) + 1;
                    curTonePos = vStart + getTonePosition(vs, vEnd == current);
                    tone = buffer[curTonePos].tone;
                    markChange(current);
                    current--;

                    if (tone != 0 && current >= 0
                            && (buffer[current].form == vnw_v || buffer[current].form == vnw_cv)) {
                        int newVs = buffer[current].vseq;
                        newTonePos = vStart + getTonePosition(newVs, true);
                        if (newTonePos != curTonePos) {
                            markChange(newTonePos);
                            buffer[newTonePos].tone = tone;
                            markChange(curTonePos);
                            buffer[curTonePos].tone = 0;
                        }
                    }
                } else {
                    markChange(current);
                    current--;
                }
                undo = true;
            }
        }

        ev.evType = vneNormal;
        ev.chType = charTypeOf(ev.keyCode);
        ev.vnSym = isoToVnLexi(ev.keyCode);
        ret = processAppend(ev);
        if (undo) {
            singleMode = false;
            return 1;
        }
        return ret;
    }

    //=================================================================
    // processEscChar (ukengine.cpp)
    //=================================================================
    private int processEscChar(UkKeyEvent ev) {
        if (vietKey && current >= 0 && buffer[current].form != vnw_empty
                && buffer[current].form != vnw_nonVn) {
            toEscape = true;
        }
        return processAppend(ev);
    }

    //=================================================================
    // processNoSpellCheck (ukengine.cpp)
    //=================================================================
    private int processNoSpellCheck(UkKeyEvent ev) {
        WordInfo entry = buffer[current];
        if (isVnVowel(entry.vnSym)) {
            entry.form = vnw_v;
            entry.vOffset = 0;
            entry.vseq = lookupVSeq(entry.vnSym);
            entry.c1Offset = -1;
            entry.c2Offset = -1;
        } else {
            entry.form = vnw_c;
            entry.c1Offset = 0;
            entry.c2Offset = -1;
            entry.vOffset = -1;
            entry.cseq = lookupCSeq(entry.vnSym);
        }

        if (ev.evType == vneNormal
                && ((entry.keyCode >= 'a' && entry.keyCode <= 'z')
                || (entry.keyCode >= 'A' && entry.keyCode <= 'Z'))) {
            return 0;
        }
        markChange(current);
        return 1;
    }

    //=================================================================
    // processAppend (ukengine.cpp)
    //=================================================================
    private int processAppend(UkKeyEvent ev) {
        switch (ev.chType) {
            case ukcReset:
                reset();
                return 0;

            case ukcWordBreak:
                singleMode = false;
                return processWordEnd(ev);

            case ukcNonVn: {
                current++;
                WordInfo entry = buffer[current];
                entry.form = vnw_nonVn;
                entry.c1Offset = -1;
                entry.c2Offset = -1;
                entry.vOffset = -1;
                entry.vseq = vs_nil;
                entry.cseq = cs_nil;
                entry.keyCode = ev.keyCode;
                entry.vnSym = vnToLower(ev.vnSym);
                entry.tone = 0;
                entry.caps = (entry.vnSym != ev.vnSym) ? 1 : 0;
                markChange(current);
                return 1;
            }

            case ukcVn: {
                if (isVnVowel(ev.vnSym)) {
                    int v = STD_VN_NO_TONE[vnToLower(ev.vnSym)];
                    if (current >= 0 && buffer[current].form == vnw_c
                            && ((buffer[current].cseq == cs_q && v == vnl_u)
                            || (buffer[current].cseq == cs_g && v == vnl_i))) {
                        // u sau q, i sau g được xử lý như phụ âm
                        return appendConsonnant(ev);
                    }
                    return appendVowel(ev);
                }
                return appendConsonnant(ev);
            }

            default:
                return 0;
        }
    }

    //=================================================================
    // appendVowel (ukengine.cpp)
    //=================================================================
    private int appendVowel(UkKeyEvent ev) {
        current++;
        WordInfo entry = buffer[current];

        int lowerSym = vnToLower(ev.vnSym);
        int canSym = STD_VN_NO_TONE[lowerSym];

        entry.vnSym = canSym;
        entry.caps = (lowerSym != ev.vnSym) ? 1 : 0;
        entry.tone = (lowerSym - canSym) / 2;
        entry.keyCode = ev.keyCode;

        if (current == 0 || !vietKey) {
            entry.form = vnw_v;
            entry.c1Offset = -1;
            entry.c2Offset = -1;
            entry.vOffset = 0;
            entry.vseq = lookupVSeq(canSym);
            markChange(current);
            return 1;
        }

        WordInfo prev = buffer[current - 1];
        int vs, newVs;
        int prevTonePos;
        int tone, newTone, tonePos, newTonePos;

        switch (prev.form) {
            case vnw_empty:
                entry.form = vnw_v;
                entry.c1Offset = -1;
                entry.c2Offset = -1;
                entry.vOffset = 0;
                entry.vseq = lookupVSeq(canSym);
                break;

            case vnw_nonVn:
            case vnw_cvc:
            case vnw_vc:
                entry.form = vnw_nonVn;
                entry.c1Offset = -1;
                entry.c2Offset = -1;
                entry.vOffset = -1;
                break;

            case vnw_v:
            case vnw_cv:
                vs = prev.vseq;
                prevTonePos = (current - 1) - (vseqLen(vs) - 1)
                        + getTonePosition(vs, true);
                tone = buffer[prevTonePos].tone;

                if (lowerSym != canSym && tone != 0) {
                    // ký tự mới đã có dấu, nhưng trước đó cũng đã có dấu
                    newVs = vs_nil;
                } else if (vseqLen(vs) == 3) {
                    newVs = vs_nil;
                } else if (vseqLen(vs) == 2) {
                    newVs = lookupVSeq(vInfo(vs).v0, vInfo(vs).v1, canSym);
                } else {
                    newVs = lookupVSeq(vInfo(vs).v0, canSym);
                }

                if (newVs != vs_nil && prev.form == vnw_cv) {
                    int cs = buffer[current - 1 - prev.c1Offset].cseq;
                    if (!isValidCV(cs, newVs)) {
                        newVs = vs_nil;
                    }
                }

                if (newVs == vs_nil) {
                    entry.form = vnw_nonVn;
                    entry.c1Offset = -1;
                    entry.c2Offset = -1;
                    entry.vOffset = -1;
                    break;
                }

                entry.form = prev.form;
                entry.c1Offset = (prev.form == vnw_cv) ? prev.c1Offset + 1 : -1;
                entry.c2Offset = -1;
                entry.vOffset = 0;
                entry.vseq = newVs;
                entry.tone = 0;

                newTone = (lowerSym - canSym) / 2;
                if (tone == 0) {
                    if (newTone != 0) {
                        tone = newTone;
                        tonePos = getTonePosition(newVs, true)
                                + ((current - 1) - vseqLen(vs) + 1);
                        markChange(tonePos);
                        buffer[tonePos].tone = tone;
                        return 1;
                    }
                } else {
                    newTonePos = getTonePosition(newVs, true)
                            + ((current - 1) - vseqLen(vs) + 1);
                    if (newTonePos != prevTonePos) {
                        markChange(prevTonePos);
                        buffer[prevTonePos].tone = 0;
                        markChange(newTonePos);
                        if (newTone != 0) {
                            tone = newTone;
                        }
                        buffer[newTonePos].tone = tone;
                        return 1;
                    }
                    if (newTone != 0 && newTone != tone) {
                        tone = newTone;
                        markChange(prevTonePos);
                        buffer[prevTonePos].tone = tone;
                        return 1;
                    }
                }
                break;

            case vnw_c:
                newVs = lookupVSeq(canSym);
                int cs = prev.cseq;
                if (!isValidCV(cs, newVs)) {
                    entry.form = vnw_nonVn;
                    entry.c1Offset = -1;
                    entry.c2Offset = -1;
                    entry.vOffset = -1;
                    break;
                }

                entry.form = vnw_cv;
                entry.c1Offset = 1;
                entry.c2Offset = -1;
                entry.vOffset = 0;
                entry.vseq = newVs;

                if (cs == cs_gi && prev.tone != 0) {
                    if (entry.tone == 0) {
                        entry.tone = prev.tone;
                    }
                    markChange(current - 1);
                    prev.tone = 0;
                    return 1;
                }
                break;

            default:
                break;
        }

        markChange(current);
        return 1;
    }

    //=================================================================
    // appendConsonnant (ukengine.cpp)
    //=================================================================
    private int appendConsonnant(UkKeyEvent ev) {
        boolean complexEvent = false;
        current++;
        WordInfo entry = buffer[current];

        int lowerSym = vnToLower(ev.vnSym);

        entry.vnSym = lowerSym;
        entry.caps = (lowerSym != ev.vnSym) ? 1 : 0;
        entry.keyCode = ev.keyCode;
        entry.tone = 0;

        if (current == 0 || !vietKey) {
            entry.form = vnw_c;
            entry.c1Offset = 0;
            entry.c2Offset = -1;
            entry.vOffset = -1;
            entry.cseq = lookupCSeq(lowerSym);
            markChange(current);
            return 1;
        }

        int cs, newCs, c1;
        int isValid;

        WordInfo prev = buffer[current - 1];

        switch (prev.form) {
            case vnw_nonVn:
                entry.form = vnw_nonVn;
                entry.c1Offset = -1;
                entry.c2Offset = -1;
                entry.vOffset = -1;
                markChange(current);
                return 1;

            case vnw_empty:
                entry.form = vnw_c;
                entry.c1Offset = 0;
                entry.c2Offset = -1;
                entry.vOffset = -1;
                entry.cseq = lookupCSeq(lowerSym);
                markChange(current);
                return 1;

            case vnw_v:
            case vnw_cv:
                int vs = prev.vseq;
                int newVs = vs;
                if (vs == vs_uoh || vs == vs_uho) {
                    newVs = vs_uhoh;
                }

                c1 = cs_nil;
                if (prev.c1Offset != -1) {
                    c1 = buffer[current - 1 - prev.c1Offset].cseq;
                }

                newCs = lookupCSeq(lowerSym);
                isValid = isValidCVC(c1, newVs, newCs) ? 1 : 0;

                if (isValid == 1) {
                    // kiểm tra ưo -> ưo+
                    if (vs == vs_uho) {
                        markChange(current - 1);
                        prev.vnSym = vnl_oh;
                        prev.vseq = vs_uhoh;
                        complexEvent = true;
                    } else if (vs == vs_uoh) {
                        markChange(current - 2);
                        buffer[current - 2].vnSym = vnl_uh;
                        buffer[current - 2].vseq = vs_uh;
                        prev.vseq = vs_uhoh;
                        complexEvent = true;
                    }

                    if (prev.form == vnw_v) {
                        entry.form = vnw_vc;
                        entry.c1Offset = -1;
                        entry.c2Offset = 0;
                        entry.vOffset = 1;
                    } else { // prev == vnw_cv
                        entry.form = vnw_cvc;
                        entry.c1Offset = prev.c1Offset + 1;
                        entry.c2Offset = 0;
                        entry.vOffset = 1;
                    }
                    entry.cseq = newCs;

                    // dời dấu thanh nếu cần
                    int oldIdx = (current - 1) - (vseqLen(vs) - 1)
                            + getTonePosition(vs, true);
                    if (buffer[oldIdx].tone != 0) {
                        int newIdx = (current - 1) - (vseqLen(newVs) - 1)
                                + getTonePosition(newVs, false);
                        if (newIdx != oldIdx) {
                            markChange(newIdx);
                            buffer[newIdx].tone = buffer[oldIdx].tone;
                            markChange(oldIdx);
                            buffer[oldIdx].tone = 0;
                            return 1;
                        }
                    }
                } else {
                    entry.form = vnw_nonVn;
                    entry.c1Offset = -1;
                    entry.c2Offset = -1;
                    entry.vOffset = -1;
                }

                if (complexEvent) {
                    return 1;
                }
                markChange(current);
                return 1;

            case vnw_c:
            case vnw_vc:
            case vnw_cvc:
                cs = prev.cseq;
                // cseq == cs_nil: ký tự đầu từ là phụ âm không có trong CSeqList
                // (f, h, j, w, z...). Không thể mở rộng chuỗi phụ âm này.
                if (cs == cs_nil) {
                    newCs = cs_nil;
                } else if (CSEQ[cs].len == 3) {
                    newCs = cs_nil;
                } else if (CSEQ[cs].len == 2) {
                    newCs = lookupCSeq(CSEQ[cs].c0, CSEQ[cs].c1, lowerSym);
                } else {
                    newCs = lookupCSeq(CSEQ[cs].c0, lowerSym);
                }

                if (newCs != cs_nil && (prev.form == vnw_vc || prev.form == vnw_cvc)) {
                    c1 = cs_nil;
                    if (prev.c1Offset != -1) {
                        c1 = buffer[current - 1 - prev.c1Offset].cseq;
                    }
                    int vIdx = (current - 1) - prev.vOffset;
                    int vs2 = buffer[vIdx].vseq;
                    if (!isValidCVC(c1, vs2, newCs)) {
                        newCs = cs_nil;
                    }
                }

                if (newCs == cs_nil) {
                    entry.form = vnw_nonVn;
                    entry.c1Offset = -1;
                    entry.c2Offset = -1;
                    entry.vOffset = -1;
                } else {
                    if (prev.form == vnw_c) {
                        entry.form = vnw_c;
                        entry.c1Offset = 0;
                        entry.c2Offset = -1;
                        entry.vOffset = -1;
                    } else if (prev.form == vnw_vc) {
                        entry.form = vnw_vc;
                        entry.c1Offset = -1;
                        entry.c2Offset = 0;
                        entry.vOffset = prev.vOffset + 1;
                    } else { // vnw_cvc
                        entry.form = vnw_cvc;
                        entry.c1Offset = prev.c1Offset + 1;
                        entry.c2Offset = 0;
                        entry.vOffset = prev.vOffset + 1;
                    }
                    entry.cseq = newCs;
                }
                markChange(current);
                return 1;

            default:
                break;
        }

        markChange(current);
        return 1;
    }

    //=================================================================
    // processWordEnd (ukengine.cpp) — không dùng macro
    //=================================================================
    private int processWordEnd(UkKeyEvent ev) {
        current++;
        WordInfo entry = buffer[current];
        entry.vseq = vs_nil;
        entry.cseq = cs_nil;
        entry.tone = 0;
        entry.form = vnw_empty;
        entry.c1Offset = -1;
        entry.c2Offset = -1;
        entry.vOffset = -1;
        entry.keyCode = ev.keyCode;
        entry.vnSym = vnToLower(ev.vnSym);
        entry.caps = (entry.vnSym != ev.vnSym) ? 1 : 0;
        return 0;
    }

    //=================================================================
    // markChange / getSeqSteps (ukengine.cpp)
    //=================================================================
    private void markChange(int pos) {
        if (pos < changePos) {
            backs += getSeqSteps(pos, changePos - 1);
            changePos = pos;
        }
    }

    private int getSeqSteps(int first, int last) {
        if (last < first) {
            return 0;
        }
        int count = 0;
        for (int i = first; i <= last; i++) {
            int std = stdCharAt(i);
            if (std < 0) {
                continue;
            }
            count++;
        }
        return count;
    }

    /** Chuỗi ký tự mà một mục trong buffer tạo ra (dùng để đếm backspace). */
    private int stdCharAt(int index) {
        WordInfo info = buffer[index];
        if (info.vnSym == vnl_nonVnChar) {
            return info.keyCode;
        }
        int sym = info.vnSym;
        if (info.caps == 1) {
            sym = sym - 1;
        }
        return sym;
    }

    //=================================================================
    // Chuẩn bị buffer (UkEngine::prepareBuffer)
    //=================================================================
    private void prepareBuffer() {
        if (current >= 0 && current + 10 >= bufferSize) {
            int rid;
            for (rid = current / 2; rid < current && buffer[rid].form != vnw_empty; rid++) {
                // tìm điểm cắt không nằm giữa một từ
            }
            if (rid == current) {
                current = -1;
            } else {
                rid++;
                int remaining = current - rid + 1;
                WordInfo[] copy = new WordInfo[remaining];
                for (int i = 0; i < remaining; i++) {
                    if (buffer[rid + i] == null) {
                        buffer[rid + i] = new WordInfo();
                    }
                    copy[i] = buffer[rid + i];
                }
                System.arraycopy(copy, 0, buffer, 0, remaining);
                for (int i = remaining; i < buffer.length; i++) {
                    buffer[i] = new WordInfo();
                }
                current -= rid;
            }
        }
    }

    //=================================================================
    // Tiện ích
    //=================================================================
    private static int vseqLen(int vs) {
        return VSEQ[vs].len;
    }

    private static int charTypeOf(int keyCode) {
        if (keyCode < 0 || keyCode > 255) {
            return (isoToVnLexi(keyCode) == vnl_nonVnChar) ? ukcNonVn : ukcVn;
        }
        return UKC_MAP[keyCode];
    }

    /** Chuyển một mục trong buffer thành ký tự Unicode tương ứng. */
    private void appendOutputChar(StringBuilder sb, WordInfo info) {
        if (info.vnSym == vnl_nonVnChar) {
            sb.append((char) info.keyCode);
            return;
        }
        char out = stdVnCharToUnicode(info.vnSym, info.tone, info.caps);
        if (out == 0) {
            sb.append((char) info.keyCode);
        } else {
            sb.append(out);
        }
    }

    /**
     * Chuyển (ký hiệu, dấu thanh, chữ hoa) thành ký tự Unicode.
     * Công thức giống writeOutput() trong ukengine.cpp:
     *   stdChar = vnSym + VnStdCharOffset;
     *   if (caps) stdChar--;
     *   if (tone) stdChar += tone * 2;
     * nên chỉ số trong UnicodeTable là: vnSym + tone * 2 - caps.
     */
    private static char stdVnCharToUnicode(int vnSym, int tone, int caps) {
        if (vnSym == vnl_nonVnChar) {
            return 0;
        }
        int index = vnSym + tone * 2 - caps;
        if (index >= 0 && index < VN_TABLE_CHAR_COUNT) {
            return UNI[index];
        }
        return 0;
    }

    /**
     * Chuyển một ký tự tiếng Việt Unicode về dạng đã chuẩn hoá (không dấu).
     * Dùng khi engine cần nạp trước nội dung đã có trong ô nhập liệu.
     */
    public void feedChar(int ch) {
        process(ch);
    }

    /** Trả về chuỗi ký tự Unicode của ký hiệu VnLexiName (tiện cho kiểm thử). */
    public static String lexiToUnicode(int vnSym, int tone, int caps) {
        if (vnSym == vnl_nonVnChar) {
            return "";
        }
        char c = stdVnCharToUnicode(vnSym, tone, caps);
        return c == 0 ? "" : String.valueOf(c);
    }
}
