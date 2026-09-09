package fm.welle.radio;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Favorites {
    private final SharedPreferences prefs;

    public Favorites(Context context) {
        this.prefs = context.getSharedPreferences("welle", 0);
    }

    public List<Station> all() {
        Station from;
        ArrayList arrayList = new ArrayList();
        try {
            JSONArray jSONArray = new JSONArray(this.prefs.getString("favorites", "[]"));
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject optJSONObject = jSONArray.optJSONObject(i);
                if (optJSONObject != null && (from = Station.from(optJSONObject)) != null) {
                    arrayList.add(from);
                }
            }
        } catch (Exception unused) {
        }
        return arrayList;
    }

    public boolean has(String str) {
        Iterator<Station> it = all().iterator();
        while (it.hasNext()) {
            if (it.next().id.equals(str)) {
                return true;
            }
        }
        return false;
    }

    public void toggle(Station station) {
        List<Station> all = all();
        int i = 0;
        while (true) {
            if (i < all.size()) {
                if (all.get(i).id.equals(station.id)) {
                    all.remove(i);
                    break;
                }
                i++;
            } else {
                all.add(0, station);
                break;
            }
        }
        JSONArray jSONArray = new JSONArray();
        Iterator<Station> it = all.iterator();
        while (it.hasNext()) {
            jSONArray.put(it.next().toJson());
        }
        this.prefs.edit().putString("favorites", jSONArray.toString()).apply();
    }
}
