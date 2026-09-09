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
    public String tags = "";

    public static Station from(JSONObject jSONObject) {
        Station station = new Station();
        station.id = jSONObject.optString("stationuuid", "").trim();
        station.name = jSONObject.optString(PlayerService.EXTRA_NAME, "").trim();
        String trim = jSONObject.optString("url_resolved", "").trim();
        station.url = trim;
        if (trim.isEmpty()) {
            station.url = jSONObject.optString(PlayerService.EXTRA_URL, "").trim();
        }
        station.country = jSONObject.optString("country", "").trim();
        station.countrycode = jSONObject.optString("countrycode", "").trim().toUpperCase(Locale.ROOT);
        station.language = jSONObject.optString("language", "").trim();
        station.codec = jSONObject.optString("codec", "").trim().toUpperCase(Locale.ROOT);
        station.bitrate = jSONObject.optInt("bitrate", 0);
        station.clicks = jSONObject.optInt("clickcount", 0);
        station.hls = jSONObject.optInt("hls", 0) == 1 || station.url.contains(".m3u8");
        station.favicon = jSONObject.optString(PlayerService.EXTRA_FAVICON, "").trim();
        station.tags = jSONObject.optString("tags", "").trim();
        if (station.id.isEmpty() || station.name.isEmpty() || station.url.isEmpty()) {
            return null;
        }
        return station;
    }

    public static Station local(String str, String str2, String str3, String str4, String str5) {
        Station station = new Station();
        station.id = str;
        station.name = str2;
        station.url = str3;
        station.country = str4;
        station.countrycode = "DE";
        station.language = "deutsch";
        station.codec = "MP3";
        station.bitrate = 192;
        if (str5 == null) {
            str5 = "";
        }
        station.favicon = str5;
        return station;
    }

    public static List<Station> list(JSONArray jSONArray) {
        Station from;
        ArrayList arrayList = new ArrayList();
        if (jSONArray == null) {
            return arrayList;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null && (from = from(optJSONObject)) != null) {
                arrayList.add(from);
            }
        }
        return arrayList;
    }

    public String meta() {
        StringBuilder sb = new StringBuilder();
        if (!this.country.isEmpty()) {
            sb.append(this.country);
        }
        if (this.bitrate > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(this.bitrate).append(" kbps");
        }
        if (!this.codec.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(this.codec);
        }
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject jSONObject = new JSONObject();
        try {
            jSONObject.put("stationuuid", this.id);
            jSONObject.put(PlayerService.EXTRA_NAME, this.name);
            jSONObject.put(PlayerService.EXTRA_URL, this.url);
            jSONObject.put("url_resolved", this.url);
            jSONObject.put("country", this.country);
            jSONObject.put("countrycode", this.countrycode);
            jSONObject.put("language", this.language);
            jSONObject.put("codec", this.codec);
            jSONObject.put("bitrate", this.bitrate);
            jSONObject.put("clickcount", this.clicks);
            jSONObject.put("hls", this.hls ? 1 : 0);
            jSONObject.put(PlayerService.EXTRA_FAVICON, this.favicon);
            jSONObject.put("tags", this.tags);
        } catch (Exception unused) {
        }
        return jSONObject;
    }
}
