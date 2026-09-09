package fm.welle.radio;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ListenHistory {
    private static ListenHistory inst;
    private String activeId;
    private long activeStart;
    private final SharedPreferences prefs;

    public static synchronized ListenHistory get(Context context) {
        ListenHistory listenHistory;
        synchronized (ListenHistory.class) {
            if (inst == null) {
                inst = new ListenHistory(context.getApplicationContext());
            }
            listenHistory = inst;
        }
        return listenHistory;
    }

    private ListenHistory(Context context) {
        this.prefs = context.getSharedPreferences("welle", 0);
    }

    public void start(Station station) {
        flush();
        if (station == null || station.id == null || station.id.isEmpty()) {
            return;
        }
        this.activeId = station.id;
        this.activeStart = System.currentTimeMillis();
        bump(station, 1, 0L);
    }

    public void flush() {
        if (this.activeId == null || this.activeStart <= 0) {
            return;
        }
        long currentTimeMillis = (System.currentTimeMillis() - this.activeStart) / 1000;
        this.activeStart = System.currentTimeMillis();
        if (currentTimeMillis > 0) {
            addSeconds(this.activeId, currentTimeMillis);
        }
    }

    public void stop() {
        flush();
        this.activeId = null;
        this.activeStart = 0L;
    }

    public boolean hasEnough() {
        return top(1).size() > 0;
    }

    public List<Station> top(int i) {
        List<JSONObject> all = all();
        all.sort(new Comparator() {
            @Override
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Double.compare(ListenHistory.score((JSONObject) obj2), ListenHistory.score((JSONObject) obj));
                return compare;
            }
        });
        ArrayList arrayList = new ArrayList();
        Iterator<JSONObject> it = all.iterator();
        while (it.hasNext()) {
            Station from = Station.from(it.next());
            if (from != null) {
                arrayList.add(from);
                if (arrayList.size() >= i) {
                    break;
                }
            }
        }
        return arrayList;
    }

    public String topCountryName() {
        String str = topCountry();
        if (str == null) {
            return null;
        }
        for (JSONObject jSONObject : all()) {
            if (str.equalsIgnoreCase(jSONObject.optString("countrycode"))) {
                String trim = jSONObject.optString("country", "").trim();
                if (!trim.isEmpty() && !trim.contains("·") && trim.length() < 40) {
                    return trim;
                }
            }
        }
        return str;
    }

    public String topCountry() {
        HashMap hashMap = new HashMap();
        for (JSONObject jSONObject : all()) {
            String upperCase = jSONObject.optString("countrycode", "").trim().toUpperCase(Locale.ROOT);
            if (upperCase.length() == 2) {
                hashMap.put(upperCase, Long.valueOf(((Long) hashMap.getOrDefault(upperCase, 0L)).longValue() + jSONObject.optLong("seconds", 0L) + (jSONObject.optInt("plays", 0) * 20)));
            }
        }
        return bestKey(hashMap);
    }

    public String topLanguage() {
        HashMap hashMap = new HashMap();
        for (JSONObject jSONObject : all()) {
            String lowerCase = jSONObject.optString("language", "").trim().toLowerCase(Locale.ROOT);
            if (!lowerCase.isEmpty()) {
                String trim = lowerCase.split("[,/]")[0].trim();
                if (trim.length() >= 2) {
                    hashMap.put(trim, Long.valueOf(((Long) hashMap.getOrDefault(trim, 0L)).longValue() + jSONObject.optLong("seconds", 0L)));
                }
            }
        }
        return bestKey(hashMap);
    }

    public String topTag() {
        HashMap hashMap = new HashMap();
        for (JSONObject jSONObject : all()) {
            String lowerCase = jSONObject.optString("tags", "").trim().toLowerCase(Locale.ROOT);
            if (!lowerCase.isEmpty()) {
                long optLong = jSONObject.optLong("seconds", 0L) + 15;
                for (String str : lowerCase.split(",")) {
                    String trim = str.trim();
                    if (trim.length() >= 3 && trim.length() <= 24) {
                        hashMap.put(trim, Long.valueOf(((Long) hashMap.getOrDefault(trim, 0L)).longValue() + optLong));
                    }
                }
            }
        }
        return bestKey(hashMap);
    }

    public Set<String> knownIds() {
        HashSet hashSet = new HashSet();
        Iterator<JSONObject> it = all().iterator();
        while (it.hasNext()) {
            String optString = it.next().optString("stationuuid", "");
            if (!optString.isEmpty()) {
                hashSet.add(optString);
            }
        }
        return hashSet;
    }

    public void clear() {
        stop();
        this.prefs.edit().remove("listen_history").apply();
    }

    private void bump(Station station, int i, long j) {
        JSONObject jSONObject;
        try {
            JSONArray raw = raw();
            int i2 = 0;
            while (true) {
                if (i2 >= raw.length()) {
                    jSONObject = null;
                    i2 = -1;
                    break;
                } else {
                    jSONObject = raw.optJSONObject(i2);
                    if (jSONObject != null && station.id.equals(jSONObject.optString("stationuuid"))) {
                        break;
                    } else {
                        i2++;
                    }
                }
            }
            if (jSONObject == null) {
                jSONObject = station.toJson();
                jSONObject.put("plays", 0);
                jSONObject.put("seconds", 0);
            }
            jSONObject.put("plays", jSONObject.optInt("plays", 0) + i);
            jSONObject.put("seconds", jSONObject.optLong("seconds", 0L) + j);
            jSONObject.put("last", System.currentTimeMillis());
            if (i2 >= 0) {
                raw.put(i2, jSONObject);
            } else {
                raw.put(jSONObject);
            }
            while (raw.length() > 80) {
                raw.remove(0);
            }
            this.prefs.edit().putString("listen_history", raw.toString()).apply();
        } catch (Exception unused) {
        }
    }

    private void addSeconds(String str, long j) {
        try {
            JSONArray raw = raw();
            for (int i = 0; i < raw.length(); i++) {
                JSONObject optJSONObject = raw.optJSONObject(i);
                if (optJSONObject != null && str.equals(optJSONObject.optString("stationuuid"))) {
                    optJSONObject.put("seconds", optJSONObject.optLong("seconds", 0L) + j);
                    optJSONObject.put("last", System.currentTimeMillis());
                    raw.put(i, optJSONObject);
                    this.prefs.edit().putString("listen_history", raw.toString()).apply();
                    return;
                }
            }
        } catch (Exception unused) {
        }
    }

    private JSONArray raw() {
        try {
            return new JSONArray(this.prefs.getString("listen_history", "[]"));
        } catch (Exception unused) {
            return new JSONArray();
        }
    }

    private List<JSONObject> all() {
        JSONArray raw = raw();
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject optJSONObject = raw.optJSONObject(i);
            if (optJSONObject != null) {
                arrayList.add(optJSONObject);
            }
        }
        return arrayList;
    }

    private static double score(JSONObject jSONObject) {
        long optLong = jSONObject.optLong("seconds", 0L);
        int optInt = jSONObject.optInt("plays", 0);
        long last = jSONObject.optLong("last", 0L);
        return (optLong + (optInt * 45)) * (last > 0 ? 1.0d / ((Math.max(0L, (System.currentTimeMillis() - last) / 86400000) / 7.0d) + 1.0d) : 1.0d);
    }

    private static String bestKey(Map<String, Long> map) {
        String str = null;
        long j = 0;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (entry.getValue().longValue() > j) {
                long longValue = entry.getValue().longValue();
                str = entry.getKey();
                j = longValue;
            }
        }
        return str;
    }
}
