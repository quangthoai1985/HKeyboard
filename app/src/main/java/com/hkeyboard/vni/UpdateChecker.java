package com.hkeyboard.vni;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kiểm tra và tải bản cập nhật từ GitHub Releases.
 *
 * <p>Cách hoạt động: gọi {@code GET /repos/{owner}/{repo}/releases/latest},
 * đọc tag (ví dụ {@code v2.1}) và file APK đính kèm, so số phiên bản với bản
 * đang cài. Nếu có bản mới, tải APK về bộ nhớ cache của app rồi mở trình cài
 * đặt của hệ thống.
 *
 * <p>Mọi callback đều được gọi trên main thread.
 */
public final class UpdateChecker {

    private static final String API_TEMPLATE =
            "https://api.github.com/repos/%s/%s/releases/latest";
    private static final String USER_AGENT = "HKeyboard-Android";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    /** Kết quả kiểm tra phiên bản. */
    public static final class CheckResult {
        /** Số phiên bản đang cài. */
        public final int currentVersionCode;
        public final String currentVersionName;
        /** Số phiên bản mới nhất trên GitHub. */
        public final int latestVersionCode;
        public final String latestVersionName;
        /** Trang release trên GitHub (để mở bằng trình duyệt). */
        public final String releasePageUrl;
        /** Liên kết tải trực tiếp file .apk (null nếu release không đính kèm APK). */
        public final String apkUrl;
        /** Ghi chú phát hành. */
        public final String releaseNotes;

        CheckResult(int currentVersionCode, String currentVersionName,
                    int latestVersionCode, String latestVersionName,
                    String releasePageUrl, String apkUrl, String releaseNotes) {
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName;
            this.latestVersionCode = latestVersionCode;
            this.latestVersionName = latestVersionName;
            this.releasePageUrl = releasePageUrl;
            this.apkUrl = apkUrl;
            this.releaseNotes = releaseNotes;
        }

        /** true nếu bản trên GitHub mới hơn bản đang cài. */
        public boolean hasUpdate() {
            return latestVersionCode > currentVersionCode;
        }
    }

    /** Callback cho {@link #check}. */
    public interface CheckListener {
        void onResult(CheckResult result);

        void onError(String message);
    }

    /** Callback cho {@link #downloadApk}. */
    public interface DownloadListener {
        /** @param totalBytes tổng dung lượng, hoặc -1 nếu không xác định được */
        void onProgress(int downloadedBytes, int totalBytes);

        void onSuccess(File apkFile);

        void onError(String message);
    }

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UpdateChecker(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ------------------------------------------------------------------
    // Thông tin phiên bản đang cài
    // ------------------------------------------------------------------

    public static String installedVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "?" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    public static int installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** Chuỗi hiển thị, ví dụ {@code "2.1 (3)"}. */
    public static String installedVersionLabel(Context context) {
        return installedVersionName(context) + " (" + installedVersionCode(context) + ")";
    }

    // ------------------------------------------------------------------
    // Kiểm tra bản mới
    // ------------------------------------------------------------------

    public void check(final CheckListener listener) {
        executor.execute(() -> {
            try {
                CheckResult result = fetchLatestRelease();
                mainHandler.post(() -> listener.onResult(result));
            } catch (Exception e) {
                String message = describeError(e);
                mainHandler.post(() -> listener.onError(message));
            }
        });
    }

    private CheckResult fetchLatestRelease() throws Exception {
        String url = String.format(API_TEMPLATE,
                BuildConfig.GITHUB_REPO_OWNER, BuildConfig.GITHUB_REPO_NAME);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        try {
            int code = conn.getResponseCode();
            if (code == 404) {
                throw new IOException("Không tìm thấy repo "
                        + BuildConfig.GITHUB_REPO_OWNER + "/" + BuildConfig.GITHUB_REPO_NAME
                        + " hoặc repo đang ở chế độ riêng tư.");
            }
            if (code == 403) {
                throw new IOException("GitHub tạm chặn yêu cầu (quá nhiều lượt). Thử lại sau.");
            }
            if (code != 200) {
                throw new IOException("GitHub trả về mã lỗi " + code);
            }
            String body = readAll(conn.getInputStream());
            return parseRelease(appContext, new JSONObject(body));
        } finally {
            conn.disconnect();
        }
    }

    private static CheckResult parseRelease(Context context, JSONObject release) {
        String tag = release.optString("tag_name", "");
        String pageUrl = release.optString("html_url", "");
        String notes = release.optString("body", "");

        String latestName = tag.startsWith("v") || tag.startsWith("V")
                ? tag.substring(1) : tag;
        int latestCode = parseVersionCode(latestName);

        // Tìm file .apk trong danh sách đính kèm.
        String apkUrl = null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase().endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", null);
                    break;
                }
            }
        }

        return new CheckResult(
                installedVersionCode(context),
                installedVersionName(context),
                latestCode,
                latestName,
                pageUrl,
                apkUrl,
                notes);
    }

    /**
     * Đổi "2.1" thành 20100, "2.1.3" thành 20103, "2" thành 20000.
     *
     * <p>Quy ước: major * 10000 + minor * 100 + patch. Khớp với
     * {@code versionCode} trong {@code version.properties}.
     */
    static int parseVersionCode(String versionName) {
        if (versionName == null || versionName.isEmpty()) {
            return 0;
        }
        String clean = versionName.trim();
        // Bỏ hậu tố kiểu "-beta", "+build".
        int cut = clean.indexOf('-');
        if (cut > 0) {
            clean = clean.substring(0, cut);
        }
        cut = clean.indexOf('+');
        if (cut > 0) {
            clean = clean.substring(0, cut);
        }
        String[] parts = clean.split("\\.");
        int major = partAt(parts, 0);
        int minor = partAt(parts, 1);
        int patch = partAt(parts, 2);
        return major * 10000 + minor * 100 + patch;
    }

    private static int partAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Tải APK
    // ------------------------------------------------------------------

    /** Thư mục chứa APK tải về (trong bộ nhớ cache của app, không cần quyền). */
    public static File apkFile(Context context) {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            // Không tạo được thì vẫn trả về đường dẫn để báo lỗi rõ ràng.
            return new File(dir, "Hkeyboard.apk");
        }
        return new File(dir, "Hkeyboard.apk");
    }

    public void downloadApk(final String apkUrl, final DownloadListener listener) {
        executor.execute(() -> {
            File target = apkFile(appContext);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new IOException("Máy chủ trả về mã " + code);
                }
                int total = conn.getContentLength();

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[16384];
                    int downloaded = 0;
                    int lastReported = -1;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        // Báo tiến độ theo từng 1% để không làm nghẽn main thread.
                        int percent = total > 0 ? (downloaded * 100 / total) : 0;
                        if (percent != lastReported) {
                            lastReported = percent;
                            final int d = downloaded;
                            mainHandler.post(() -> listener.onProgress(d, total));
                        }
                    }
                }

                if (!isValidApk(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    throw new IOException("File tải về không phải APK hợp lệ.");
                }
                final File done = target;
                mainHandler.post(() -> listener.onSuccess(done));
            } catch (Exception e) {
                String message = describeError(e);
                mainHandler.post(() -> listener.onError(message));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** Kiểm tra file tải về đúng là APK của ứng dụng này. */
    private boolean isValidApk(File file) {
        PackageManager pm = appContext.getPackageManager();
        PackageInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info = pm.getPackageArchiveInfo(file.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(0));
        } else {
            info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
        }
        return info != null && appContext.getPackageName().equals(info.packageName);
    }

    // ------------------------------------------------------------------
    // Mở trình cài đặt
    // ------------------------------------------------------------------

    /** true nếu app đã được phép cài APK từ nguồn này. */
    public static boolean canInstallPackages(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** Mở màn hình cấp quyền "Cài đặt ứng dụng không rõ nguồn gốc". */
    public static void openInstallPermissionSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Mở trình cài đặt APK của hệ thống.
     *
     * @return true nếu đã mở được
     */
    public static boolean installApk(Context context, File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Mở trang release trên GitHub bằng trình duyệt. */
    public static void openReleasePage(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedInputStream bis = new BufferedInputStream(in)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, read, "UTF-8"));
            }
        }
        return sb.toString();
    }

    private static String describeError(Exception e) {
        if (e instanceof java.net.UnknownHostException) {
            return "Không có kết nối mạng.";
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "Hết thời gian chờ. Kiểm tra mạng rồi thử lại.";
        }
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
