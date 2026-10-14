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
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class RadioApi {
    private static final String[] MIRRORS = {"https://de2.api.radio-browser.info", "https://de1.api.radio-browser.info", "https://at1.api.radio-browser.info"};
    private static final String UA = "WelleRadio/1.0 (Android)";

    public static List<Station> search(String str, String str2, String str3, String str4, int i) throws Exception {
        StringBuilder sb = new StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=");
        sb.append(i);
        if (str != null && !str.isEmpty()) {
            sb.append("&name=").append(enc(str));
        }
        if (str2 != null && !str2.isEmpty()) {
            sb.append("&countrycode=").append(enc(str2));
        }
        if (str3 != null && !str3.isEmpty()) {
            sb.append("&language=").append(enc(str3));
        }
        if (str4 != null && !str4.isEmpty()) {
            sb.append("&tag=").append(enc(str4));
        }
        return Station.list(new JSONArray(get(sb.toString())));
    }

    public static List<NamedCount> countries() throws Exception {
        JSONArray jSONArray = new JSONArray(get("/json/countries"));
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                String upperCase = optJSONObject.optString("iso_3166_1", "").trim().toUpperCase();
                int optInt = optJSONObject.optInt("stationcount", 0);
                String trim = optJSONObject.optString(PlayerService.EXTRA_NAME, "").trim();
                trim = germanCountryName(upperCase, trim);
                if (upperCase.length() == 2 && optInt > 0 && !trim.isEmpty()) {
                    arrayList.add(new NamedCount(trim, upperCase, optInt));
                }
            }
        }
        arrayList.sort(new Comparator() {
            @Override
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(((NamedCount) obj2).count, ((NamedCount) obj).count);
                return compare;
            }
        });
        return arrayList;
    }

    static String germanCountryName(String code, String fallback) {
        if (code == null || code.length() != 2) return fallback;
        String german = new Locale("", code).getDisplayCountry(Locale.GERMANY);
        if (german == null || german.isEmpty() || german.equalsIgnoreCase(code)) return fallback;
        return german;
    }

    public static List<NamedCount> countriesMatching(String query) throws Exception {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.GERMANY);
        List<NamedCount> found = new ArrayList<>();
        for (NamedCount country : countries()) {
            if (country.name.toLowerCase(Locale.GERMANY).contains(needle)
                    || country.code.equalsIgnoreCase(needle)) {
                found.add(country);
            }
        }
        return found;
    }

    public static List<NamedCount> tagsMatching(String query) throws Exception {
        String needle = query == null ? "" : query.trim();
        if (needle.isEmpty()) return new ArrayList<>();
        return tagsFrom(new JSONArray(get("/json/tags/" + enc(needle)
                + "?order=stationcount&reverse=true&hidebroken=true&limit=40")));
    }

    public static List<NamedCount> languages() throws Exception {
        JSONArray jSONArray = new JSONArray(get("/json/languages"));
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                String trim = optJSONObject.optString(PlayerService.EXTRA_NAME, "").trim();
                int optInt = optJSONObject.optInt("stationcount", 0);
                if (!trim.isEmpty() && optInt >= 5) {
                    arrayList.add(new NamedCount(trim, optJSONObject.optString("iso_639", ""), optInt));
                }
            }
        }
        arrayList.sort(new Comparator() {
            @Override
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(((NamedCount) obj2).count, ((NamedCount) obj).count);
                return compare;
            }
        });
        return arrayList;
    }

    public static List<NamedCount> tags() throws Exception {
        return tagsFrom(new JSONArray(get("/json/tags")));
    }

    static List<NamedCount> tagsFrom(JSONArray jSONArray) {
        ArrayList arrayList = new ArrayList();
        if (jSONArray == null) {
            return arrayList;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject == null) {
                continue;
            }
            String raw = optJSONObject.optString("name", "").trim();
            int count = optJSONObject.optInt("stationcount", 0);
            if (raw.length() < 2 || raw.length() > 24 || count < 20) {
                continue;
            }
            if (raw.indexOf(',') >= 0 || raw.indexOf('/') >= 0) {
                continue;
            }
            arrayList.add(new NamedCount(label(raw), raw, count));
        }
        arrayList.sort(new Comparator() {
            @Override
            public final int compare(Object obj, Object obj2) {
                return Integer.compare(((NamedCount) obj2).count, ((NamedCount) obj).count);
            }
        });
        if (arrayList.size() > 70) {
            return new ArrayList(arrayList.subList(0, 70));
        }
        return arrayList;
    }

    private static String label(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    public static String resolve(String str, String str2) {
        String trim = "";
        if (str != null && str.startsWith("t4e-")) {
            return str2;
        }
        try {
            trim = new JSONObject(get("/json/url/" + enc(str))).optString(PlayerService.EXTRA_URL, "").trim();
        } catch (Exception unused) {
        }
        return !trim.isEmpty() ? trim : str2;
    }

    private static String enc(String str) throws Exception {
        return URLEncoder.encode(str, "UTF-8");
    }

    private static String get(String path) throws Exception {
        Exception last = new Exception("Radio-Verzeichnis nicht erreichbar");
        for (String mirror : MIRRORS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
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
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                while (true) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    int n = in.read(buf);
                    if (n < 0) break;
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                last = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last;
    }

}
