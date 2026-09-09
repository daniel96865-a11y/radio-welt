package fm.welle.radio;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

        interface BoolFn {
        void set(boolean z);
    }

        interface IntFn {
        void set(int i);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        build();
    }

    private void build() {
        this.theme = Theme.current(this);
        this.d = getResources().getDisplayMetrics().density;
        int dp = dp(18);
        LinearLayout linearLayout = new LinearLayout(this);
        this.root = linearLayout;
        linearLayout.setOrientation(1);
        this.root.setBackgroundColor(this.theme.bg);
        this.root.setPadding(dp, dp, dp, dp(28));
        TextView textView = new TextView(this);
        textView.setText("←  Zurück");
        textView.setTextColor(this.theme.muted);
        textView.setTextSize(2, 15.0f);
        textView.setPadding(0, 0, 0, dp(4));
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.lambda$build$0(view);
            }
        });
        this.root.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("Einstellungen");
        textView2.setTextColor(this.theme.fg);
        textView2.setTextSize(2, 30.0f);
        textView2.setTypeface(Typeface.SERIF, 2);
        textView2.setPadding(0, 0, 0, dp(6));
        this.root.addView(textView2);
        LinearLayout card = card();
        card.addView(toggle("Letzten Sender starten", "Beim Öffnen weiterhören", Prefs.autoplay(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.lambda$build$1(z);
            }
        }));
        card.addView(line());
        card.addView(toggle("Kopfhörer gezogen → Pause", "Stoppt beim Abziehen", Prefs.pauseUnplug(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.lambda$build$2(z);
            }
        }));
        card.addView(line());
        card.addView(toggle("Automatisch neu verbinden", "Wenn der Stream abbricht", Prefs.reconnect(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.lambda$build$3(z);
            }
        }));
        card.addView(line());
        volumeRow(card);
        card.addView(line());
        bufferRow(card);
        labeled("Wiedergabe", card);
        LinearLayout card2 = card();
        card2.addView(hint(sleepText()));
        card2.addView(chips(new int[]{0, 15, 30, 45, 60, 90}, new String[]{"Aus", "15", "30", "45", "60", "90"}, Prefs.sleepMin(this), new IntFn() {
            @Override
            public final void set(int i) {
                SettingsActivity.this.lambda$build$4(i);
            }
        }));
        labeled("Schlaf-Timer", card2);
        LinearLayout card3 = card();
        card3.addView(hint("Tippe eine Farbe — gilt überall."));
        card3.addView(colorGrid());
        labeled("Aussehen", card3);
        LinearLayout card4 = card();
        card4.addView(actionRow("Hörverlauf löschen", "Vorschläge starten wieder bei null", new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.lambda$build$5();
            }
        }));
        labeled("Daten", card4);
        LinearLayout card5 = card();
        card5.addView(actionRow("Auf Update prüfen", "Version " + UpdateChecker.installedName(this), new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.checkUpdate();
            }
        }));
        labeled("App", card5);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(this.theme.bg);
        scrollView.setFillViewport(true);
        scrollView.addView(this.root);
        setContentView(scrollView);
        getWindow().setStatusBarColor(this.theme.bg);
        getWindow().setNavigationBarColor(this.theme.bg);
    }

        public /* synthetic */ void lambda$build$0(View view) {
        finish();
    }

        public /* synthetic */ void lambda$build$1(boolean z) {
        Prefs.autoplay(this, z);
    }

        public /* synthetic */ void lambda$build$2(boolean z) {
        Prefs.pauseUnplug(this, z);
    }

        public /* synthetic */ void lambda$build$3(boolean z) {
        Prefs.reconnect(this, z);
    }

        public /* synthetic */ void lambda$build$4(int i) {
        Prefs.sleepMin(this, i);
        PlayerService.setSleepMinutes(i);
        Toast.makeText(this, i == 0 ? "Timer aus" : i + " Minuten", 0).show();
        build();
    }

        public /* synthetic */ void lambda$build$5() {
        ListenHistory.get(this).clear();
        Toast.makeText(this, "Hörverlauf gelöscht", 0).show();
    }

    private String sleepText() {
        long currentTimeMillis = PlayerService.sleepUntil - System.currentTimeMillis();
        if (currentTimeMillis > 0) {
            return "Stoppt in " + Math.max(1L, currentTimeMillis / 60000) + " Min";
        }
        return "Radio schaltet sich danach aus.";
    }

    private void volumeRow(LinearLayout linearLayout) {
        int volume = Prefs.volume(this);
        final TextView label = label("Lautstärke  " + volume + " %");
        linearLayout.addView(label);
        SeekBar slider = slider(100, volume);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
                label.setText("Lautstärke  " + i + " %");
                if (z) {
                    Prefs.volume(SettingsActivity.this, i);
                    PlayerService.applyLiveVolume();
                }
            }
        });
        linearLayout.addView(slider);
    }

    private void bufferRow(LinearLayout linearLayout) {
        linearLayout.addView(label("Puffer  " + Prefs.bufferSec(this) + " Sek."));
        linearLayout.addView(hint("Überbrückt kurze Netzaussetzer."));
        linearLayout.addView(chips(new int[]{5, 10, 15, 20, 30, 60}, new String[]{"5", "10", "15", "20", "30", "60"}, Prefs.bufferSec(this), new IntFn() {
            @Override
            public final void set(int i) {
                SettingsActivity.this.lambda$bufferRow$6(i);
            }
        }));
    }

        public /* synthetic */ void lambda$bufferRow$6(int i) {
        Prefs.bufferSec(this, i);
        PlayerService.applyLiveBuffer();
        Toast.makeText(this, "Puffer: " + i + " Sek.", 0).show();
        build();
    }

    private View colorGrid() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        String str = this.theme.id;
        LinearLayout linearLayout2 = null;
        for (int i = 0; i < Theme.ALL.length; i++) {
            if (i % 5 == 0) {
                linearLayout2 = new LinearLayout(this);
                linearLayout2.setOrientation(0);
                linearLayout2.setGravity(17);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) {
                    layoutParams.topMargin = dp(12);
                }
                linearLayout2.setLayoutParams(layoutParams);
                linearLayout.addView(linearLayout2);
            }
            final Theme theme = Theme.ALL[i];
            boolean equals = theme.id.equals(str);
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            linearLayout3.setGravity(1);
            linearLayout3.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
            View view = new View(this);
            int dp = dp(equals ? 38 : 32);
            view.setLayoutParams(new LinearLayout.LayoutParams(dp, dp));
            view.setBackground(theme.oval(theme.accent));
            if (equals) {
                GradientDrawable oval = theme.oval(theme.accent);
                oval.setStroke(dp(3), this.theme.fg);
                view.setBackground(oval);
            }
            linearLayout3.addView(view);
            TextView textView = new TextView(this);
            textView.setText(equals ? theme.name : " ");
            textView.setTextColor(this.theme.muted);
            textView.setTextSize(2, 11.0f);
            textView.setGravity(17);
            textView.setPadding(0, dp(6), 0, 0);
            linearLayout3.addView(textView);
            linearLayout3.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view2) {
                    SettingsActivity.this.lambda$colorGrid$7(theme, view2);
                }
            });
            linearLayout2.addView(linearLayout3);
        }
        return linearLayout;
    }

        public /* synthetic */ void lambda$colorGrid$7(Theme theme, View view) {
        if (theme.id.equals(Theme.current(this).id)) {
            return;
        }
        Theme.save(this, theme.id);
        build();
    }

    private View chips(int[] iArr, String[] strArr, int i, final IntFn intFn) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setPadding(0, dp(8), 0, 0);
        for (int i2 = 0; i2 < iArr.length; i2++) {
            final int i3 = iArr[i2];
            boolean z = i == i3;
            TextView textView = new TextView(this);
            textView.setText(strArr[i2]);
            Theme theme = this.theme;
            textView.setTextColor(z ? theme.onAccent : theme.fg);
            textView.setTextSize(2, 13.0f);
            textView.setGravity(17);
            textView.setPadding(dp(4), dp(8), dp(4), dp(8));
            Theme theme2 = this.theme;
            textView.setBackground(theme2.roundColor(z ? theme2.accent : theme2.chip, dp(16)));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            if (i2 > 0) {
                layoutParams.setMarginStart(dp(6));
            }
            textView.setLayoutParams(layoutParams);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    intFn.set(i3);
                }
            });
            linearLayout.addView(textView);
        }
        return linearLayout;
    }

    private View toggle(String str, String str2, boolean z, final BoolFn boolFn) {
        final boolean[] zArr = {z};
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(0, dp(10), 0, dp(10));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(this.theme.muted);
        textView2.setTextSize(2, 12.0f);
        linearLayout2.addView(textView);
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2);
        final TextView textView3 = new TextView(this);
        paintSwitch(textView3, zArr[0]);
        linearLayout.addView(textView3);
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.lambda$toggle$9(zArr, boolFn, textView3, view);
            }
        });
        return linearLayout;
    }

        public /* synthetic */ void lambda$toggle$9(boolean[] zArr, BoolFn boolFn, TextView textView, View view) {
        boolean z = !zArr[0];
        zArr[0] = z;
        boolFn.set(z);
        paintSwitch(textView, zArr[0]);
    }

    private void paintSwitch(TextView textView, boolean z) {
        textView.setText(z ? "An" : "Aus");
        Theme theme = this.theme;
        textView.setTextColor(z ? theme.onAccent : theme.fg);
        textView.setTextSize(2, 13.0f);
        textView.setPadding(dp(14), dp(6), dp(14), dp(6));
        Theme theme2 = this.theme;
        textView.setBackground(theme2.roundColor(z ? theme2.accent : theme2.chip, dp(14)));
    }

    private View actionRow(String str, String str2, final Runnable runnable) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(0, dp(8), 0, dp(8));
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(this.theme.muted);
        textView2.setTextSize(2, 12.0f);
        linearLayout.addView(textView);
        linearLayout.addView(textView2);
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                runnable.run();
            }
        });
        return linearLayout;
    }

        public void checkUpdate() {
        Toast.makeText(this, "Prüfe…", 0).show();
        new Thread(new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.lambda$checkUpdate$12();
            }
        }).start();
    }

        public /* synthetic */ void lambda$checkUpdate$12() {
        final UpdateChecker.Info fetch = UpdateChecker.fetch();
        final int installedCode = UpdateChecker.installedCode(this);
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.lambda$checkUpdate$11(fetch, installedCode);
            }
        });
    }

        public /* synthetic */ void lambda$checkUpdate$11(UpdateChecker.Info info, int i) {
        if (info == null) {
            Toast.makeText(this, "Update-Server nicht erreichbar", 0).show();
        } else if (info.versionCode <= i) {
            Toast.makeText(this, "Du hast die neueste Version.", 0).show();
        } else {
            Toast.makeText(this, "Neue Version " + info.versionName, 0).show();
            UpdateChecker.open(this, info.url);
        }
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        Theme theme = this.theme;
        linearLayout.setBackground(theme.roundColor(theme.surface, dp(18)));
        linearLayout.setPadding(dp(14), dp(8), dp(14), dp(12));
        return linearLayout;
    }

    private void labeled(String str, View view) {
        TextView textView = new TextView(this);
        textView.setText(str.toUpperCase());
        textView.setTextColor(this.theme.accent);
        textView.setTextSize(2, 11.0f);
        textView.setLetterSpacing(0.14f);
        textView.setPadding(dp(4), dp(18), 0, dp(8));
        this.root.addView(textView);
        this.root.addView(view);
    }

    private TextView label(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        textView.setPadding(0, dp(10), 0, dp(2));
        return textView;
    }

    private TextView hint(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.muted);
        textView.setTextSize(2, 12.0f);
        textView.setPadding(0, 0, 0, dp(4));
        return textView;
    }

    private View line() {
        View view = new View(this);
        view.setBackgroundColor(this.theme.line);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return view;
    }

    private SeekBar slider(int i, int i2) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(i);
        seekBar.setProgress(i2);
        seekBar.setPadding(dp(2), dp(8), dp(2), dp(4));
        seekBar.setProgressTintList(ColorStateList.valueOf(this.theme.accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(this.theme.accent));
        return seekBar;
    }

    private int dp(int i) {
        return Math.round(i * this.d);
    }
}
