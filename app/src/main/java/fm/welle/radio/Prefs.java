package fm.welle.radio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

public class Prefs {
    private static SharedPreferences p(Context context) {
        return context.getSharedPreferences("welle", 0);
    }

    public static boolean autoplay(Context c) { return p(c).getBoolean("autoplay", false); }
    public static void autoplay(Context c, boolean v) { p(c).edit().putBoolean("autoplay", v).apply(); }

    public static boolean pauseUnplug(Context c) { return p(c).getBoolean("pause_unplug", true); }
    public static void pauseUnplug(Context c, boolean v) { p(c).edit().putBoolean("pause_unplug", v).apply(); }

    public static boolean reconnect(Context c) { return p(c).getBoolean("reconnect", true); }
    public static void reconnect(Context c, boolean v) { p(c).edit().putBoolean("reconnect", v).apply(); }

    public static int volume(Context c) { return p(c).getInt("player_vol", 100); }
    public static void volume(Context c, int v) {
        p(c).edit().putInt("player_vol", Math.max(0, Math.min(100, v))).apply();
    }

    public static int bufferSec(Context c) {
        return (Math.max(5, Math.min(60, p(c).getInt("buffer_sec", 15))) / 5) * 5;
    }
    public static void bufferSec(Context c, int v) {
        p(c).edit().putInt("buffer_sec", (Math.max(5, Math.min(60, v)) / 5) * 5).apply();
    }

    public static int sleepMin(Context c) { return p(c).getInt("sleep_min", 0); }
    public static void sleepMin(Context c, int v) { p(c).edit().putInt("sleep_min", v).apply(); }

    public static void saveLast(Context c, Station station) {
        if (station == null) return;
        p(c).edit().putString("last_station", station.toJson().toString()).apply();
    }

    public static Station last(Context c) {
        try {
            String raw = p(c).getString("last_station", "");
            if (raw.isEmpty()) return null;
            return Station.from(new JSONObject(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
