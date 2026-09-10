package fm.welle.radio;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
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
    private View firstFocus;

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
        this.firstFocus = null;
        LinearLayout linearLayout = new LinearLayout(this);
        this.root = linearLayout;
        linearLayout.setOrientation(1);
        this.root.setBackgroundColor(this.theme.bg);
        this.root.setPadding(dp, dp, dp, dp(28));
        TextView textView = new TextView(this);
        textView.setText("←  Zurück");
        textView.setTextColor(this.theme.muted);
        textView.setTextSize(2, 15.0f);
        textView.setPadding(dp(10), dp(10), dp(10), dp(10));
        textView.setFocusable(true);
        textView.setClickable(true);
        textView.setBackground(focusRowBg());
        attachFocusScale(textView);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.onBackClick(view);
            }
        });
        this.root.addView(textView);
        rememberFirst(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("Einstellungen");
        textView2.setTextColor(this.theme.fg);
        textView2.setTextSize(2, 30.0f);
        textView2.setTypeface(Typeface.SERIF, 2);
        textView2.setPadding(0, 0, 0, dp(6));
        textView2.setFocusable(false);
        this.root.addView(textView2);
        LinearLayout card = card();
        card.addView(toggle("Letzten Sender starten", "Beim Öffnen weiterhören", Prefs.autoplay(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onAutoplay(z);
            }
        }));
        card.addView(line());
        card.addView(toggle("Kopfhörer gezogen → Pause", "Stoppt beim Abziehen", Prefs.pauseUnplug(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onPauseUnplug(z);
            }
        }));
        card.addView(line());
        card.addView(toggle("Automatisch neu verbinden", "Wenn der Stream abbricht", Prefs.reconnect(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onReconnect(z);
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
                SettingsActivity.this.onSleepMin(i);
            }
        }));
        labeled("Schlaf-Timer", card2);
        LinearLayout card3 = card();
        card3.addView(hint(Prefs.isTelevision(this) ? "DPAD: Farbe wählen — gilt überall." : "Tippe eine Farbe — gilt überall."));
        card3.addView(colorGrid());
        labeled("Aussehen", card3);
        LinearLayout card4 = card();
        card4.addView(actionRow("Hörverlauf löschen", "Vorschläge starten wieder bei null", new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.onClearHistory();
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
        scrollView.setFocusable(false);
        scrollView.addView(this.root);
        setContentView(scrollView);
        getWindow().setStatusBarColor(this.theme.bg);
        getWindow().setNavigationBarColor(this.theme.bg);
        if (Prefs.isTelevision(this) && this.firstFocus != null) {
            final View focus = this.firstFocus;
            focus.post(() -> focus.requestFocus());
        }
    }

    private void onBackClick(View view) {
        finish();
    }

    private void onAutoplay(boolean z) {
        Prefs.autoplay(this, z);
    }

    private void onPauseUnplug(boolean z) {
        Prefs.pauseUnplug(this, z);
    }

    private void onReconnect(boolean z) {
        Prefs.reconnect(this, z);
    }

    private void onSleepMin(int i) {
        Prefs.sleepMin(this, i);
        PlayerService.setSleepMinutes(i);
        Toast.makeText(this, i == 0 ? "Timer aus" : i + " Minuten", 0).show();
        build();
    }

    private void onClearHistory() {
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
        rememberFirst(slider);
    }

    private void bufferRow(LinearLayout linearLayout) {
        linearLayout.addView(label("Puffer  " + Prefs.bufferSec(this) + " Sek."));
        linearLayout.addView(hint("Überbrückt kurze Netzaussetzer."));
        linearLayout.addView(chips(new int[]{5, 10, 15, 20, 30, 60}, new String[]{"5", "10", "15", "20", "30", "60"}, Prefs.bufferSec(this), new IntFn() {
            @Override
            public final void set(int i) {
                SettingsActivity.this.onBufferSec(i);
            }
        }));
    }

    private void onBufferSec(int i) {
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
            linearLayout3.setFocusable(true);
            linearLayout3.setClickable(true);
            linearLayout3.setPadding(dp(4), dp(4), dp(4), dp(4));
            linearLayout3.setBackground(focusRowBg());
            attachFocusScale(linearLayout3);
            View view = new View(this);
            int dp = dp(equals ? 38 : 32);
            view.setLayoutParams(new LinearLayout.LayoutParams(dp, dp));
            view.setFocusable(false);
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
            textView.setFocusable(false);
            linearLayout3.addView(textView);
            linearLayout3.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view2) {
                    SettingsActivity.this.onThemePick(theme, view2);
                }
            });
            linearLayout3.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                    v.performClick();
                    return true;
                }
                return false;
            });
            linearLayout2.addView(linearLayout3);
            rememberFirst(linearLayout3);
        }
        return linearLayout;
    }

    private void onThemePick(Theme theme, View view) {
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
            textView.setFocusable(true);
            textView.setClickable(true);
            textView.setBackground(chipFocusBg(z));
            attachFocusScale(textView);
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
            rememberFirst(textView);
        }
        return linearLayout;
    }

    private View toggle(String str, String str2, boolean z, final BoolFn boolFn) {
        final boolean[] zArr = {z};
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(8), dp(10), dp(8), dp(10));
        linearLayout.setFocusable(true);
        linearLayout.setClickable(true);
        linearLayout.setBackground(focusRowBg());
        attachFocusScale(linearLayout);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout2.setFocusable(false);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        textView.setFocusable(false);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(this.theme.muted);
        textView2.setTextSize(2, 12.0f);
        textView2.setFocusable(false);
        linearLayout2.addView(textView);
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2);
        final TextView textView3 = new TextView(this);
        textView3.setFocusable(false);
        paintSwitch(textView3, zArr[0]);
        linearLayout.addView(textView3);
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.onToggleClick(zArr, boolFn, textView3, view);
            }
        });
        linearLayout.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                v.performClick();
                return true;
            }
            return false;
        });
        rememberFirst(linearLayout);
        return linearLayout;
    }

    private void onToggleClick(boolean[] zArr, BoolFn boolFn, TextView textView, View view) {
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
        linearLayout.setPadding(dp(8), dp(10), dp(8), dp(10));
        linearLayout.setFocusable(true);
        linearLayout.setClickable(true);
        linearLayout.setBackground(focusRowBg());
        attachFocusScale(linearLayout);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        textView.setFocusable(false);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(this.theme.muted);
        textView2.setTextSize(2, 12.0f);
        textView2.setFocusable(false);
        linearLayout.addView(textView);
        linearLayout.addView(textView2);
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                runnable.run();
            }
        });
        linearLayout.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                v.performClick();
                return true;
            }
            return false;
        });
        rememberFirst(linearLayout);
        return linearLayout;
    }

    public void checkUpdate() {
        Toast.makeText(this, "Prüfe…", 0).show();
        new Thread(new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.checkUpdateBg();
            }
        }).start();
    }

    private void checkUpdateBg() {
        final UpdateChecker.Info fetch = UpdateChecker.fetch();
        final int installedCode = UpdateChecker.installedCode(this);
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.checkUpdateUi(fetch, installedCode);
            }
        });
    }

    private void checkUpdateUi(UpdateChecker.Info info, int i) {
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
        linearLayout.setFocusable(false);
        return linearLayout;
    }

    private void labeled(String str, View view) {
        TextView textView = new TextView(this);
        textView.setText(str.toUpperCase());
        textView.setTextColor(this.theme.accent);
        textView.setTextSize(2, 11.0f);
        textView.setLetterSpacing(0.14f);
        textView.setPadding(dp(4), dp(18), 0, dp(8));
        textView.setFocusable(false);
        this.root.addView(textView);
        this.root.addView(view);
    }

    private TextView label(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.fg);
        textView.setTextSize(2, 15.0f);
        textView.setPadding(0, dp(10), 0, dp(2));
        textView.setFocusable(false);
        return textView;
    }

    private TextView hint(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.theme.muted);
        textView.setTextSize(2, 12.0f);
        textView.setPadding(0, 0, 0, dp(4));
        textView.setFocusable(false);
        return textView;
    }

    private View line() {
        View view = new View(this);
        view.setBackgroundColor(this.theme.line);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        view.setFocusable(false);
        return view;
    }

    private SeekBar slider(int i, int i2) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(i);
        seekBar.setProgress(i2);
        seekBar.setPadding(dp(2), dp(8), dp(2), dp(4));
        seekBar.setProgressTintList(ColorStateList.valueOf(this.theme.accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(this.theme.accent));
        seekBar.setFocusable(true);
        seekBar.setBackground(focusRowBg());
        attachFocusScale(seekBar);
        return seekBar;
    }

    private StateListDrawable focusRowBg() {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(brighten(this.theme.surface, 0.25f));
        focused.setCornerRadius(dp(12));
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius(dp(12));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private StateListDrawable chipFocusBg(boolean selected) {
        int fill = selected ? this.theme.accent : this.theme.chip;
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(brighten(fill, 0.2f));
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(dp(16));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void attachFocusScale(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.05f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            v.setElevation(hasFocus ? dp(6) : 0);
        });
    }

    private void rememberFirst(View view) {
        if (this.firstFocus == null) {
            this.firstFocus = view;
        }
    }

    private int brighten(int color, float amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, Color.red(color) + Math.round(80 + 120 * amount));
        int g = Math.min(255, Color.green(color) + Math.round(80 + 120 * amount));
        int b = Math.min(255, Color.blue(color) + Math.round(90 + 120 * amount));
        return Color.argb(a == 0 ? 255 : a, r, g, b);
    }

    private int dp(int i) {
        return Math.round(i * this.d);
    }
}
