package fm.welle.radio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    /** GitHub raw feeds only — paste.rs/Cko3Z was stale (3.1). */
    public static final String[] MIRRORS = {
            "https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.txt",
            "https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.json"
    };

    private static final AtomicBoolean installing = new AtomicBoolean(false);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static class Info {
        public int versionCode;
        public String versionName = "";
        public String title = "Update verfügbar";
        public String message = "Eine neue Version von Radio Welt 2.0 ist da.";
        public String url = "";
    }

    public static int installedCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception unused) {
            return 0;
        }
    }

    public static String installedName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName == null ? "" : packageInfo.versionName;
        } catch (Exception unused) {
            return "";
        }
    }

    public static Info fetch() {
        Info info = null;
        for (String str : MIRRORS) {
            try {
                Info parse = parse(get(str));
                if (parse != null && parse.versionCode > 0 && !parse.url.isEmpty()
                        && (info == null || parse.versionCode > info.versionCode)) {
                    info = parse;
                }
            } catch (Exception unused) {
            }
        }
        return info;
    }

    /**
     * Download APK to cache and launch the system package installer (TV-friendly).
     * Falls back to browser only if download/install setup fails hard.
     */
    public static void open(final Context context, final String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            return;
        }
        if (!installing.compareAndSet(false, true)) {
            toast(context, "Update wird bereits geladen…");
            return;
        }
        final Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 26) {
            if (!app.getPackageManager().canRequestPackageInstalls()) {
                installing.set(false);
                toast(context, "Bitte Installation aus unbekannten Quellen erlauben, dann erneut OK drücken.");
                openUnknownSourcesSettings(context);
                return;
            }
        }
        toast(context, "Update wird geladen…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                File apk = null;
                try {
                    apk = downloadApk(app, apkUrl);
                    final File ready = apk;
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                launchInstall(context, ready);
                            } catch (Exception e) {
                                toast(context, "Installation fehlgeschlagen. Alte App deinstallieren, dann neu installieren.");
                                fallbackBrowser(context, apkUrl);
                            } finally {
                                installing.set(false);
                            }
                        }
                    });
                } catch (Exception e) {
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(context, "Download fehlgeschlagen — öffne Link im Browser.");
                            fallbackBrowser(context, apkUrl);
                            installing.set(false);
                        }
                    });
                }
            }
        }).start();
    }

    private static File downloadApk(Context app, String apkUrl) throws Exception {
        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("cache dir");
        }
        // Clean old APKs
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        File out = new File(dir, "RadioWelt-update.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RadioWelt/3.3 (Android)");
        conn.setRequestProperty("Accept", "*/*");
        try {
            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new Exception("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int n;
                    int lastPct = -1;
                    long contentLen = conn.getContentLengthLong();
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        total += n;
                        if (contentLen > 0) {
                            int pct = (int) ((total * 100) / contentLen);
                            if (pct >= lastPct + 20) {
                                lastPct = pct;
                                final int show = pct;
                                UI.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast(app, "Update: " + show + " %");
                                    }
                                });
                            }
                        }
                    }
                    fos.flush();
                } finally {
                    fos.close();
                }
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
        if (!out.exists() || out.length() < 1000) {
            throw new Exception("empty apk");
        }
        return out;
    }

    private static void launchInstall(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Helpful note before system installer — signature mismatch needs uninstall
        toast(context, "Falls Installation wegen Signatur scheitert: alte App deinstallieren, dann neu installieren.");
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri);
            install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(install);
        }
    }

    private static void openUnknownSourcesSettings(Context context) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    private static void fallbackBrowser(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static void toast(Context context, String msg) {
        try {
            Context c = context instanceof Activity ? context : context.getApplicationContext();
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
            } else {
                UI.post(() -> Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
            }
        } catch (Exception ignored) {
        }
    }

    static Info parse(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        Info info = new Info();
        Matcher matcher = Pattern.compile("versionCode[\"\\s:=]+(\\d+)").matcher(str);
        if (matcher.find()) {
            info.versionCode = Integer.parseInt(matcher.group(1));
        }
        Matcher matcher2 = Pattern.compile("versionName[\"\\s:=]+([0-9][0-9.]*)").matcher(str);
        if (matcher2.find()) {
            info.versionName = matcher2.group(1);
        }
        Matcher matcher3 = Pattern.compile("title[\"\\s:=]+([^\\n\"<]+)").matcher(str);
        if (matcher3.find()) {
            info.title = matcher3.group(1).trim();
        }
        Matcher matcher4 = Pattern.compile("message[\"\\s:=]+([^\\n\"<]+)").matcher(str);
        if (matcher4.find()) {
            info.message = matcher4.group(1).trim();
        }
        Matcher matcher5 = Pattern.compile("https://[^\\s\"'<>]+\\.(?:zip|apk)").matcher(str);
        if (matcher5.find()) {
            info.url = matcher5.group(0);
        }
        if (info.url.isEmpty()) {
            Matcher matcher6 = Pattern.compile("url[\"\\s:=]+(https://[^\\s\"'<>]+)").matcher(str);
            if (matcher6.find()) {
                info.url = matcher6.group(1).replace("</a>", "");
            }
        }
        if (info.versionCode <= 0) {
            return null;
        }
        return info;
    }

    private static String get(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(8000);
        httpURLConnection.setReadTimeout(8000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("User-Agent", "RadioWelt/3.3 (Android)");
        httpURLConnection.setRequestProperty("Accept", "text/plain, application/json, text/html;q=0.8");
        try {
            InputStream inputStream = httpURLConnection.getInputStream();
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[4096];
                int i = 0;
                while (true) {
                    int read = inputStream.read(bArr);
                    if (read <= 0 || i >= 200000) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr, 0, read);
                    i += read;
                }
                String byteArrayOutputStream2 = byteArrayOutputStream.toString("UTF-8");
                if (inputStream != null) {
                    inputStream.close();
                }
                return byteArrayOutputStream2;
            } finally {
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }
}
