package fm.welle.radio;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
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
    private ScrollView settingsScroll;
    private int nextControlId;

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
        View oldFocus = getCurrentFocus();
        final int restoreId = oldFocus == null ? View.NO_ID : oldFocus.getId();
        final int restoreScroll = this.settingsScroll == null ? 0 : this.settingsScroll.getScrollY();
        this.nextControlId = 0x00100000;
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
        textView.setText("‹  Zurück");
        textView.setTextColor(this.theme.accent);
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
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(0);
        titleRow.setGravity(16);
        titleRow.setPadding(dp(4), dp(4), 0, dp(2));
        TextView textView2 = new TextView(this);
        textView2.setText("Einstellungen");
        textView2.setTextColor(this.theme.fg);
        textView2.setTextSize(2, 34.0f);
        textView2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textView2.setFocusable(false);
        textView2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        titleRow.addView(textView2);
        titleRow.addView(new WaveView(this, this.theme.accent), new LinearLayout.LayoutParams(dp(96), dp(36)));
        titleRow.setFocusable(false);
        this.root.addView(titleRow);
        LinearLayout card = card();
        card.addView(toggle(R.drawable.ic_set_history, "Letzten Sender starten", "Beim Öffnen weiterhören", Prefs.autoplay(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onAutoplay(z);
            }
        }));
        card.addView(line());
        card.addView(toggle(R.drawable.ic_set_headphones, "Kopfhörer gezogen → Pause", "Stoppt beim Abziehen", Prefs.pauseUnplug(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onPauseUnplug(z);
            }
        }));
        card.addView(line());
        card.addView(toggle(R.drawable.ic_set_link, "Automatisch neu verbinden", "Wenn der Stream abbricht", Prefs.reconnect(this), new BoolFn() {
            @Override
            public final void set(boolean z) {
                SettingsActivity.this.onReconnect(z);
            }
        }));
        card.addView(line());
        volumeRow(card);
        card.addView(line());
        bufferRow(card);
        labeled(R.drawable.ic_set_play, "Wiedergabe", card);
        LinearLayout card2 = card();
        card2.addView(hint(sleepText()));
        card2.addView(chips(new int[]{0, 15, 30, 45, 60, 90}, new String[]{"Aus", "15", "30", "45", "60", "90"}, Prefs.sleepMin(this), new IntFn() {
            @Override
            public final void set(int i) {
                SettingsActivity.this.onSleepMin(i);
            }
        }));
        labeled(R.drawable.ic_set_moon, "Schlaf-Timer", card2);
        LinearLayout card3 = card();
        card3.addView(hint(Prefs.isTelevision(this) ? "DPAD: Farbe wählen — gilt überall." : "Tippe eine Farbe — gilt überall."));
        card3.addView(colorGrid());
        labeled(R.drawable.ic_set_palette, "Aussehen", card3);
        LinearLayout card4 = card();
        card4.addView(actionRow("Hörverlauf löschen", "Vorschläge starten wieder bei null", new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.onClearHistory();
            }
        }));
        labeled(R.drawable.ic_set_delete, "Daten", card4);
        LinearLayout card5 = card();
        card5.addView(actionRow("Auf Update prüfen", "Version " + UpdateChecker.installedName(this), new Runnable() {
            @Override
            public final void run() {
                SettingsActivity.this.checkUpdate();
            }
        }));
        labeled(R.drawable.ic_set_info, "App", card5);
        ScrollView scrollView = new ScrollView(this);
        this.settingsScroll = scrollView;
        scrollView.setBackgroundColor(this.theme.bg);
        scrollView.setFillViewport(true);
        scrollView.setFocusable(false);
        scrollView.addView(this.root);
        setContentView(scrollView);
        getWindow().setStatusBarColor(this.theme.bg);
        getWindow().setNavigationBarColor(this.theme.bg);
        if (Prefs.isTelevision(this) && this.firstFocus != null) {
            View restored = restoreId == View.NO_ID ? null : findViewById(restoreId);
            final View focus = restored == null ? this.firstFocus : restored;
            scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    focus.requestFocus();
                    scrollView.scrollTo(0, restoreScroll);
                }
            });
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
        final TextView label = label("");
        label.setText(valueLabel("Lautstärke", volume + " %"));
        linearLayout.addView(iconRow(R.drawable.ic_set_volume, this.theme.accent, label));
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
                label.setText(valueLabel("Lautstärke", i + " %"));
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
        TextView label = label("");
        label.setText(valueLabel("Puffer", Prefs.bufferSec(this) + " Sek."));
        LinearLayout row = iconRow(R.drawable.ic_set_storage, this.theme.fg, label);
        row.setPadding(dp(6), dp(10), dp(8), dp(4));
        linearLayout.addView(row);
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
            textView.setText(theme.name);
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

    private View toggle(int icon, String str, String str2, boolean z, final BoolFn boolFn) {
        final boolean[] zArr = {z};
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(6), dp(10), dp(8), dp(10));
        linearLayout.setFocusable(true);
        linearLayout.setClickable(true);
        linearLayout.setBackground(focusRowBg());
        attachFocusScale(linearLayout);
        linearLayout.addView(iconTile(icon, this.theme.fg));
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
        final ToggleView toggleView = new ToggleView(this, this.theme, zArr[0]);
        toggleView.setFocusable(false);
        linearLayout.addView(toggleView, new LinearLayout.LayoutParams(dp(52), dp(30)));
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.onToggleClick(zArr, boolFn, toggleView, view);
            }
        });

        rememberFirst(linearLayout);
        return linearLayout;
    }

    private void onToggleClick(boolean[] zArr, BoolFn boolFn, ToggleView toggleView, View view) {
        boolean z = !zArr[0];
        zArr[0] = z;
        boolFn.set(z);
        toggleView.setOn(z);
    }

    private View iconTile(int icon, int tint) {
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(tint));
        image.setPadding(dp(9), dp(9), dp(9), dp(9));
        image.setBackground(this.theme.roundColor(this.theme.chip, dp(11)));
        image.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMarginEnd(dp(12));
        image.setLayoutParams(lp);
        return image;
    }

    private CharSequence valueLabel(String name, String value) {
        SpannableString text = new SpannableString(name + "  " + value);
        text.setSpan(new ForegroundColorSpan(this.theme.accent), name.length() + 2, text.length(), 0);
        text.setSpan(new StyleSpan(Typeface.BOLD), name.length() + 2, text.length(), 0);
        return text;
    }

    private LinearLayout iconRow(int icon, int tint, TextView label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(6), dp(10), dp(8), dp(2));
        row.setFocusable(false);
        row.addView(iconTile(icon, tint));
        label.setPadding(0, 0, 0, 0);
        row.addView(label);
        return row;
    }

    static final class ToggleView extends View {
        private final Theme theme;
        private boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ToggleView(Context context, Theme theme, boolean on) {
            super(context);
            this.theme = theme;
            this.on = on;
        }

        void setOn(boolean on) {
            this.on = on;
            invalidate();
        }

        boolean isOn() {
            return this.on;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(on ? theme.accent : theme.chip);
            canvas.drawRoundRect(0, 0, w, h, h / 2, h / 2, paint);
            float pad = h * 0.12f;
            float r = h / 2 - pad;
            float cx = on ? w - h / 2 : h / 2;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, h / 2, r, paint);
        }
    }

    static final class WaveView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WaveView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            int bars = 22;
            float step = w / bars;
            paint.setStrokeWidth(Math.max(2f, step * 0.42f));
            for (int i = 0; i < bars; i++) {
                float t = i / (float) (bars - 1);
                float envelope = (float) Math.sin(Math.PI * t);
                float wobble = 0.55f + 0.45f * (float) Math.abs(Math.sin(i * 1.7));
                float bar = Math.max(h * 0.12f, h * envelope * wobble);
                paint.setAlpha(Math.round(70 + 150 * envelope));
                float x = step * (i + 0.5f);
                canvas.drawLine(x, (h - bar) / 2, x, (h + bar) / 2, paint);
            }
        }
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

    private void labeled(int icon, String str, LinearLayout card) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding(dp(4), dp(4), dp(4), dp(6));
        header.setFocusable(false);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(this.theme.accent));
        image.setFocusable(false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.setMarginEnd(dp(10));
        header.addView(image, ip);
        TextView textView = new TextView(this);
        textView.setText(str.toUpperCase());
        textView.setTextColor(this.theme.accent);
        textView.setTextSize(2, 12.0f);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textView.setLetterSpacing(0.14f);
        textView.setFocusable(false);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        header.addView(textView);
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setRotation(-90f);
        chevron.setTextColor(this.theme.muted);
        chevron.setTextSize(2, 14.0f);
        chevron.setFocusable(false);
        header.addView(chevron);
        card.addView(header, 0);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        this.root.addView(card, cp);
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
        focused.setStroke(dp(3), this.theme.fg);
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
        focused.setStroke(dp(3), this.theme.fg);
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
        view.setId(this.nextControlId++);
        if (Prefs.isTelevision(this)) view.setMinimumHeight(dp(48));
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setElevation(0f);
    }

    private void rememberFirst(View view) {
        if (this.firstFocus == null) {
            this.firstFocus = view;
        }
    }

    private int brighten(int color, float amount) {
        int target = this.theme.fg;
        float blend = Math.min(0.12f, amount);
        return Color.rgb(
                Math.round(Color.red(color) * (1 - blend) + Color.red(target) * blend),
                Math.round(Color.green(color) * (1 - blend) + Color.green(target) * blend),
                Math.round(Color.blue(color) * (1 - blend) + Color.blue(target) * blend));
    }

    private int dp(int i) {
        return Math.round(i * this.d);
    }
}
