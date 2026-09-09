package fm.welle.radio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

public class Prefs {
    private static SharedPreferences p(Context context) {
        return context.getSharedPreferences("welle", 0);
    }

    public static boolean autoplay(Context context) {
        return p(context).getBoolean("autoplay", false);
    }

    public static void autoplay(Context context, boolean z) {
        p(context).edit().putBoolean("autoplay", z).apply();
    }

    public static boolean pauseUnplug(Context context) {
        return p(context).getBoolean("pause_unplug", true);
    }

    public static void pauseUnplug(Context context, boolean z) {
        p(context).edit().putBoolean("pause_unplug", z).apply();
    }

    public static boolean reconnect(Context context) {
        return p(context).getBoolean("reconnect", true);
    }

    public static void reconnect(Context context, boolean z) {
        p(context).edit().putBoolean("reconnect", z).apply();
    }

    public static int volume(Context context) {
        return p(context).getInt("player_vol", 100);
    }

    public static void volume(Context context, int i) {
        p(context).edit().putInt("player_vol", Math.max(0, Math.min(100, i))).apply();
    }

    public static int bufferSec(Context context) {
        return (Math.max(5, Math.min(60, p(context).getInt("buffer_sec", 15))) / 5) * 5;
    }

    public static void bufferSec(Context context, int i) {
        p(context).edit().putInt("buffer_sec", (Math.max(5, Math.min(60, i)) / 5) * 5).apply();
    }

    public static int sleepMin(Context context) {
        return p(context).getInt("sleep_min", 0);
    }

    public static void sleepMin(Context context, int i) {
        p(context).edit().putInt("sleep_min", i).apply();
    }

    public static void saveLast(Context context, Station station) {
        if (station == null) {
            return;
        }
        p(context).edit().putString("last_station", station.toJson().toString()).apply();
    }

    public static Station last(Context context) {
        try {
            String string = p(context).getString("last_station", "");
            if (string.isEmpty()) {
                return null;
            }
            return Station.from(new JSONObject(string));
        } catch (Exception unused) {
            return null;
        }
    }
}
