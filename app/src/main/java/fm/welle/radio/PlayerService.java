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
import android.content.pm.ServiceInfo;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.function.Consumer;
import org.json.JSONObject;

// Media3: DefaultLoadControl/DefaultMediaSourceFactory-Konfiguration ist als
// @UnstableApi markiert (API kann sich zwischen Versionen ändern) – bewusst genutzt.
@OptIn(markerClass = UnstableApi.class)
public class PlayerService extends Service {
    public static final String ACTION_PLAY = "fm.welle.radio.PLAY";
    public static final String ACTION_STOP = "fm.welle.radio.STOP";
    public static final String ACTION_TOGGLE = "fm.welle.radio.TOGGLE";
    public static final String EXTRA_FAVICON = "favicon";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_JSON = "json";
    public static final String EXTRA_META = "meta";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URL = "url";
    public static boolean buffering;
    public static Station current;
    public static boolean playing;
    public static String trackTitle = "";
    private static PlayerService self;
    public static long sleepUntil;
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
            if ("android.media.AUDIO_BECOMING_NOISY".equals(intent.getAction()) && Prefs.pauseUnplug(PlayerService.this) && PlayerService.playing && PlayerService.this.player != null) {
                PlayerService.this.player.pause();
                PlayerService.playing = false;
                ListenHistory.get(PlayerService.this).stop();
                PlayerService playerService = PlayerService.this;
                playerService.startFg(playerService.notification("Pausiert"));
                MainActivity.broadcastState(PlayerService.this);
                PlayerService.this.updateSession();
            }
        }
    };
    private final Runnable stopForSleep = new Runnable() {
        @Override
        public final void run() {
            PlayerService.this.lambda$new$0();
        }
    };
    private final Runnable historyBeat = new Runnable() {
        @Override
        public final void run() {
            PlayerService.this.lambda$new$1();
        }
    };
    private final Player.Listener exoListener = new AnonymousClass3();

    /** Vordergrund-Dienst immer mit Typ mediaPlayback starten (Pflicht ab Android 14). */
    private void startFg(Notification notification) {
        int type = android.os.Build.VERSION.SDK_INT >= 29
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, 7, notification, type);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void play(Context context, Station station, String str) {
        Intent intent = new Intent(context, (Class<?>) PlayerService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra("id", station.id);
        intent.putExtra(EXTRA_NAME, station.name);
        intent.putExtra(EXTRA_URL, str);
        intent.putExtra(EXTRA_META, station.meta());
        intent.putExtra(EXTRA_FAVICON, station.favicon);
        intent.putExtra(EXTRA_JSON, station.toJson().toString());
        context.startForegroundService(intent);
    }

    public static void toggle(Context context) {
        Intent intent = new Intent(context, (Class<?>) PlayerService.class);
        intent.setAction(ACTION_TOGGLE);
        context.startForegroundService(intent);
    }

    public static void applyLiveVolume() {
        PlayerService playerService = self;
        if (playerService != null) {
            playerService.applyPlayerVolume();
        }
    }

    public static void applyLiveBuffer() {
        String str;
        PlayerService playerService = self;
        if (playerService == null || (str = playerService.pendingUrl) == null || str.isEmpty()) {
            return;
        }
        if (playing || buffering || self.player != null) {
            PlayerService playerService2 = self;
            playerService2.startPlayback(playerService2.pendingUrl);
        }
    }

    public static void setSleepMinutes(int i) {
        PlayerService playerService = self;
        if (playerService == null) {
            sleepUntil = i > 0 ? System.currentTimeMillis() + (i * 60000) : 0L;
            return;
        }
        playerService.timer.removeCallbacks(playerService.stopForSleep);
        if (i <= 0) {
            sleepUntil = 0L;
            Station station = current;
            if (station != null) {
                PlayerService playerService2 = self;
                playerService2.startFg(playerService2.notification(playing ? station.name : "Pausiert"));
                return;
            }
            return;
        }
        long j = i * 60000;
        sleepUntil = System.currentTimeMillis() + j;
        PlayerService playerService3 = self;
        playerService3.timer.postDelayed(playerService3.stopForSleep, j);
        Station station2 = current;
        if (station2 != null) {
            PlayerService playerService4 = self;
            playerService4.startFg(playerService4.notification(station2.name));
        }
    }

        public /* synthetic */ void lambda$new$0() {
        sleepUntil = 0L;
        Toast.makeText(this, "Schlaf-Timer: Wiedergabe beendet", Toast.LENGTH_SHORT).show();
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        ArtLoader.init(this);
        this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        MediaSession mediaSession = new MediaSession(this, "RadioWelt");
        this.session = mediaSession;
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                PlayerService.toggle(PlayerService.this);
            }

            @Override
            public void onPause() {
                PlayerService.toggle(PlayerService.this);
            }

            @Override
            public void onStop() {
                PlayerService.this.startService(new Intent(PlayerService.this, (Class<?>) PlayerService.class).setAction(PlayerService.ACTION_STOP));
            }
        });
        this.session.setActive(true);
        // AUDIO_BECOMING_NOISY ist ein geschützter System-Broadcast; er kommt auch bei
        // RECEIVER_NOT_EXPORTED an. Andere Apps können den Receiver so nicht auslösen.
        // Unter Android 13 gibt es das Flag nicht (und es wird dort nicht verlangt).
        IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.noisy, noisyFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(this.noisy, noisyFilter);
        }
        long currentTimeMillis = sleepUntil - System.currentTimeMillis();
        if (currentTimeMillis > 0) {
            this.timer.postDelayed(this.stopForSleep, currentTimeMillis);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent == null) {
            // A sticky restart after the task was removed must not leave playback running.
            if (this.player == null) {
                closeCompletely();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            Station stationFrom = stationFrom(intent);
            if (current == null || stationFrom.id == null || !stationFrom.id.equals(current.id)) {
                trackTitle = "";
            }
            current = stationFrom;
            this.pendingUrl = stationFrom.url;
            buffering = true;
            playing = false;
            ListenHistory.get(this).start(stationFrom);
            beatHistory();
            startFg(notification("Verbindet…"));
            startPlayback(this.pendingUrl);
            loadArt(stationFrom);
            updateSession();
            MainActivity.broadcastState(this);
            Prefs.saveLast(this, stationFrom);
        } else if (ACTION_TOGGLE.equals(action)) {
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer != null && playing) {
                exoPlayer.pause();
                playing = false;
                ListenHistory.get(this).stop();
                startFg(notification("Pausiert"));
            } else if (exoPlayer != null && current != null) {
                exoPlayer.play();
                playing = true;
                ListenHistory.get(this).start(current);
                beatHistory();
                startFg(notification(current.name));
            } else if (current != null) {
                ListenHistory.get(this).start(current);
                beatHistory();
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

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app out of recents closes the task and the music.
        closeCompletely();
    }

    private void closeCompletely() {
        this.timer.removeCallbacksAndMessages(null);
        sleepUntil = 0;
        current = null;
        release();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception unused) {
        }
        stopSelf();
    }

    private Station stationFrom(Intent intent) {
        String stringExtra = intent.getStringExtra(EXTRA_JSON);
        if (stringExtra != null && !stringExtra.isEmpty()) {
            try {
                Station from = Station.from(new JSONObject(stringExtra));
                if (from != null) {
                    String stringExtra2 = intent.getStringExtra(EXTRA_URL);
                    if (stringExtra2 != null && !stringExtra2.isEmpty()) {
                        from.url = stringExtra2;
                    }
                    return from;
                }
            } catch (Exception unused) {
            }
        }
        Station station = new Station();
        station.id = intent.getStringExtra("id");
        station.name = intent.getStringExtra(EXTRA_NAME);
        station.url = intent.getStringExtra(EXTRA_URL);
        station.country = intent.getStringExtra(EXTRA_META);
        station.favicon = intent.getStringExtra(EXTRA_FAVICON);
        return station;
    }

        public /* synthetic */ void lambda$new$1() {
        if (playing) {
            ListenHistory.get(this).flush();
            this.timer.postDelayed(this.historyBeat, C.DEFAULT_SEEK_FORWARD_INCREMENT_MS);
        }
    }

    private void beatHistory() {
        this.timer.removeCallbacks(this.historyBeat);
        this.timer.postDelayed(this.historyBeat, C.DEFAULT_SEEK_FORWARD_INCREMENT_MS);
    }

        public void startPlayback(String str) {
        releasePlayerOnly();
        try {
            int bufferSec = Prefs.bufferSec(this);
            int i = bufferSec * 1000;
            ExoPlayer build = new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(Math.max(i, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS), Math.max(bufferSec * 3000, 30000), Math.min(1500, i), Math.min(3000, i)).setPrioritizeTimeOverSizeThresholds(true).build()).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultHttpDataSource.Factory().setUserAgent(AppInfo.userAgent()).setConnectTimeoutMs(10000).setReadTimeoutMs(10000).setAllowCrossProtocolRedirects(true))).setWakeMode(2).build();
            this.player = build;
            build.addListener(this.exoListener);
            this.player.setMediaItem(MediaItem.fromUri(Uri.parse(str)));
            this.player.setPlayWhenReady(true);
            this.player.prepare();
            applyPlayerVolume();
            requestFocus();
            lockWifi();
        } catch (Exception unused) {
            buffering = false;
            playing = false;
            Toast.makeText(this, "Stream nicht spielbar", Toast.LENGTH_SHORT).show();
            MainActivity.broadcastState(this);
        }
    }

    /* renamed from: fm.welle.radio.PlayerService$3, reason: invalid class name */
    class AnonymousClass3 implements Player.Listener {
        AnonymousClass3() {
        }

        @Override
        public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata mediaMetadata) {
            CharSequence title = mediaMetadata.title;
            PlayerService.this.applyTrackTitle(title == null ? "" : title.toString());
        }

        @Override
        public void onPlaybackStateChanged(int i) {
            if (i == 2) {
                PlayerService.buffering = true;
                PlayerService playerService = PlayerService.this;
                playerService.startFg(playerService.notification("Puffer…"));
                PlayerService.this.updateSession();
                MainActivity.broadcastState(PlayerService.this);
                return;
            }
            if (i == 3) {
                PlayerService.buffering = false;
                PlayerService.playing = PlayerService.this.player != null && PlayerService.this.player.getPlayWhenReady();
                PlayerService.this.retries = 0;
                PlayerService.this.pausedByFocus = false;
                PlayerService.this.applyPlayerVolume();
                PlayerService playerService2 = PlayerService.this;
                playerService2.startFg(playerService2.notification(PlayerService.current != null ? PlayerService.current.name : "Live"));
                PlayerService.this.updateSession();
                MainActivity.broadcastState(PlayerService.this);
                return;
            }
            if (i == 4) {
                PlayerService.playing = false;
                PlayerService.buffering = false;
                if (Prefs.reconnect(PlayerService.this) && PlayerService.current != null && PlayerService.current.url != null && !PlayerService.current.url.isEmpty()) {
                    PlayerService.this.timer.postDelayed(new Runnable() {
                        @Override
                        public final void run() {
                            PlayerService.AnonymousClass3.this.lambda$onPlaybackStateChanged$0();
                        }
                    }, 1500L);
                    PlayerService playerService3 = PlayerService.this;
                    playerService3.startFg(playerService3.notification("Verbindet neu…"));
                }
                PlayerService.this.updateSession();
                MainActivity.broadcastState(PlayerService.this);
            }
        }

                public /* synthetic */ void lambda$onPlaybackStateChanged$0() {
            PlayerService.this.startPlayback(PlayerService.current.url);
        }

        @Override
        public void onPlayerError(PlaybackException playbackException) {
            PlayerService.buffering = false;
            PlayerService.playing = false;
            if (Prefs.reconnect(PlayerService.this) && PlayerService.this.retries < 5 && PlayerService.this.pendingUrl != null && !PlayerService.this.pendingUrl.isEmpty()) {
                PlayerService.this.retries++;
                PlayerService.this.timer.postDelayed(new Runnable() {
                    @Override
                    public final void run() {
                        PlayerService.AnonymousClass3.this.lambda$onPlayerError$1();
                    }
                }, 2500L);
                PlayerService playerService = PlayerService.this;
                playerService.startFg(playerService.notification("Verbindet neu…"));
                MainActivity.broadcastState(PlayerService.this);
                return;
            }
            Toast.makeText(PlayerService.this, "Sender konnte nicht geladen werden", Toast.LENGTH_SHORT).show();
            PlayerService.this.updateSession();
            MainActivity.broadcastState(PlayerService.this);
        }

                public /* synthetic */ void lambda$onPlayerError$1() {
            PlayerService playerService = PlayerService.this;
            playerService.startPlayback(playerService.pendingUrl);
        }
    }

    private void applyTrackTitle(String raw) {
        String cleaned = TrackTitle.clean(raw);
        if (current != null) {
            if (current.name != null && cleaned.equalsIgnoreCase(current.name)) {
                cleaned = "";
            }
            if (current.url != null && cleaned.equals(current.url)) {
                cleaned = "";
            }
        }
        if (cleaned.equals(trackTitle)) {
            return;
        }
        trackTitle = cleaned;
        String label = current != null && current.name != null ? current.name : "Live";
        startFg(notification(playing ? label : "Pausiert"));
        updateSession();
        MainActivity.broadcastState(this);
    }

        public void applyPlayerVolume() {
        if (this.player == null) {
            return;
        }
        float volume = Prefs.volume(this) / 100.0f;
        if (this.ducked) {
            volume *= 0.18f;
        }
        try {
            this.player.setVolume(volume);
        } catch (Exception unused) {
        }
    }

    private void requestFocus() {
        if (this.audioManager == null) {
            this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        try {
            boolean z = true;
            AudioFocusRequest build = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAcceptsDelayedFocusGain(true).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public final void onAudioFocusChange(int i) {
                    PlayerService.this.onAudioFocus(i);
                }
            }, this.timer).build();
            this.focusReq = build;
            if (this.audioManager.requestAudioFocus(build) == 1) {
                z = false;
            }
            this.ducked = z;
            applyPlayerVolume();
        } catch (Exception unused) {
        }
    }

    private void abandonFocus() {
        AudioFocusRequest audioFocusRequest;
        AudioManager audioManager = this.audioManager;
        if (audioManager != null && (audioFocusRequest = this.focusReq) != null) {
            try {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } catch (Exception unused) {
            }
        }
        this.ducked = false;
    }

        public void onAudioFocus(int i) {
        ExoPlayer exoPlayer;
        ExoPlayer exoPlayer2;
        if (i == 1) {
            this.ducked = false;
            applyPlayerVolume();
            if (!this.pausedByFocus || (exoPlayer2 = this.player) == null || current == null) {
                return;
            }
            try {
                exoPlayer2.play();
                playing = true;
                this.pausedByFocus = false;
                startFg(notification(current.name));
                updateSession();
                MainActivity.broadcastState(this);
                return;
            } catch (Exception unused) {
                return;
            }
        }
        if (i == -3) {
            this.ducked = true;
            applyPlayerVolume();
            return;
        }
        if (i == -2) {
            this.ducked = true;
            applyPlayerVolume();
            return;
        }
        if (i == -1) {
            this.ducked = false;
            if (!playing || (exoPlayer = this.player) == null) {
                return;
            }
            exoPlayer.pause();
            playing = false;
            this.pausedByFocus = true;
            startFg(notification("Pausiert"));
            updateSession();
            MainActivity.broadcastState(this);
        }
    }

    private void loadArt(final Station station) {
        this.art = ArtLoader.peek(station == null ? "" : station.favicon);
        if (station == null || station.favicon == null || station.favicon.isEmpty()) {
            return;
        }
        ArtLoader.load(station.favicon, new Consumer() {
            @Override
            public final void accept(Object obj) {
                PlayerService.this.lambda$loadArt$2(station, (Bitmap) obj);
            }
        });
    }

        public /* synthetic */ void lambda$loadArt$2(Station station, Bitmap bitmap) {
        if (bitmap == null || current == null || !station.favicon.equals(current.favicon)) {
            return;
        }
        this.art = bitmap;
        startFg(notification(playing ? current.name : "Pausiert"));
        updateSession();
    }

        public void updateSession() {
        int i;
        MediaSession mediaSession = this.session;
        if (mediaSession == null) {
            return;
        }
        if (buffering) {
            i = PlaybackState.STATE_BUFFERING;
        } else {
            i = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        }
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP).setState(i, -1L, playing ? 1.0f : 0.0f).build());
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        Station station = current;
        String track = TrackTitle.clean(trackTitle);
        String stationName = station != null && station.name != null && !station.name.isEmpty() ? station.name : "Radio Welt";
        MediaMetadata.Builder putString = builder.putString(MediaMetadata.METADATA_KEY_TITLE, !track.isEmpty() ? track : stationName).putString(MediaMetadata.METADATA_KEY_ARTIST, !track.isEmpty() ? stationName : "Radio Welt");
        Station station2 = current;
        MediaMetadata.Builder putString2 = putString.putString(MediaMetadata.METADATA_KEY_ALBUM, (station2 == null || station2.country == null) ? "Live" : current.country);
        Bitmap bitmap = this.art;
        if (bitmap != null) {
            putString2.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
        }
        this.session.setMetadata(putString2.build());
        this.session.setActive(true);
    }

    private void lockWifi() {
        WifiManager.WifiLock wifiLock = this.wifiLock;
        if (wifiLock == null || !wifiLock.isHeld()) {
            WifiManager.WifiLock createWifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(3, "welle");
            this.wifiLock = createWifiLock;
            createWifiLock.acquire();
        }
    }

    private void releasePlayerOnly() {
        ExoPlayer exoPlayer = this.player;
        if (exoPlayer != null) {
            try {
                exoPlayer.removeListener(this.exoListener);
                this.player.stop();
                this.player.release();
            } catch (Exception unused) {
            }
            this.player = null;
        }
    }

    private void release() {
        ListenHistory.get(this).stop();
        abandonFocus();
        releasePlayerOnly();
        WifiManager.WifiLock wifiLock = this.wifiLock;
        if (wifiLock != null && wifiLock.isHeld()) {
            this.wifiLock.release();
        }
        this.wifiLock = null;
        playing = false;
        buffering = false;
        trackTitle = "";
        this.ducked = false;
        MediaSession mediaSession = this.session;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, -1L, 0.0f).build());
            this.session.setActive(false);
        }
        MainActivity.broadcastState(this);
    }

        public Notification notification(String str) {
        String str2;
        NotificationChannel notificationChannel = new NotificationChannel("media2", "Wiedergabe", NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setSound(null, null);
        notificationChannel.enableVibration(false);
        notificationChannel.setShowBadge(false);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ((NotificationManager) getSystemService(NotificationManager.class)).createNotificationChannel(notificationChannel);
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, (Class<?>) PlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent service = PendingIntent.getService(this, 1, new Intent(this, (Class<?>) PlayerService.class).setAction(ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent service2 = PendingIntent.getService(this, 2, new Intent(this, (Class<?>) PlayerService.class).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Station station = current;
        String str3 = (station == null || station.name == null) ? "Radio Welt" : current.name;
        String track = TrackTitle.clean(trackTitle);
        if (buffering) {
            str2 = "Verbindet…";
        } else if (!playing) {
            str2 = "Pausiert";
        } else if (!track.isEmpty()) {
            str2 = track;
        } else {
            str2 = "Radio Welt · Live";
        }
        if (sleepUntil > System.currentTimeMillis() && playing) {
            String timerText = "Timer " + Math.max(1L, (sleepUntil - System.currentTimeMillis()) / 60000) + " Min";
            str2 = str2 + " · " + timerText;
        }
        Bitmap bitmap = this.art;
        if (bitmap == null) {
            try {
                bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            } catch (Exception unused) {
            }
        }
        Notification.Builder category = new Notification.Builder(this, "media2").setSmallIcon(R.drawable.ic_stat_radio).setContentTitle(str3).setContentText(str2).setSubText("Radio Welt").setContentIntent(activity).setOngoing(playing).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        boolean z = playing;
        Notification.Builder addAction = category.addAction(new Notification.Action.Builder(z ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, z ? "Pause" : "Play", service).build()).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", service2).build());
        if (bitmap != null) {
            addAction.setLargeIcon(bitmap);
        }
        Notification.MediaStyle showActionsInCompactView = new Notification.MediaStyle().setShowActionsInCompactView(0);
        MediaSession mediaSession = this.session;
        if (mediaSession != null) {
            showActionsInCompactView.setMediaSession(mediaSession.getSessionToken());
        }
        addAction.setStyle(showActionsInCompactView);
        return addAction.build();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(this.noisy);
        } catch (Exception unused) {
        }
        this.timer.removeCallbacksAndMessages(null);
        MediaSession mediaSession = this.session;
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                this.session.release();
            } catch (Exception unused2) {
            }
            this.session = null;
        }
        if (self == this) {
            self = null;
        }
        release();
        super.onDestroy();
    }
}
