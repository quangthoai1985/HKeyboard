package com.hkeyboard.vni;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lưu tuỳ chọn của bàn phím (dùng chung giữa {@link SettingsActivity} và
 * {@link HKeyboardService}).
 */
public final class ImePrefs {

    public static final String FILE = "hkeyboard_prefs";
    public static final String KEY_INPUT_METHOD = "input_method";

    private static final String VALUE_TELEX = "telex";
    private static final String VALUE_VNI = "vni";

    private ImePrefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Kiểu gõ đang chọn. Mặc định VNI để giữ nguyên thói quen người dùng cũ. */
    public static UnikeyEngine.InputMethod inputMethod(Context context) {
        String value = get(context).getString(KEY_INPUT_METHOD, VALUE_VNI);
        return VALUE_TELEX.equals(value)
                ? UnikeyEngine.InputMethod.TELEX : UnikeyEngine.InputMethod.VNI;
    }

    public static void setInputMethod(Context context, UnikeyEngine.InputMethod method) {
        get(context).edit()
                .putString(KEY_INPUT_METHOD,
                        method == UnikeyEngine.InputMethod.TELEX ? VALUE_TELEX : VALUE_VNI)
                .apply();
    }
}
