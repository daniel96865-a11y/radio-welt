package fm.welle.radio;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    public static final String[] MIRRORS = {"https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.txt", "https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.json", "https://paste.rs/Cko3Z"};

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
                if (parse != null && parse.versionCode > 0 && !parse.url.isEmpty() && (info == null || parse.versionCode > info.versionCode)) {
                    info = parse;
                }
            } catch (Exception unused) {
            }
        }
        return info;
    }

    public static void open(Context context, String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(str));
        intent.addFlags(268435456);
        context.startActivity(intent);
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
        httpURLConnection.setRequestProperty("User-Agent", "RadioWelt/3.0 (Android)");
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
