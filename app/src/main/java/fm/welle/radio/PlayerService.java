package fm.welle.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

public class PlayerService extends Service {
    public static final String ACTION_PLAY = "fm.welle.radio.PLAY";
    public static final String ACTION_STOP = "fm.welle.radio.STOP";
    public static final String ACTION_TOGGLE = "fm.welle.radio.TOGGLE";
    public static final String EXTRA_FAVICON = "favicon";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_META = "meta";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URL = "url";

    public static boolean buffering;
    public static Station current;
    public static boolean playing;
    public static long sleepUntil;
    private static PlayerService self;

    private Bitmap art;
    private AudioManager audioManager;
    private boolean ducked;
    private AudioFocusRequest focusReq;
    private boolean pausedByFocus;
    private ExoPlayer player;
    private int retries;
    private MediaSession session;
    private WifiManager.WifiLock wifiLock;
    private String pendingUrl = "";
    private final Handler timer = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.AUDIO_BECOMING_NOISY".equals(intent.getAction())
                    && Prefs.pauseUnplug(PlayerService.this)
                    && playing && player != null) {
                player.pause();
                playing = false;
                startForeground(7, notification("Pausiert"));
                MainActivity.broadcastState(PlayerService.this);
            }
        }
    };

    private final Runnable stopForSleep = () -> {
        sleepUntil = 0L;
        Toast.makeText(this, "Schlaf-Timer: Wiedergabe beendet", Toast.LENGTH_SHORT).show();
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    };

    private final Player.Listener exoListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                buffering = true;
                startForeground(7, notification("Puffer…"));
                updateSession();
                MainActivity.broadcastState(PlayerService.this);
            } else if (state == Player.STATE_READY) {
                buffering = false;
                playing = player != null && player.getPlayWhenReady();
                retries = 0;
                pausedByFocus = false;
                applyPlayerVolume();
                startForeground(7, notification(current != null ? current.name : "Live"));
                updateSession();
                MainActivity.broadcastState(PlayerService.this);
            } else if (state == Player.STATE_ENDED) {
                playing = false;
                buffering = false;
                if (Prefs.reconnect(PlayerService.this) && current != null
                        && current.url != null && !current.url.isEmpty()) {
                    timer.postDelayed(() -> startPlayback(current.url), 1500L);
                    startForeground(7, notification("Verbindet neu…"));
                }
                updateSession();
                MainActivity.broadcastState(PlayerService.this);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            buffering = false;
            playing = false;
            if (Prefs.reconnect(PlayerService.this) && retries < 5
                    && pendingUrl != null && !pendingUrl.isEmpty()) {
                retries++;
                timer.postDelayed(() -> startPlayback(pendingUrl), 2500L);
                startForeground(7, notification("Verbindet neu…"));
                MainActivity.broadcastState(PlayerService.this);
                return;
            }
            Toast.makeText(PlayerService.this, "Sender konnte nicht geladen werden", Toast.LENGTH_SHORT).show();
            updateSession();
            MainActivity.broadcastState(PlayerService.this);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void play(Context context, Station station, String url) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra(EXTRA_ID, station.id);
        intent.putExtra(EXTRA_NAME, station.name);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_META, station.meta());
        intent.putExtra(EXTRA_FAVICON, station.favicon);
        context.startForegroundService(intent);
    }

    public static void toggle(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_TOGGLE);
        context.startForegroundService(intent);
    }

    public static void applyLiveVolume() {
        if (self != null) self.applyPlayerVolume();
    }

    public static void applyLiveBuffer() {
        if (self == null || self.pendingUrl == null || self.pendingUrl.isEmpty()) return;
        if (playing || buffering || self.player != null) {
            self.startPlayback(self.pendingUrl);
        }
    }

    public static void setSleepMinutes(int minutes) {
        if (self == null) {
            sleepUntil = minutes > 0 ? System.currentTimeMillis() + minutes * 60000L : 0L;
            return;
        }
        self.timer.removeCallbacks(self.stopForSleep);
        if (minutes <= 0) {
            sleepUntil = 0L;
            if (current != null) {
                self.startForeground(7, self.notification(playing ? current.name : "Pausiert"));
            }
            return;
        }
        long ms = minutes * 60000L;
        sleepUntil = System.currentTimeMillis() + ms;
        self.timer.postDelayed(self.stopForSleep, ms);
        if (current != null) {
            self.startForeground(7, self.notification(current.name));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        ArtLoader.init(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        session = new MediaSession(this, "RadioWelt");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { toggle(PlayerService.this); }
            @Override public void onPause() { toggle(PlayerService.this); }
            @Override public void onStop() {
                startService(new Intent(PlayerService.this, PlayerService.class).setAction(ACTION_STOP));
            }
        });
        session.setActive(true);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        long remaining = sleepUntil - System.currentTimeMillis();
        if (remaining > 0) timer.postDelayed(stopForSleep, remaining);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            Station station = new Station();
            station.id = intent.getStringExtra(EXTRA_ID);
            station.name = intent.getStringExtra(EXTRA_NAME);
            station.url = intent.getStringExtra(EXTRA_URL);
            station.country = intent.getStringExtra(EXTRA_META);
            station.favicon = intent.getStringExtra(EXTRA_FAVICON);
            current = station;
            pendingUrl = station.url;
            buffering = true;
            playing = false;
            startForeground(7, notification("Verbindet…"));
            startPlayback(pendingUrl);
            loadArt(station);
            updateSession();
            MainActivity.broadcastState(this);
            Prefs.saveLast(this, station);
        } else if (ACTION_TOGGLE.equals(action)) {
            if (player != null && playing) {
                player.pause();
                playing = false;
                startForeground(7, notification("Pausiert"));
            } else if (player != null && current != null) {
                player.play();
                playing = true;
                startForeground(7, notification(current.name));
            } else if (current != null) {
                startPlayback(current.url);
            }
            MainActivity.broadcastState(this);
            updateSession();
        } else if (ACTION_STOP.equals(action)) {
            release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startPlayback(String url) {
        releasePlayerOnly();
        try {
            int bufferSec = Prefs.bufferSec(this);
            int minBuf = bufferSec * 1000;
            ExoPlayer exo = new ExoPlayer.Builder(this)
                    .setLoadControl(new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                    Math.max(minBuf, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS),
                                    Math.max(bufferSec * 3000, 30000),
                                    Math.min(1500, minBuf),
                                    Math.min(3000, minBuf))
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build())
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(
                            new DefaultHttpDataSource.Factory()
                                    .setUserAgent("RadioWelt/2.0 (Android)")
                                    .setConnectTimeoutMs(10000)
                                    .setReadTimeoutMs(10000)
                                    .setAllowCrossProtocolRedirects(true)))
                    .setWakeMode(2)
                    .build();
            player = exo;
            player.addListener(exoListener);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.setPlayWhenReady(true);
            player.prepare();
            applyPlayerVolume();
            requestFocus();
            lockWifi();
        } catch (Exception e) {
            buffering = false;
            playing = false;
            Toast.makeText(this, "Stream nicht spielbar", Toast.LENGTH_SHORT).show();
            MainActivity.broadcastState(this);
        }
    }

    private void applyPlayerVolume() {
        if (player == null) return;
        float vol = Prefs.volume(this) / 100f;
        if (ducked) vol *= 0.18f;
        try {
            player.setVolume(vol);
        } catch (Exception ignored) {
        }
    }

    private void requestFocus() {
        if (audioManager == null) audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this::onAudioFocus, timer)
                    .build();
            ducked = audioManager.requestAudioFocus(focusReq) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            applyPlayerVolume();
        } catch (Exception ignored) {
        }
    }

    private void abandonFocus() {
        if (audioManager != null && focusReq != null) {
            try {
                audioManager.abandonAudioFocusRequest(focusReq);
            } catch (Exception ignored) {
            }
        }
        ducked = false;
    }

    private void onAudioFocus(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            ducked = false;
            applyPlayerVolume();
            if (pausedByFocus && player != null && current != null) {
                try {
                    player.play();
                    playing = true;
                    pausedByFocus = false;
                    startForeground(7, notification(current.name));
                    updateSession();
                    MainActivity.broadcastState(this);
                } catch (Exception ignored) {
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            ducked = true;
            applyPlayerVolume();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            ducked = false;
            if (playing && player != null) {
                player.pause();
                playing = false;
                pausedByFocus = true;
                startForeground(7, notification("Pausiert"));
                updateSession();
                MainActivity.broadcastState(this);
            }
        }
    }

    private void loadArt(Station station) {
        art = ArtLoader.peek(station == null ? "" : station.favicon);
        if (station == null || station.favicon == null || station.favicon.isEmpty()) return;
        ArtLoader.load(station.favicon, bmp -> {
            if (bmp == null || current == null || !station.favicon.equals(current.favicon)) return;
            art = bmp;
            startForeground(7, notification(playing ? current.name : "Pausiert"));
            updateSession();
        });
    }

    private void updateSession() {
        if (session == null) return;
        int state = buffering ? PlaybackState.STATE_BUFFERING
                : (playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1f : 0f)
                .build());
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, current != null ? current.name : "Radio Welt 2.0")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Radio Welt 2.0")
                .putString(MediaMetadata.METADATA_KEY_ALBUM,
                        (current == null || current.country == null) ? "Live" : current.country);
        if (art != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(meta.build());
        session.setActive(true);
    }

    private void lockWifi() {
        if (wifiLock == null || !wifiLock.isHeld()) {
            wifiLock = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "welle");
            wifiLock.acquire();
        }
    }

    private void releasePlayerOnly() {
        if (player != null) {
            try {
                player.removeListener(exoListener);
                player.stop();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void release() {
        abandonFocus();
        releasePlayerOnly();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        wifiLock = null;
        playing = false;
        buffering = false;
        ducked = false;
        if (session != null) {
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, -1, 0f).build());
            session.setActive(false);
        }
        MainActivity.broadcastState(this);
    }

    private Notification notification(String ignored) {
        NotificationChannel channel = new NotificationChannel("media2", "Wiedergabe", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent togglePi = PendingIntent.getService(this, 1,
                new Intent(this, PlayerService.class).setAction(ACTION_TOGGLE), flags);
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, PlayerService.class).setAction(ACTION_STOP), flags);

        String title = (current == null || current.name == null) ? "Radio Welt 2.0" : current.name;
        String text;
        if (sleepUntil > System.currentTimeMillis() && playing) {
            text = "Timer " + Math.max(1L, (sleepUntil - System.currentTimeMillis()) / 60000) + " Min · Radio Welt 2.0";
        } else if (buffering) {
            text = "Verbindet…";
        } else if (playing) {
            text = "Radio Welt 2.0 · Live";
        } else {
            text = "Pausiert";
        }

        Bitmap large = art;
        if (large == null) {
            try {
                large = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            } catch (Exception ignored2) {
            }
        }

        Notification.Builder builder = new Notification.Builder(this, "media2")
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Radio Welt 2.0")
                .setContentIntent(open)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(new Notification.Action.Builder(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", togglePi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi).build());
        if (large != null) builder.setLargeIcon(large);

        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0);
        if (session != null) style.setMediaSession(session.getSessionToken());
        builder.setStyle(style);
        return builder.build();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(noisy);
        } catch (Exception ignored) {
        }
        timer.removeCallbacksAndMessages(null);
        if (session != null) {
            try {
                session.setActive(false);
                session.release();
            } catch (Exception ignored) {
            }
            session = null;
        }
        if (self == this) self = null;
        release();
        super.onDestroy();
    }
}
