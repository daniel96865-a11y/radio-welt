package fm.welle.radio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class Theme {
    public static final Theme[] ALL = {
            new Theme("nacht", "Nacht", "#09090B", "#F4F1EA", "#A8ABB3", "#16161C", "#E8C27A", "#22222A", "#26262E", "#1A1408", false),
            new Theme("ozean", "Ozean", "#07141F", "#E4F1FA", "#8AA9BE", "#0E2233", "#4EC4E0", "#163044", "#1C3A50", "#062028", false),
            new Theme("wein", "Wein", "#160A0E", "#F6E6E4", "#C49A9A", "#241116", "#E06B78", "#321820", "#3A1C26", "#2A0C12", false),
            new Theme("moos", "Moos", "#0B140E", "#E5F0E4", "#8EAA90", "#142018", "#7BC47A", "#1C2C20", "#243428", "#102010", false),
            new Theme("violett", "Violett", "#120C1A", "#F0E8FA", "#B0A0C8", "#1C1528", "#C4A0FF", "#2A2040", "#322848", "#1A1030", false),
            new Theme("kupfer", "Kupfer", "#14100C", "#F3E8D4", "#C2A888", "#221C16", "#E0A04A", "#2E2418", "#382C1C", "#2A1A08", false),
            new Theme("schiefer", "Schiefer", "#12151A", "#E8ECF2", "#9AA6B4", "#1C222A", "#7EB0D4", "#262E38", "#2E3844", "#102028", false),
            new Theme("sakura", "Sakura", "#1A1014", "#FBE8EE", "#D4A0B0", "#281820", "#F090B0", "#382028", "#402830", "#301018", false),
            new Theme("sand", "Sand", "#F3EDE2", "#1C1812", "#6E665A", "#E7DFD2", "#C45C28", "#DDD4C4", "#D4CBB8", "#FFF8EE", true),
            new Theme("schnee", "Schnee", "#F4F5F7", "#16181C", "#5C646E", "#E6E8EC", "#2F6FED", "#D8DCE2", "#CED2D8", "#FFFFFF", true)
    };

    public final String id;
    public final String name;
    public final int bg;
    public final int fg;
    public final int muted;
    public final int surface;
    public final int accent;
    public final int chip;
    public final int line;
    public final int onAccent;
    public final boolean light;

    public Theme(String id, String name, String bg, String fg, String muted, String surface,
                 String accent, String chip, String line, String onAccent, boolean light) {
        this.id = id;
        this.name = name;
        this.bg = Color.parseColor(bg);
        this.fg = Color.parseColor(fg);
        this.muted = Color.parseColor(muted);
        this.surface = Color.parseColor(surface);
        this.accent = Color.parseColor(accent);
        this.chip = Color.parseColor(chip);
        this.line = Color.parseColor(line);
        this.onAccent = Color.parseColor(onAccent);
        this.light = light;
    }

    public static Theme current(Context context) {
        String id = context.getSharedPreferences("welle", 0).getString("theme", "nacht");
        for (Theme t : ALL) {
            if (t.id.equals(id)) return t;
        }
        return ALL[0];
    }

    public static void save(Context context, String id) {
        context.getSharedPreferences("welle", 0).edit().putString("theme", id).apply();
    }

    public GradientDrawable round(float radius) {
        return roundColor(chip, radius);
    }

    public GradientDrawable roundColor(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    public GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
