package com.hkeyboard.vni;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * HKeyboard Service — bàn phím ảo 0dp dành cho bàn phím rời (Huawei Smart Keyboard).
 *
 * Service này:
 *  - Trả về view cao 0dp → không hiện bàn phím ảo.
 *  - Chuyển toàn bộ phím ký tự cho {@link UnikeyEngine} — bộ máy gõ tiếng Việt
 *    theo đúng quy tắc của UniKey (xem UnikeyEngine.java).
 *  - Engine tự quyết định số ký tự cần xoá và nội dung cần chèn.
 *
 * Toàn bộ quy tắc gõ dấu cũ của HKeyboard đã được loại bỏ hoàn toàn; không còn
 * việc đọc text trước con trỏ để đoán từ, nên không còn lỗi kiểu "gõ chữ, cách
 * ra khoảng trắng rồi gõ số thì số vẫn bị tính là dấu của chữ trước đó".
 */
public class HKeyboardService extends InputMethodService {

    /** Bộ máy gõ tiếng Việt theo quy tắc UniKey. */
    private UnikeyEngine engine;

    /** Bật/tắt gõ tiếng Việt (mặc định bật). */
    private boolean vietnameseMode = true;

    /** Theo dõi onKeyUp tương ứng với onKeyDown đã tiêu thụ. */
    private boolean lastKeyConsumed = false;
    private int lastConsumedKeyCode = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------
    // Vòng đời
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        UnikeyEngine.Options options = new UnikeyEngine.Options();
        // Theo đúng bộ tùy chọn mặc định của UniKey (CreateDefaultUnikeyOptions)
        options.freeMarking = true;        // FreeStyle = Yes (VNI khuyến nghị)
        options.modernStyle = false;       // "hòa", "khỏe" (kiểu cũ)
        options.spellCheckEnabled = true;  // kiểm tra chính tả
        options.macroEnabled = false;
        options.autoNonVnRestore = false;
        engine = new UnikeyEngine(options);
    }

    @Override
    public View onCreateInputView() {
        return getLayoutInflater().inflate(R.layout.keyboard_view, null);
    }

    /** Không bao giờ hiện bàn phím ảo. */
    @Override
    public boolean onEvaluateInputViewShown() {
        return false;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        lastKeyConsumed = false;
        lastConsumedKeyCode = -1;
        if (!restarting) {
            engine.reset();
        }
        setCandidatesViewShown(false);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // Trạng thái engine không còn khớp với nội dung ô nhập liệu -> reset.
        engine.reset();
        requestHideSelf(0);
    }

    @Override
    public void onFinishInput() {
        engine.reset();
        super.onFinishInput();
    }

    // ------------------------------------------------------------------
    // Xử lý phím
    // ------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        lastKeyConsumed = false;
        lastConsumedKeyCode = -1;

        // Bỏ qua nếu có phím bổ trợ (Shift, Alt, Ctrl, Meta) — tránh việc
        // Shift+0 (ký tự ')') bị hiểu là phím 0 và làm mất dấu.
        if (event.isAltPressed() || event.isCtrlPressed() || event.isMetaPressed()) {
            return super.onKeyDown(keyCode, event);
        }

        // Giữ phím = lặp lại, không xử lý lại
        if (event.getRepeatCount() > 0) {
            return super.onKeyDown(keyCode, event);
        }

        if (!vietnameseMode) {
            return super.onKeyDown(keyCode, event);
        }

        // Backspace: engine tự đồng bộ trạng thái với ô nhập liệu
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            UnikeyEngine.Result result = engine.processBackspace();
            if (result.consumed) {
                applyResult(result, 0);
                lastKeyConsumed = true;
                lastConsumedKeyCode = keyCode;
                return true;
            }
            // Engine không có gì để chèn/xoá -> hệ thống tự xoá một ký tự.
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL
                || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_MOVE_HOME
                || keyCode == KeyEvent.KEYCODE_MOVE_END
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // Con trỏ có thể đã di chuyển -> trạng thái engine không còn đúng
            engine.reset();
            engine.resetKeyBuf();
            return super.onKeyDown(keyCode, event);
        }

        int ch = characterFromKey(keyCode, event);
        if (ch < 0) {
            // Phím không tạo ký tự (Enter, Tab, phím chức năng...) -> để hệ
            // thống xử lý; engine tự reset khi nhận ký tự điều khiển.
            return super.onKeyDown(keyCode, event);
        }

        UnikeyEngine.Result result = engine.process(ch);
        if (!result.handled) {
            // Ký tự điều khiển: để hệ thống xử lý.
            return super.onKeyDown(keyCode, event);
        }
        // Engine đã quản lý trạng thái phím này. Khi engine không sinh ra nội
        // dung nào (chữ cái ASCII thường, dấu cách, chữ số sau dấu cách...),
        // chính chúng ta phải chèn ký tự gốc — giống UniKey: hệ thống tự chèn
        // phím mà bộ gõ trả về 0.
        if (applyResult(result, ch)) {
            lastKeyConsumed = true;
            lastConsumedKeyCode = keyCode;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (lastKeyConsumed && keyCode == lastConsumedKeyCode) {
            lastKeyConsumed = false;
            lastConsumedKeyCode = -1;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Áp dụng kết quả của engine vào ô nhập liệu.
     *
     * <p>Hợp đồng của {@link UnikeyEngine} giống hệt UniKey gốc:
     * <ul>
     *   <li>{@code out != null}: xoá {@code backspaces} ký tự rồi chèn
     *       {@code out}.</li>
     *   <li>{@code out == null} và {@code handled == true}: engine chỉ cập nhật
     *       trạng thái, người gọi phải tự chèn ký tự gốc {@code rawChar}.</li>
     *   <li>{@code handled == false}: phím không liên quan tới bộ gõ.</li>
     * </ul>
     *
     * @param rawChar ký tự gốc của phím (0 nếu là phím Backspace)
     * @return true nếu phím đã bị tiêu thụ
     */
    private boolean applyResult(UnikeyEngine.Result result, int rawChar) {
        String committed = result.out;
        if (committed == null && rawChar > 0) {
            committed = new String(Character.toChars(rawChar));
        }
        if (result.backspaces <= 0 && (committed == null || committed.isEmpty())) {
            return false;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        ic.beginBatchEdit();
        try {
            if (result.backspaces > 0) {
                ic.deleteSurroundingText(result.backspaces, 0);
            }
            if (committed != null && !committed.isEmpty()) {
                ic.commitText(committed, 1);
            }
        } finally {
            ic.endBatchEdit();
        }
        return true;
    }

    /**
     * Lấy ký tự (mã Unicode) mà một phím tạo ra, hoặc -1 nếu phím không tạo
     * ký tự nào.
     *
     * <p>Thứ tự ưu tiên:
     * <ol>
     *   <li>{@link KeyEvent#getUnicodeChar(int)} — tôn trọng bố cục bàn phím
     *       của hệ thống và mọi phím bổ trợ.</li>
     *   <li>{@link KeyCharMap#charOf(int, boolean)} — bảng dự phòng có đủ cặp
     *       "không Shift / có Shift", nhờ đó {@code ! @ # $ % ^ & * ( ) _ +}
     *       và các dấu câu khác luôn gõ được kể cả khi thiết bị không cung cấp
     *       được bảng ký tự.</li>
     * </ol>
     */
    private int characterFromKey(int keyCode, KeyEvent event) {
        int unicode = event.getUnicodeChar(event.getMetaState());
        if (unicode > 0) {
            return unicode;
        }
        return KeyCharMap.charOf(keyCode, event.isShiftPressed());
    }

    // ------------------------------------------------------------------
    // API phụ trợ
    // ------------------------------------------------------------------

    /** Bật/tắt chế độ gõ tiếng Việt. */
    public void setVietnameseMode(boolean enabled) {
        this.vietnameseMode = enabled;
        engine.reset();
    }

    public boolean isVietnameseMode() {
        return vietnameseMode;
    }

    /** Đưa một ký tự đã có trong ô nhập liệu vào engine (đồng bộ trạng thái). */
    public void feedExistingText(CharSequence text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            engine.feedChar(text.charAt(i));
        }
    }

    /** Chạy một tác vụ nhỏ trên main thread (giữ tham chiếu Handler). */
    void post(Runnable r) {
        handler.post(r);
    }
}
