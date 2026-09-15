package com.hkeyboard.vni;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/**
 * Màn hình thiết lập của HKeyboard.
 *
 * <p>Hiển thị:
 * <ul>
 *   <li>trạng thái bàn phím (đã bật / đã chọn làm mặc định);</li>
 *   <li>số hiệu phiên bản đang cài;</li>
 *   <li>nút kiểm tra và tự cập nhật từ GitHub Releases.</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity {

    private TextView tvImeStatus;
    private TextView tvImeSelected;
    private TextView tvUpdateStatus;
    private Button btnCheckUpdate;

    private UpdateChecker updateChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvImeStatus = findViewById(R.id.tv_ime_status);
        tvImeSelected = findViewById(R.id.tv_ime_selected);
        TextView tvVersion = findViewById(R.id.tv_version);
        tvUpdateStatus = findViewById(R.id.tv_update_status);
        btnCheckUpdate = findViewById(R.id.btn_check_update);

        updateChecker = new UpdateChecker(this);

        tvVersion.setText(getString(R.string.version_label,
                UpdateChecker.installedVersionName(this),
                UpdateChecker.installedVersionCode(this)));

        // Button: Open system keyboard settings
        Button btnEnable = findViewById(R.id.btn_enable);
        btnEnable.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        // Button: Show IME picker to select HKeyboard
        Button btnSelect = findViewById(R.id.btn_select);
        btnSelect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        btnCheckUpdate.setOnClickListener(v -> checkForUpdate(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        // Tự kiểm tra bản mới mỗi khi mở app; im lặng nếu đã là bản mới nhất.
        checkForUpdate(true);
    }

    /**
     * Check and display the current status of HKeyboard VNI:
     * - Whether it's enabled in system settings
     * - Whether it's selected as the default IME
     */
    private void updateStatus() {
        String packageName = getPackageName();

        // Check if our IME is enabled
        String enabledIMEs = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS
        );
        boolean isEnabled = enabledIMEs != null && enabledIMEs.contains(packageName);
        tvImeStatus.setText(isEnabled ? R.string.status_enabled : R.string.status_disabled);

        // Check if our IME is the currently selected default
        String defaultIME = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        boolean isSelected = defaultIME != null && defaultIME.contains(packageName);
        tvImeSelected.setText(isSelected ? R.string.status_selected : R.string.status_not_selected);
    }

    // ------------------------------------------------------------------
    // Cập nhật phiên bản
    // ------------------------------------------------------------------

    /**
     * @param silent true khi tự kiểm tra lúc mở app: không hiện thông báo lỗi
     *               mạng (tránh làm phiền khi người dùng không chủ động bấm).
     */
    private void checkForUpdate(boolean silent) {
        btnCheckUpdate.setEnabled(false);
        tvUpdateStatus.setText(R.string.update_checking);

        updateChecker.check(new UpdateChecker.CheckListener() {
            @Override
            public void onResult(UpdateChecker.CheckResult result) {
                btnCheckUpdate.setEnabled(true);
                if (result.hasUpdate()) {
                    tvUpdateStatus.setText(getString(R.string.update_available,
                            result.latestVersionName));
                    showUpdateDialog(result);
                } else {
                    tvUpdateStatus.setText(getString(R.string.update_latest,
                            result.currentVersionName));
                }
            }

            @Override
            public void onError(String message) {
                btnCheckUpdate.setEnabled(true);
                if (silent) {
                    tvUpdateStatus.setText(R.string.update_not_checked);
                } else {
                    tvUpdateStatus.setText(getString(R.string.update_error, message));
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.update_error, message),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showUpdateDialog(UpdateChecker.CheckResult result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.update_dialog_message,
                result.latestVersionName, result.currentVersionName));
        if (result.releaseNotes != null && !result.releaseNotes.trim().isEmpty()) {
            message.append("\n\n").append(trimNotes(result.releaseNotes));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.update_dialog_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_now, (d, w) -> startUpdate(result))
                .setNegativeButton(R.string.update_later, null);
        if (result.releasePageUrl != null && !result.releasePageUrl.isEmpty()) {
            builder.setNeutralButton(R.string.update_open_github, (d, w) ->
                    UpdateChecker.openReleasePage(this, result.releasePageUrl));
        }
        builder.show();
    }

    private static String trimNotes(String notes) {
        String trimmed = notes.trim();
        return trimmed.length() > 600 ? trimmed.substring(0, 600) + "…" : trimmed;
    }

    private void startUpdate(UpdateChecker.CheckResult result) {
        if (result.apkUrl == null || result.apkUrl.isEmpty()) {
            Toast.makeText(this, R.string.update_no_apk, Toast.LENGTH_LONG).show();
            if (result.releasePageUrl != null && !result.releasePageUrl.isEmpty()) {
                UpdateChecker.openReleasePage(this, result.releasePageUrl);
            }
            return;
        }

        // Android 8 trở lên bắt buộc người dùng cho phép app cài APK.
        if (!UpdateChecker.canInstallPackages(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_perm_title)
                    .setMessage(R.string.update_perm_message)
                    .setPositiveButton(R.string.update_perm_open, (d, w) ->
                            UpdateChecker.openInstallPermissionSettings(this))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        final ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(R.string.update_downloading);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        updateChecker.downloadApk(result.apkUrl, new UpdateChecker.DownloadListener() {
            @Override
            public void onProgress(int downloadedBytes, int totalBytes) {
                if (totalBytes > 0) {
                    progress.setProgress(downloadedBytes * 100 / totalBytes);
                }
            }

            @Override
            public void onSuccess(File apkFile) {
                progress.dismiss();
                tvUpdateStatus.setText(R.string.update_downloaded);
                if (!UpdateChecker.installApk(SettingsActivity.this, apkFile)) {
                    Toast.makeText(SettingsActivity.this,
                            R.string.update_install_failed, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                tvUpdateStatus.setText(getString(R.string.update_error, message));
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.update_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }
}
