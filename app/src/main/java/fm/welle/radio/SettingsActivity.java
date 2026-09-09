package fm.welle.radio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private float d;
    private LinearLayout root;
    private Theme theme;
    private String downloadUrl =
            "https://github.com/daniel96865-a11y/radio-welt/releases/latest/download/RadioWelt-latest.apk";

    interface BoolFn {
        void set(boolean value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            UpdatesRepository.Feed feed = UpdatesRepository.load(this);
            if (feed != null && feed.downloadUrl != null && !feed.downloadUrl.isEmpty()) {
                downloadUrl = feed.downloadUrl;
            }
            runOnUiThread(this::build);
        }).start();
        build();
    }

    private void build() {
        theme = Theme.current(this);
        d = getResources().getDisplayMetrics().density;
        int pad = (int) (d * 20f);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.bg);
        root.setPadding(pad, pad, pad, pad);

        TextView back = new TextView(this);
        back.setText("←  Zurück");
        back.setTextColor(theme.muted);
        back.setTextSize(2, 15f);
        back.setPadding(0, 0, 0, (int) (d * 8f));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = new TextView(this);
        title.setText("Einstellungen");
        title.setTextColor(theme.fg);
        title.setTextSize(2, 32f);
        title.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        root.addView(title);

        section("Player");
        playerBlock();
        section("Farben");
        TextView hint = new TextView(this);
        hint.setText("Farbe wählen — gilt überall in der App.");
        hint.setTextColor(theme.muted);
        hint.setTextSize(2, 14f);
        hint.setPadding(0, 0, 0, (int) (d * 10f));
        root.addView(hint);
        String currentId = theme.id;
        for (Theme t : Theme.ALL) {
            root.addView(colorRow(t, t.id.equals(currentId)));
        }

        section("Über");
        aboutBlock();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(theme.bg);
        scroll.addView(root);
        setContentView(scroll);
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
    }

    private void aboutBlock() {
        String versionName = "2.9";
        int versionCode = 14;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        TextView ver = new TextView(this);
        ver.setText("Radio Welt " + versionName + "  (Build " + versionCode + ")");
        ver.setTextColor(theme.fg);
        ver.setTextSize(2, 16f);
        ver.setPadding(0, (int) (d * 8f), 0, (int) (d * 6f));
        root.addView(ver);

        TextView desc = new TextView(this);
        desc.setText("Internet-Radio mit Radio Browser, Favoriten, Themen und Updates.");
        desc.setTextColor(theme.muted);
        desc.setTextSize(2, 13f);
        desc.setPadding(0, 0, 0, (int) (d * 12f));
        root.addView(desc);

        TextView btn = new TextView(this);
        btn.setText(getString(R.string.download_apk));
        btn.setTextColor(theme.onAccent);
        btn.setTextSize(2, 15f);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding((int) (d * 18f), (int) (d * 12f), (int) (d * 18f), (int) (d * 12f));
        btn.setBackground(theme.roundColor(theme.accent, d * 18f));
        final String url = downloadUrl;
        btn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "Download-Link nicht öffnenbar", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btn);

        TextView link = new TextView(this);
        link.setText(url);
        link.setTextColor(theme.muted);
        link.setTextSize(2, 11f);
        link.setPadding(0, (int) (d * 8f), 0, (int) (d * 16f));
        root.addView(link);
    }

    private void playerBlock() {
        root.addView(toggle("Letzten Sender starten", "Beim Öffnen der App weiterhören",
                Prefs.autoplay(this), v -> Prefs.autoplay(this, v)));
        root.addView(toggle("Kopfhörer gezogen → Pause", "Stoppt, wenn du die Kopfhörer ziehst",
                Prefs.pauseUnplug(this), v -> Prefs.pauseUnplug(this, v)));
        root.addView(toggle("Automatisch neu verbinden", "Wenn der Stream abbricht",
                Prefs.reconnect(this), v -> Prefs.reconnect(this, v)));

        final TextView volLabel = new TextView(this);
        int volume = Prefs.volume(this);
        volLabel.setText("Player-Lautstärke  " + volume + "%");
        volLabel.setTextColor(theme.fg);
        volLabel.setTextSize(2, 16f);
        volLabel.setPadding(0, (int) (d * 16f), 0, (int) (d * 6f));
        root.addView(volLabel);

        SeekBar vol = new SeekBar(this);
        vol.setMax(100);
        vol.setProgress(volume);
        vol.setPadding((int) (d * 4f), (int) (d * 8f), (int) (d * 4f), (int) (d * 8f));
        vol.setProgressTintList(ColorStateList.valueOf(theme.accent));
        vol.setThumbTintList(ColorStateList.valueOf(theme.accent));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Prefs.volume(SettingsActivity.this, progress);
                    volLabel.setText("Player-Lautstärke  " + progress + "%");
                    PlayerService.applyLiveVolume();
                }
            }
        });
        root.addView(vol);

        final TextView bufLabel = new TextView(this);
        int bufferSec = Prefs.bufferSec(this);
        bufLabel.setText("Puffer  " + bufferSec + " Sekunden");
        bufLabel.setTextColor(theme.fg);
        bufLabel.setTextSize(2, 16f);
        bufLabel.setPadding(0, (int) (d * 18f), 0, (int) (d * 4f));
        root.addView(bufLabel);

        TextView bufHint = new TextView(this);
        bufHint.setText("In 5-Sekunden-Schritten. Überbrückt kurze Netzaussetzer.");
        bufHint.setTextColor(theme.muted);
        bufHint.setTextSize(2, 13f);
        bufHint.setPadding(0, 0, 0, (int) (d * 6f));
        root.addView(bufHint);

        SeekBar buf = new SeekBar(this);
        buf.setMax(11);
        buf.setProgress((bufferSec - 5) / 5);
        buf.setPadding((int) (d * 4f), (int) (d * 8f), (int) (d * 4f), (int) (d * 8f));
        buf.setProgressTintList(ColorStateList.valueOf(theme.accent));
        buf.setThumbTintList(ColorStateList.valueOf(theme.accent));
        buf.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = progress * 5 + 5;
                bufLabel.setText("Puffer  " + sec + " Sekunden");
                if (fromUser) Prefs.bufferSec(SettingsActivity.this, sec);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int sec = seekBar.getProgress() * 5 + 5;
                Prefs.bufferSec(SettingsActivity.this, sec);
                PlayerService.applyLiveBuffer();
                Toast.makeText(SettingsActivity.this, "Puffer: " + sec + " Sekunden", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(buf);

        TextView sleepTitle = new TextView(this);
        sleepTitle.setText("Schlaf-Timer");
        sleepTitle.setTextColor(theme.fg);
        sleepTitle.setTextSize(2, 16f);
        sleepTitle.setPadding(0, (int) (d * 18f), 0, (int) (d * 8f));
        root.addView(sleepTitle);

        TextView sleepHint = new TextView(this);
        long remaining = PlayerService.sleepUntil - System.currentTimeMillis();
        sleepHint.setText(remaining > 0
                ? "Stoppt in " + Math.max(1L, remaining / 60000) + " Min"
                : "Radio schaltet sich danach selbst aus.");
        sleepHint.setTextColor(theme.muted);
        sleepHint.setTextSize(2, 13f);
        sleepHint.setPadding(0, 0, 0, (int) (d * 10f));
        root.addView(sleepHint);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int sleepMin = Prefs.sleepMin(this);
        int[] mins = {0, 15, 30, 45, 60, 90};
        String[] labels = {"Aus", "15", "30", "45", "60", "90"};
        for (int i = 0; i < mins.length; i++) {
            final int value = mins[i];
            boolean on = sleepMin == value;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextColor(on ? theme.onAccent : theme.fg);
            chip.setTextSize(2, 14f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding((int) (d * 12f), (int) (d * 8f), (int) (d * 12f), (int) (d * 8f));
            chip.setBackground(theme.roundColor(on ? theme.accent : theme.chip, d * 18f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart((int) (d * 6f));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                Prefs.sleepMin(this, value);
                PlayerService.setSleepMinutes(value);
                Toast.makeText(this, value == 0 ? "Timer aus" : "Timer: " + value + " Minuten", Toast.LENGTH_SHORT).show();
                build();
            });
            chips.addView(chip);
        }
        root.addView(chips);
    }

    private View toggle(String title, String subtitle, boolean value, BoolFn fn) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (d * 12f), 0, (int) (d * 12f));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(theme.fg);
        t.setTextSize(2, 16f);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(theme.muted);
        s.setTextSize(2, 12f);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts);

        TextView badge = new TextView(this);
        badge.setText(value ? "An" : "Aus");
        badge.setTextColor(value ? theme.onAccent : theme.fg);
        badge.setTextSize(2, 13f);
        badge.setPadding((int) (d * 14f), (int) (d * 6f), (int) (d * 14f), (int) (d * 6f));
        badge.setBackground(theme.roundColor(value ? theme.accent : theme.chip, d * 16f));
        row.addView(badge);

        row.setOnClickListener(v -> {
            fn.set(!value);
            build();
        });
        return row;
    }

    private void section(String name) {
        TextView tv = new TextView(this);
        tv.setText(name.toUpperCase());
        tv.setTextColor(theme.accent);
        tv.setTextSize(2, 12f);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, (int) (22f * d), 0, (int) (d * 8f));
        root.addView(tv);
    }

    private View colorRow(Theme t, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (d * 64f));
        row.setPadding((int) (d * 12f), (int) (d * 8f), (int) (d * 12f), (int) (d * 8f));
        row.setBackground(theme.roundColor(selected ? t.chip : 0, d * 16f));

        View swatch = new View(this);
        swatch.setLayoutParams(new LinearLayout.LayoutParams((int) (d * 40f), (int) (d * 40f)));
        swatch.setBackground(t.oval(t.accent));
        row.addView(swatch);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding((int) (d * 14f), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextColor(theme.fg);
        name.setTextSize(2, 17f);
        TextView status = new TextView(this);
        status.setText(selected ? "Aktiv" : "Tippen zum Übernehmen");
        status.setTextColor(theme.muted);
        status.setTextSize(2, 12f);
        texts.addView(name);
        texts.addView(status);
        row.addView(texts);

        View bg = new View(this);
        LinearLayout.LayoutParams bgLp = new LinearLayout.LayoutParams((int) (d * 10f), (int) (d * 40f));
        bgLp.setMarginStart((int) (d * 8f));
        bg.setLayoutParams(bgLp);
        bg.setBackground(t.roundColor(t.bg, d * 8f));
        row.addView(bg);

        View surf = new View(this);
        LinearLayout.LayoutParams surfLp = new LinearLayout.LayoutParams((int) (d * 10f), (int) (d * 40f));
        surfLp.setMarginStart((int) (d * 4f));
        surf.setLayoutParams(surfLp);
        surf.setBackground(t.roundColor(t.surface, d * 8f));
        row.addView(surf);

        row.setOnClickListener(v -> {
            Theme.save(this, t.id);
            build();
        });
        return row;
    }
}
