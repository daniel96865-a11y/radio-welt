package fm.welle.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class Station {
    public int bitrate;
    public int clicks;
    public boolean hls;
    public String id = "";
    public String name = "";
    public String url = "";
    public String country = "";
    public String countrycode = "";
    public String language = "";
    public String codec = "";
    public String favicon = "";

    public static Station from(JSONObject json) {
        Station s = new Station();
        s.id = json.optString("stationuuid", "").trim();
        s.name = json.optString(PlayerService.EXTRA_NAME, "").trim();
        String resolved = json.optString("url_resolved", "").trim();
        s.url = resolved.isEmpty() ? json.optString(PlayerService.EXTRA_URL, "").trim() : resolved;
        s.country = json.optString("country", "").trim();
        s.countrycode = json.optString("countrycode", "").trim().toUpperCase(Locale.ROOT);
        s.language = json.optString("language", "").trim();
        s.codec = json.optString("codec", "").trim().toUpperCase(Locale.ROOT);
        s.bitrate = json.optInt("bitrate", 0);
        s.clicks = json.optInt("clickcount", 0);
        s.hls = json.optInt("hls", 0) == 1 || s.url.contains(".m3u8");
        s.favicon = json.optString(PlayerService.EXTRA_FAVICON, "").trim();
        if (s.id.isEmpty() || s.name.isEmpty() || s.url.isEmpty()) {
            return null;
        }
        return s;
    }

    public static Station local(String id, String name, String url, String country, String favicon) {
        Station s = new Station();
        s.id = id;
        s.name = name;
        s.url = url;
        s.country = country;
        s.countrycode = "DE";
        s.language = "deutsch";
        s.codec = "MP3";
        s.bitrate = 192;
        s.favicon = favicon == null ? "" : favicon;
        return s;
    }

    public static List<Station> list(JSONArray array) {
        ArrayList<Station> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                Station s = from(obj);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public String meta() {
        StringBuilder sb = new StringBuilder();
        if (!country.isEmpty()) {
            sb.append(country);
        }
        if (bitrate > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(bitrate).append(" kbps");
        }
        if (!codec.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(codec);
        }
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("stationuuid", id);
            json.put(PlayerService.EXTRA_NAME, name);
            json.put(PlayerService.EXTRA_URL, url);
            json.put("url_resolved", url);
            json.put("country", country);
            json.put("countrycode", countrycode);
            json.put("language", language);
            json.put("codec", codec);
            json.put("bitrate", bitrate);
            json.put("clickcount", clicks);
            json.put("hls", hls ? 1 : 0);
            json.put(PlayerService.EXTRA_FAVICON, favicon);
        } catch (Exception ignored) {
        }
        return json;
    }
}
