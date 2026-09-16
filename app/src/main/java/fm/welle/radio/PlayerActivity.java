package fm.welle.radio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated now-playing screen. Back returns to the list without stopping playback.
 * All controls are DPAD-focusable for Android TV / Fire TV remotes.
 */
public class PlayerActivity extends Activity {
    private boolean television;
    private float density;
    private Theme theme;
    private Favorites favorites;
    private ImageView art;
    private TextView title;
    private TextView meta;
    private TextView status;
    private TextView favBtn;
    private ImageButton playBtn;
    private ImageButton prevBtn;
    private ImageButton nextBtn;
    private TextView volLabel;
    private TextView sleepHint;
    private TextView[] sleepChips;
    private TextView[] bufferChips;
    private final int[] sleepValues = {0, 15, 30, 45, 60, 90};
    private final int[] bufferValues = {5, 10, 15, 30, 60};
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int playRequest;
    private final Runnable sleepTicker = new Runnable() {
        @Override public void run() {
            refreshSleepHint();
            ui.postDelayed(this, 30000);
        }
    };
    private final BroadcastReceiver stateRx = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_player);
        television = Prefs.isTelevision(this);
        density = getResources().getDisplayMetrics().density;
        favorites = new Favorites(this);
        ArtLoader.init(this);

        art = findViewById(R.id.player_art);
        title = findViewById(R.id.player_title);
        meta = findViewById(R.id.player_meta);
        status = findViewById(R.id.player_status);
        favBtn = findViewById(R.id.player_fav);
        playBtn = findViewById(R.id.player_play);
        prevBtn = findViewById(R.id.player_prev);
        nextBtn = findViewById(R.id.player_next);
        volLabel = findViewById(R.id.vol_label);
        sleepHint = findViewById(R.id.sleep_hint);

        sleepChips = new TextView[]{
                findViewById(R.id.sleep_aus), findViewById(R.id.sleep_15), findViewById(R.id.sleep_30),
                findViewById(R.id.sleep_45), findViewById(R.id.sleep_60), findViewById(R.id.sleep_90)
        };
        bufferChips = new TextView[]{
                findViewById(R.id.buf_5), findViewById(R.id.buf_10), findViewById(R.id.buf_15),
                findViewById(R.id.buf_30), findViewById(R.id.buf_60)
        };

        findViewById(R.id.player_back).setOnClickListener(v -> finish());
        findViewById(R.id.player_settings).setOnClickListener(v -> openSettings());
        findViewById(R.id.player_open_settings).setOnClickListener(v -> openSettings());
        favBtn.setOnClickListener(v -> toggleFavorite());
        playBtn.setOnClickListener(v -> togglePlay());
        prevBtn.setOnClickListener(v -> playNeighbor(false));
        nextBtn.setOnClickListener(v -> playNeighbor(true));
        findViewById(R.id.vol_down).setOnClickListener(v -> changeVolume(-5));
        findViewById(R.id.vol_up).setOnClickListener(v -> changeVolume(5));

        for (int i = 0; i < sleepChips.length; i++) {
            final int minutes = sleepValues[i];
            sleepChips[i].setOnClickListener(v -> setSleep(minutes));
        }
        for (int i = 0; i < bufferChips.length; i++) {
            final int seconds = bufferValues[i];
            bufferChips[i].setOnClickListener(v -> setBuffer(seconds));
        }

        if (television) {
            findViewById(R.id.player_root).setPadding(dp(36), dp(18), dp(36), dp(32));
            findViewById(R.id.vol_hint).setVisibility(View.VISIBLE);
            findViewById(R.id.player_tv_help).setVisibility(View.VISIBLE);
            View artView = art;
            artView.getLayoutParams().width = dp(240);
            artView.getLayoutParams().height = dp(240);
        }

        applyTheme();
        refresh();
        final View focusPlay = playBtn;
        findViewById(R.id.player_scroll).addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);
                focusPlay.requestFocus();
            }
        });
        focusPlay.post(focusPlay::requestFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(MainActivity.STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateRx, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateRx, filter);
        }
        applyTheme();
        refresh();
        ui.removeCallbacks(sleepTicker);
        ui.post(sleepTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stateRx); } catch (Exception ignored) { }
        ui.removeCallbacks(sleepTicker);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Close player screen; keep playback running (service continues).
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (PlayerService.playing || PlayerService.buffering) togglePlay();
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!PlayerService.playing && !PlayerService.buffering) togglePlay();
            } else {
                togglePlay();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            playNeighbor(false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            playNeighbor(true);
            return true;
        }
        if (isFavoriteKey(keyCode)) {
            toggleFavorite();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isFavoriteKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BOOKMARK;
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void togglePlay() {
        if (PlayerService.current == null) {
            Toast.makeText(this, "Kein Sender aktiv", Toast.LENGTH_SHORT).show();
            return;
        }
        PlayerService.toggle(this);
    }

    private void toggleFavorite() {
        Station station = PlayerService.current;
        if (station == null) {
            Toast.makeText(this, "Kein Sender aktiv", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean now = !favorites.has(station.id);
        favorites.toggle(station);
        Toast.makeText(this, now ? "Zu Favoriten" : "Favorit entfernt", Toast.LENGTH_SHORT).show();
        refreshFavorite();
        MainActivity.broadcastState(this);
    }

    private void playNeighbor(boolean next) {
        Station station = next ? PlayQueue.next() : PlayQueue.previous();
        if (station == null) {
            Toast.makeText(this, next ? "Kein nächster Sender" : "Kein vorheriger Sender", Toast.LENGTH_SHORT).show();
            refreshNav();
            return;
        }
        playStation(station);
    }

    private void playStation(Station station) {
        final int request = ++playRequest;
        Prefs.saveLast(this, station);
        Toast.makeText(this, "Verbindet " + station.name, Toast.LENGTH_SHORT).show();
        title.setText(station.name);
        meta.setText(station.meta());
        status.setText("Verbindet…");
        io.execute(() -> {
            String url = RadioApi.resolve(station.id, station.url);
            ui.post(() -> {
                if (request == playRequest && !isDestroyed()) {
                    PlayerService.play(this, station, url);
                    PlayQueue.syncTo(station);
                    refresh();
                }
            });
        });
    }

    private void setSleep(int minutes) {
        Prefs.sleepMin(this, minutes);
        PlayerService.setSleepMinutes(minutes);
        Toast.makeText(this, minutes == 0 ? "Timer aus" : minutes + " Minuten", Toast.LENGTH_SHORT).show();
        paintSleepChips();
        refreshSleepHint();
    }

    private void setBuffer(int seconds) {
        Prefs.bufferSec(this, seconds);
        PlayerService.applyLiveBuffer();
        Toast.makeText(this, "Puffer " + Prefs.bufferSec(this) + " s", Toast.LENGTH_SHORT).show();
        paintBufferChips();
    }

    private void changeVolume(int delta) {
        int next = Math.max(0, Math.min(100, Prefs.volume(this) + delta));
        Prefs.volume(this, next);
        PlayerService.applyLiveVolume();
        // Also nudge system music stream slightly so TV remotes feel consistent.
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null && television) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        delta > 0 ? AudioManager.ADJUST_SAME : AudioManager.ADJUST_SAME, 0);
            }
        } catch (Exception ignored) { }
        refreshVolume();
    }

    private void refresh() {
        Station station = PlayerService.current;
        if (station == null) {
            title.setText("Kein Sender");
            meta.setText("Wähle einen Sender zum Hören");
            status.setText("");
            art.setImageResource(R.drawable.ic_logo);
            playBtn.setImageResource(android.R.drawable.ic_media_play);
        } else {
            PlayQueue.syncTo(station);
            title.setText(station.name);
            String metaText = station.meta();
            if (metaText == null || metaText.isEmpty()) metaText = "Live";
            meta.setText(metaText);
            if (PlayerService.buffering) {
                status.setText("Puffer…");
                playBtn.setImageResource(android.R.drawable.ic_media_pause);
            } else if (PlayerService.playing) {
                status.setText("Spielt");
                playBtn.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                status.setText("Pausiert");
                playBtn.setImageResource(android.R.drawable.ic_media_play);
            }
            art.setImageResource(R.drawable.ic_logo);
            if (station.favicon != null && !station.favicon.isEmpty()) {
                ArtLoader.load(station.favicon, bitmap -> {
                    if (bitmap != null && PlayerService.current != null
                            && station.id.equals(PlayerService.current.id)) {
                        art.setImageBitmap(bitmap);
                    }
                });
            }
        }
        refreshFavorite();
        refreshNav();
        refreshVolume();
        paintSleepChips();
        paintBufferChips();
        refreshSleepHint();
    }

    private void refreshFavorite() {
        Station station = PlayerService.current;
        boolean fav = station != null && favorites.has(station.id);
        favBtn.setText(fav ? "★ Favorit" : "☆ Favorit");
        if (theme != null) {
            favBtn.setTextColor(fav ? theme.onAccent : theme.fg);
            paintChip(favBtn, fav);
        }
    }

    private void refreshNav() {
        boolean hasPrev = PlayQueue.hasPrevious();
        boolean hasNext = PlayQueue.hasNext();
        prevBtn.setEnabled(hasPrev);
        nextBtn.setEnabled(hasNext);
        prevBtn.setAlpha(hasPrev ? 1f : 0.35f);
        nextBtn.setAlpha(hasNext ? 1f : 0.35f);
        // Keep focusable so DPAD never dead-ends; OK shows a toast when empty.
        prevBtn.setFocusable(true);
        nextBtn.setFocusable(true);
    }

    private void refreshVolume() {
        volLabel.setText(Prefs.volume(this) + " %");
    }

    private void refreshSleepHint() {
        long left = PlayerService.sleepUntil - System.currentTimeMillis();
        if (left > 0) {
            sleepHint.setText("Stoppt in " + Math.max(1L, left / 60000) + " Min");
        } else {
            sleepHint.setText("Radio schaltet sich danach aus.");
        }
    }

    private void paintSleepChips() {
        int selected = Prefs.sleepMin(this);
        long left = PlayerService.sleepUntil - System.currentTimeMillis();
        if (left <= 0 && selected != 0) {
            // Prefer active timer remaining bucket when set via service.
        }
        for (int i = 0; i < sleepChips.length; i++) {
            boolean on = sleepValues[i] == selected;
            paintChip(sleepChips[i], on);
            sleepChips[i].setTextColor(on && theme != null ? theme.onAccent : (theme != null ? theme.fg : Color.WHITE));
        }
    }

    private void paintBufferChips() {
        int selected = Prefs.bufferSec(this);
        for (int i = 0; i < bufferChips.length; i++) {
            boolean on = bufferValues[i] == selected;
            paintChip(bufferChips[i], on);
            bufferChips[i].setTextColor(on && theme != null ? theme.onAccent : (theme != null ? theme.fg : Color.WHITE));
        }
    }

    private void paintChip(TextView chip, boolean selected) {
        if (theme == null) {
            chip.setBackgroundResource(R.drawable.bg_player_chip);
            chip.setSelected(selected);
            return;
        }
        int fill = selected ? theme.accent : theme.chip;
        chip.setBackground(focusableRound(fill, brighten(fill, 0.22f), theme.fg, density * 22f, 3));
    }

    private void applyTheme() {
        theme = Theme.current(this);
        findViewById(R.id.player_root).setBackgroundColor(theme.bg);
        findViewById(R.id.player_scroll).setBackgroundColor(theme.bg);
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
        title.setTextColor(theme.fg);
        meta.setTextColor(theme.muted);
        status.setTextColor(theme.muted);
        sleepHint.setTextColor(theme.muted);
        volLabel.setTextColor(theme.fg);
        ((TextView) findViewById(R.id.vol_hint)).setTextColor(theme.muted);
        ((TextView) findViewById(R.id.player_tv_help)).setTextColor(theme.muted);
        ((TextView) findViewById(R.id.player_back)).setTextColor(theme.muted);
        ((TextView) findViewById(R.id.player_back)).setBackground(
                focusableRound(theme.chip, brighten(theme.chip, 0.22f), theme.fg, density * 22f, 3));
        ((ImageButton) findViewById(R.id.player_settings)).setColorFilter(theme.fg);
        ((ImageButton) findViewById(R.id.player_settings)).setBackground(
                focusableOval(Color.TRANSPARENT, theme.fg, theme.chip));
        playBtn.setBackground(focusableOval(theme.accent, theme.fg, theme.accent));
        playBtn.setColorFilter(theme.onAccent);
        prevBtn.setColorFilter(theme.fg);
        nextBtn.setColorFilter(theme.fg);
        prevBtn.setBackground(focusableOval(theme.chip, theme.fg, brighten(theme.chip, 0.2f)));
        nextBtn.setBackground(focusableOval(theme.chip, theme.fg, brighten(theme.chip, 0.2f)));
        TextView volDown = findViewById(R.id.vol_down);
        TextView volUp = findViewById(R.id.vol_up);
        volDown.setTextColor(theme.fg);
        volUp.setTextColor(theme.fg);
        volDown.setBackground(focusableOval(theme.chip, theme.fg, brighten(theme.chip, 0.2f)));
        volUp.setBackground(focusableOval(theme.chip, theme.fg, brighten(theme.chip, 0.2f)));
        TextView more = findViewById(R.id.player_open_settings);
        more.setTextColor(theme.fg);
        more.setBackground(focusableRound(theme.chip, brighten(theme.chip, 0.22f), theme.fg, density * 22f, 3));
        roundClip(art, density * 16f);
        paintSleepChips();
        paintBufferChips();
        refreshFavorite();
    }

    private StateListDrawable focusableOval(int fill, int focusedStroke, int focusedFill) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(fill);
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.OVAL);
        focused.setColor(focusedFill);
        focused.setStroke(dp(4), focusedStroke);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private StateListDrawable focusableRound(int fill, int focusedFill, int stroke, float radius, int strokeDp) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(radius);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(focusedFill);
        focused.setCornerRadius(radius);
        focused.setStroke(dp(strokeDp), stroke);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private int brighten(int color, float amount) {
        int target = theme == null ? Color.WHITE : theme.fg;
        float blend = Math.min(0.12f, amount);
        return Color.rgb(
                Math.round(Color.red(color) * (1 - blend) + Color.red(target) * blend),
                Math.round(Color.green(color) * (1 - blend) + Color.green(target) * blend),
                Math.round(Color.blue(color) * (1 - blend) + Color.blue(target) * blend));
    }

    private void roundClip(ImageView imageView, final float radius) {
        imageView.setClipToOutline(true);
        imageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) outline.setRoundRect(0, 0, 1, 1, radius);
                else outline.setRoundRect(0, 0, w, h, radius);
            }
        });
        imageView.invalidateOutline();
    }

    private int dp(int value) {
        return Math.round(value * density);
    }
}
