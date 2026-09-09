package fm.welle.radio;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdatesRepository {
    public static final String REMOTE_URL =
            "https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/updates.json";
    public static final String RELATIVE_FALLBACK = "docs/updates.json";

    public static class UpdateItem {
        public final int versionCode;
        public final String versionName;
        public final String date;
        public final String title;
        public final List<String> notes;

        public UpdateItem(int versionCode, String versionName, String date, String title, List<String> notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.date = date;
            this.title = title;
            this.notes = notes;
        }
    }

    public static class Feed {
        public final int latestVersionCode;
        public final String latestVersionName;
        public final String downloadUrl;
        public final List<UpdateItem> updates;

        public Feed(int latestVersionCode, String latestVersionName, String downloadUrl, List<UpdateItem> updates) {
            this.latestVersionCode = latestVersionCode;
            this.latestVersionName = latestVersionName;
            this.downloadUrl = downloadUrl;
            this.updates = updates;
        }
    }

    public static Feed load(Context context) {
        Feed remote = fetchRemote();
        if (remote != null && !remote.updates.isEmpty()) {
            return remote;
        }
        Feed bundled = loadBundled(context);
        if (bundled != null) {
            return bundled;
        }
        return emptyFallback();
    }

    private static Feed fetchRemote() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "RadioWelt/2.9 (Android)");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() >= 400) return null;
            return parse(readStream(conn.getInputStream()));
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Feed loadBundled(Context context) {
        try (InputStream in = context.getAssets().open("updates.json")) {
            return parse(readStream(in));
        } catch (Exception e) {
            return null;
        }
    }

    private static Feed emptyFallback() {
        return new Feed(14, "2.9",
                "https://github.com/daniel96865-a11y/radio-welt/releases/latest/download/RadioWelt-latest.apk",
                Collections.emptyList());
    }

    private static Feed parse(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        int latestCode = root.optInt("latestVersionCode", 0);
        String latestName = root.optString("latestVersionName", "");
        String downloadUrl = root.optString("downloadUrl", "");
        JSONArray arr = root.optJSONArray("updates");
        ArrayList<UpdateItem> items = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ArrayList<String> notes = new ArrayList<>();
                JSONArray n = o.optJSONArray("notes");
                if (n != null) {
                    for (int j = 0; j < n.length(); j++) {
                        String note = n.optString(j, "").trim();
                        if (!note.isEmpty()) notes.add(note);
                    }
                }
                items.add(new UpdateItem(
                        o.optInt("versionCode", 0),
                        o.optString("versionName", ""),
                        o.optString("date", ""),
                        o.optString("title", ""),
                        notes
                ));
            }
        }
        // Always newest first — never hide older entries
        items.sort(Comparator.comparingInt((UpdateItem u) -> u.versionCode).reversed());
        return new Feed(latestCode, latestName, downloadUrl, items);
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
