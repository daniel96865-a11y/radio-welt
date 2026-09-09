package fm.welle.radio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class Theme {
    public static final Theme[] ALL = {new Theme("nacht", "Nacht", "#09090B", "#F4F1EA", "#A8ABB3", "#16161C", "#E8C27A", "#22222A", "#26262E", "#1A1408", false), new Theme("ozean", "Ozean", "#07141F", "#E4F1FA", "#8AA9BE", "#0E2233", "#4EC4E0", "#163044", "#1C3A50", "#062028", false), new Theme("wein", "Wein", "#160A0E", "#F6E6E4", "#C49A9A", "#241116", "#E06B78", "#321820", "#3A1C26", "#2A0C12", false), new Theme("moos", "Moos", "#0B140E", "#E5F0E4", "#8EAA90", "#142018", "#7BC47A", "#1C2C20", "#243428", "#102010", false), new Theme("violett", "Violett", "#120C1A", "#F0E8FA", "#B0A0C8", "#1C1528", "#C4A0FF", "#2A2040", "#322848", "#1A1030", false), new Theme("kupfer", "Kupfer", "#14100C", "#F3E8D4", "#C2A888", "#221C16", "#E0A04A", "#2E2418", "#382C1C", "#2A1A08", false), new Theme("schiefer", "Schiefer", "#12151A", "#E8ECF2", "#9AA6B4", "#1C222A", "#7EB0D4", "#262E38", "#2E3844", "#102028", false), new Theme("sakura", "Sakura", "#1A1014", "#FBE8EE", "#D4A0B0", "#281820", "#F090B0", "#382028", "#402830", "#301018", false), new Theme("sand", "Sand", "#F3EDE2", "#1C1812", "#6E665A", "#E7DFD2", "#C45C28", "#DDD4C4", "#D4CBB8", "#FFF8EE", true), new Theme("schnee", "Schnee", "#F4F5F7", "#16181C", "#5C646E", "#E6E8EC", "#2F6FED", "#D8DCE2", "#CED2D8", "#FFFFFF", true)};
    public final int accent;
    public final int bg;
    public final int chip;
    public final int fg;
    public final String id;
    public final boolean light;
    public final int line;
    public final int muted;
    public final String name;
    public final int onAccent;
    public final int surface;

    public Theme(String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8, String str9, String str10, boolean z) {
        this.id = str;
        this.name = str2;
        this.bg = Color.parseColor(str3);
        this.fg = Color.parseColor(str4);
        this.muted = Color.parseColor(str5);
        this.surface = Color.parseColor(str6);
        this.accent = Color.parseColor(str7);
        this.chip = Color.parseColor(str8);
        this.line = Color.parseColor(str9);
        this.onAccent = Color.parseColor(str10);
        this.light = z;
    }

    public static Theme current(Context context) {
        String string = context.getSharedPreferences("welle", 0).getString("theme", "nacht");
        for (Theme theme : ALL) {
            if (theme.id.equals(string)) {
                return theme;
            }
        }
        return ALL[0];
    }

    public static void save(Context context, String str) {
        context.getSharedPreferences("welle", 0).edit().putString("theme", str).apply();
    }

    public GradientDrawable round(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.chip);
        gradientDrawable.setCornerRadius(f);
        return gradientDrawable;
    }

    public GradientDrawable roundColor(int i, float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(f);
        return gradientDrawable;
    }

    public GradientDrawable oval(int i) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(i);
        return gradientDrawable;
    }
}
