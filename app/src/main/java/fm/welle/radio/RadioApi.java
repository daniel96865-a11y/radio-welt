package fm.welle.radio;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class RadioApi {
    private static final String[] MIRRORS = {
            "https://de2.api.radio-browser.info",
            "https://de1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    };
    private static final String UA = "WelleRadio/1.0 (Android)";

    public static List<Station> search(String name, String countryCode, String language, String tag, int limit)
            throws Exception {
        StringBuilder path = new StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=");
        path.append(limit);
        if (name != null && !name.isEmpty()) path.append("&name=").append(enc(name));
        if (countryCode != null && !countryCode.isEmpty()) path.append("&countrycode=").append(enc(countryCode));
        if (language != null && !language.isEmpty()) path.append("&language=").append(enc(language));
        if (tag != null && !tag.isEmpty()) path.append("&tag=").append(enc(tag));
        return Station.list(new JSONArray(get(path.toString())));
    }

    public static List<NamedCount> countries() throws Exception {
        JSONArray arr = new JSONArray(get("/json/countries"));
        ArrayList<NamedCount> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String code = o.optString("iso_3166_1", "").trim().toUpperCase();
            int count = o.optInt("stationcount", 0);
            String name = o.optString(PlayerService.EXTRA_NAME, "").trim();
            if (code.length() == 2 && count > 0 && !name.isEmpty()) {
                out.add(new NamedCount(name, code, count));
            }
        }
        out.sort(Comparator.comparingInt((NamedCount n) -> n.count).reversed());
        return out;
    }

    public static List<NamedCount> languages() throws Exception {
        JSONArray arr = new JSONArray(get("/json/languages"));
        ArrayList<NamedCount> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString(PlayerService.EXTRA_NAME, "").trim();
            int count = o.optInt("stationcount", 0);
            if (!name.isEmpty() && count >= 5) {
                out.add(new NamedCount(name, o.optString("iso_639", ""), count));
            }
        }
        out.sort(Comparator.comparingInt((NamedCount n) -> n.count).reversed());
        return out;
    }

    public static String resolve(String id, String fallback) {
        if (id != null && id.startsWith("t4e-")) {
            return fallback;
        }
        try {
            String url = new JSONObject(get("/json/url/" + enc(id))).optString(PlayerService.EXTRA_URL, "").trim();
            if (!url.isEmpty()) return url;
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String get(String path) throws Exception {
        Exception last = new Exception("Radio-Verzeichnis nicht erreichbar");
        for (String mirror : MIRRORS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(mirror + path).openConnection();
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(9000);
                conn.connect();
                if (conn.getResponseCode() >= 400) {
                    last = new Exception("Radio API " + conn.getResponseCode());
                    continue;
                }
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    bos.write(buf, 0, n);
                }
                return bos.toString(StandardCharsets.UTF_8);
            } catch (Exception e) {
                last = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last;
    }
}
