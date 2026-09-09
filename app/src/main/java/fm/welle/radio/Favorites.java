package fm.welle.radio;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Favorites {
    private final SharedPreferences prefs;

    public Favorites(Context context) {
        prefs = context.getSharedPreferences("welle", 0);
    }

    public List<Station> all() {
        ArrayList<Station> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("favorites", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    Station s = Station.from(obj);
                    if (s != null) out.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public boolean has(String id) {
        for (Station s : all()) {
            if (s.id.equals(id)) return true;
        }
        return false;
    }

    public void toggle(Station station) {
        List<Station> list = all();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(station.id)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            list.add(0, station);
        }
        JSONArray arr = new JSONArray();
        for (Station s : list) {
            arr.put(s.toJson());
        }
        prefs.edit().putString("favorites", arr.toString()).apply();
    }
}
