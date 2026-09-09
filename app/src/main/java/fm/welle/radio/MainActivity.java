package fm.welle.radio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    public static final String STATE = "fm.welle.radio.STATE";
    private static final String[] TABS = {"discover", "countries", "languages", "favorites", "updates"};

    private RowAdapter adapter;
    private ProgressBar busy;
    private float density;
    private TextView empty;
    private Favorites favorites;
    private ListView list;
    private ScrollView updatesScroll;
    private LinearLayout updatesContainer;
    private ImageView nowArt;
    private TextView nowMeta;
    private TextView nowTitle;
    private ImageButton play;
    private EditText search;
    private ImageButton settingsBtn;
    private TextView[] tabViews;
    private Theme theme;

    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Object> rows = new ArrayList<>();
    private String tab = "discover";
    private String detailCode = "";
    private String detailTitle = "";

    private final BroadcastReceiver stateRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshPlayer();
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    };

    public static void broadcastState(Context context) {
        context.sendBroadcast(new Intent(STATE).setPackage(context.getPackageName()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        favorites = new Favorites(this);
        density = getResources().getDisplayMetrics().density;
        ArtLoader.init(this);

        list = findViewById(R.id.list);
        updatesScroll = findViewById(R.id.updates_scroll);
        updatesContainer = findViewById(R.id.updates_container);
        empty = findViewById(R.id.empty);
        busy = findViewById(R.id.busy);
        nowTitle = findViewById(R.id.now_title);
        nowMeta = findViewById(R.id.now_meta);
        nowArt = findViewById(R.id.now_art);
        play = findViewById(R.id.play);
        settingsBtn = findViewById(R.id.settings);
        search = findViewById(R.id.search);
        tabViews = new TextView[]{
                findViewById(R.id.tab_discover),
                findViewById(R.id.tab_countries),
                findViewById(R.id.tab_languages),
                findViewById(R.id.tab_favorites),
                findViewById(R.id.tab_updates)
        };

        adapter = new RowAdapter();
        list.setAdapter(adapter);

        for (int i = 0; i < tabViews.length; i++) {
            final String id = TABS[i];
            tabViews[i].setOnClickListener(v -> selectTab(id));
        }
        play.setOnClickListener(v -> {
            if (PlayerService.current == null) {
                Toast.makeText(this, "Wähle zuerst einen Sender", Toast.LENGTH_SHORT).show();
            } else {
                PlayerService.toggle(this);
            }
        });
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        search.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId != 3 && (event == null || event.getKeyCode() != KeyEvent.KEYCODE_ENTER)) {
                return false;
            }
            String q = search.getText().toString().trim();
            if (!q.isEmpty()) runSearch(q);
            return true;
        });

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        selectTab("discover");
        applyTheme();
        refreshPlayer();
        maybeAutoplay();
    }

    private void maybeAutoplay() {
        if (!Prefs.autoplay(this) || PlayerService.playing || PlayerService.buffering) return;
        Station last = Prefs.last(this);
        if (last != null) playStation(last);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateRx, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateRx, filter);
        }
        applyTheme();
        refreshPlayer();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(stateRx);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (!detailCode.isEmpty()) {
            String keep = tab;
            detailCode = "";
            detailTitle = "";
            selectTab(keep);
            return;
        }
        super.onBackPressed();
    }

    private void selectTab(String id) {
        tab = id;
        detailCode = "";
        detailTitle = "";
        styleTabs();
        boolean updates = "updates".equals(id);
        list.setVisibility(updates ? View.GONE : View.VISIBLE);
        updatesScroll.setVisibility(updates ? View.VISIBLE : View.GONE);
        empty.setVisibility(View.GONE);
        switch (id) {
            case "favorites":
                showFavorites();
                break;
            case "discover":
                loadDiscover();
                break;
            case "countries":
                loadCountries();
                break;
            case "languages":
                loadLanguages();
                break;
            case "updates":
                loadUpdates();
                break;
        }
    }

    private void styleTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = TABS[i].equals(tab);
            if (theme != null) {
                tabViews[i].setBackground(theme.roundColor(on ? theme.accent : theme.chip, density * 20f));
                tabViews[i].setTextColor(on ? theme.onAccent : theme.fg);
            } else {
                tabViews[i].setBackgroundResource(on ? R.drawable.bg_tab_on : R.drawable.bg_tab);
                tabViews[i].setTextColor(getColor(on ? R.color.ink : R.color.paper));
            }
        }
    }

    private void loadDiscover() {
        setBusy(true);
        io.execute(() -> {
            try {
                List<Station> de = RadioApi.search(null, "DE", null, null, 20);
                List<Station> world = RadioApi.search(null, null, null, null, 30);
                ui.post(() -> {
                    rows.clear();
                    rows.add("TECHNO4EVER");
                    rows.addAll(Featured.techno4ever());
                    rows.add("Beliebt in Deutschland");
                    rows.addAll(de);
                    rows.add("Weltweit");
                    rows.addAll(world);
                    done("");
                });
            } catch (Exception e) {
                ui.post(() -> fail());
            }
        });
    }

    private void loadCountries() {
        setBusy(true);
        io.execute(() -> {
            try {
                List<NamedCount> countries = RadioApi.countries();
                ui.post(() -> {
                    rows.clear();
                    rows.addAll(countries);
                    done("Keine Länder gefunden");
                });
            } catch (Exception e) {
                ui.post(this::fail);
            }
        });
    }

    private void loadLanguages() {
        setBusy(true);
        io.execute(() -> {
            try {
                List<NamedCount> languages = RadioApi.languages();
                ui.post(() -> {
                    rows.clear();
                    rows.addAll(languages);
                    done("Keine Sprachen gefunden");
                });
            } catch (Exception e) {
                ui.post(this::fail);
            }
        });
    }

    private void showFavorites() {
        rows.clear();
        rows.addAll(favorites.all());
        done("Noch keine Favoriten — tippe den Stern bei einem Sender.");
    }

    private void loadUpdates() {
        setBusy(true);
        updatesContainer.removeAllViews();
        io.execute(() -> {
            UpdatesRepository.Feed feed = UpdatesRepository.load(this);
            ui.post(() -> {
                setBusy(false);
                renderUpdates(feed);
            });
        });
    }

    private void renderUpdates(UpdatesRepository.Feed feed) {
        updatesContainer.removeAllViews();
        Theme t = theme == null ? Theme.current(this) : theme;

        String versionName = "2.9";
        int versionCode = 14;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        TextView heading = new TextView(this);
        heading.setText("Updates");
        heading.setTextColor(t.fg);
        heading.setTextSize(2, 28f);
        heading.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        heading.setPadding(0, (int) (density * 8f), 0, (int) (density * 4f));
        updatesContainer.addView(heading);

        TextView current = new TextView(this);
        current.setText(getString(R.string.current_version) + ": " + versionName + " (" + versionCode + ")");
        current.setTextColor(t.muted);
        current.setTextSize(2, 14f);
        current.setPadding(0, 0, 0, (int) (density * 12f));
        updatesContainer.addView(current);

        if (feed.latestVersionName != null && !feed.latestVersionName.isEmpty()) {
            TextView latest = new TextView(this);
            latest.setText("Neueste Version: " + feed.latestVersionName + " (" + feed.latestVersionCode + ")");
            latest.setTextColor(t.fg);
            latest.setTextSize(2, 14f);
            latest.setPadding(0, 0, 0, (int) (density * 12f));
            updatesContainer.addView(latest);
        }

        final String downloadUrl = (feed.downloadUrl == null || feed.downloadUrl.isEmpty())
                ? "https://github.com/daniel96865-a11y/radio-welt/releases/latest/download/RadioWelt-latest.apk"
                : feed.downloadUrl;

        TextView downloadBtn = new TextView(this);
        downloadBtn.setText(R.string.download_apk);
        downloadBtn.setTextColor(t.onAccent);
        downloadBtn.setTextSize(2, 15f);
        downloadBtn.setGravity(android.view.Gravity.CENTER);
        downloadBtn.setPadding((int) (density * 16f), (int) (density * 12f), (int) (density * 16f), (int) (density * 12f));
        downloadBtn.setBackground(t.roundColor(t.accent, density * 18f));
        downloadBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
            } catch (Exception e) {
                Toast.makeText(this, "Download-Link nicht öffnenbar", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.bottomMargin = (int) (density * 20f);
        downloadBtn.setLayoutParams(btnLp);
        updatesContainer.addView(downloadBtn);

        // Always show FULL history, newest first — never hide older updates
        for (UpdatesRepository.UpdateItem item : feed.updates) {
            updatesContainer.addView(buildUpdateCard(item, t));
        }

        if (feed.updates.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Keine Update-Einträge verfügbar.");
            none.setTextColor(t.muted);
            none.setTextSize(2, 14f);
            updatesContainer.addView(none);
        }
    }

    private View buildUpdateCard(UpdatesRepository.UpdateItem item, Theme t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (density * 14f), (int) (density * 14f), (int) (density * 14f), (int) (density * 14f));
        card.setBackground(t.roundColor(t.surface, density * 16f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (density * 12f);
        card.setLayoutParams(lp);

        TextView meta = new TextView(this);
        meta.setText("v" + item.versionName + " · " + item.date + " · Build " + item.versionCode);
        meta.setTextColor(t.accent);
        meta.setTextSize(2, 12f);
        meta.setLetterSpacing(0.04f);
        card.addView(meta);

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(t.fg);
        title.setTextSize(2, 18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, (int) (density * 6f), 0, (int) (density * 8f));
        card.addView(title);

        for (String note : item.notes) {
            TextView n = new TextView(this);
            n.setText("•  " + note);
            n.setTextColor(t.muted);
            n.setTextSize(2, 14f);
            n.setPadding(0, (int) (density * 2f), 0, (int) (density * 2f));
            card.addView(n);
        }
        return card;
    }

    private void runSearch(String query) {
        tab = "discover";
        detailCode = "";
        list.setVisibility(View.VISIBLE);
        updatesScroll.setVisibility(View.GONE);
        styleTabs();
        setBusy(true);
        io.execute(() -> {
            try {
                List<Station> result = RadioApi.search(query, null, null, null, 50);
                ui.post(() -> {
                    rows.clear();
                    rows.add("Suche: " + query);
                    if (Featured.matchesSearch(query)) {
                        rows.addAll(Featured.techno4ever());
                    }
                    rows.addAll(result);
                    done("Nichts gefunden für „" + query + "“");
                });
            } catch (Exception e) {
                ui.post(this::fail);
            }
        });
    }

    private void openCountry(NamedCount namedCount) {
        detailCode = namedCount.code;
        detailTitle = namedCount.name;
        setBusy(true);
        io.execute(() -> {
            try {
                List<Station> stations = RadioApi.search(null, namedCount.code, null, null, 60);
                ui.post(() -> {
                    rows.clear();
                    rows.add(namedCount.name);
                    rows.addAll(stations);
                    done("Keine Sender");
                });
            } catch (Exception e) {
                ui.post(this::fail);
            }
        });
    }

    private void openLanguage(NamedCount namedCount) {
        detailCode = namedCount.name;
        detailTitle = namedCount.name;
        setBusy(true);
        io.execute(() -> {
            try {
                List<Station> stations = RadioApi.search(null, null, namedCount.name, null, 60);
                ui.post(() -> {
                    rows.clear();
                    rows.add(namedCount.name);
                    rows.addAll(stations);
                    done("Keine Sender");
                });
            } catch (Exception e) {
                ui.post(this::fail);
            }
        });
    }

    private void playStation(Station station) {
        Prefs.saveLast(this, station);
        Toast.makeText(this, "Verbindet " + station.name, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            String resolved = RadioApi.resolve(station.id, station.url);
            ui.post(() -> PlayerService.play(this, station, resolved));
        });
        refreshPlayer();
        adapter.notifyDataSetChanged();
    }

    private void setBusy(boolean show) {
        busy.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) empty.setVisibility(View.GONE);
    }

    private void done(String emptyText) {
        setBusy(false);
        adapter.notifyDataSetChanged();
        boolean emptyOnly = rows.isEmpty() || (rows.size() == 1 && rows.get(0) instanceof String);
        empty.setText(emptyText);
        empty.setVisibility(emptyOnly ? View.VISIBLE : View.GONE);
    }

    private void fail() {
        setBusy(false);
        rows.clear();
        adapter.notifyDataSetChanged();
        empty.setText("Verzeichnis nicht erreichbar. Prüfe deine Verbindung.");
        empty.setVisibility(View.VISIBLE);
    }

    private void applyTheme() {
        theme = Theme.current(this);
        findViewById(R.id.root).setBackgroundColor(theme.bg);
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(
                theme.light ? flags | 8208 : flags & ~8208);
        ((TextView) findViewById(R.id.brand)).setTextColor(theme.fg);
        search.setBackground(theme.roundColor(theme.surface, density * 22f));
        search.setTextColor(theme.fg);
        search.setHintTextColor(theme.muted);
        settingsBtn.setColorFilter(theme.fg);
        empty.setTextColor(theme.muted);
        findViewById(R.id.player).setBackgroundColor(theme.surface);
        nowTitle.setTextColor(theme.fg);
        nowMeta.setTextColor(theme.muted);
        play.setBackground(theme.oval(theme.accent));
        play.setColorFilter(theme.onAccent);
        list.setDivider(new ColorDrawable(theme.line));
        list.setDividerHeight(Math.max(1, (int) density));
        styleTabs();
        roundClip(nowArt);
        if ("updates".equals(tab) && updatesContainer.getChildCount() > 0) {
            // re-theme by reloading cards with current theme colors
            loadUpdates();
        }
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private void styleCover(ImageView imageView, boolean flag) {
        if (imageView == null) return;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) imageView.getLayoutParams();
        if (flag) {
            lp.width = dp(58);
            lp.height = dp(40);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            if (theme != null) imageView.setBackground(theme.roundColor(theme.line, dp(5)));
            roundClip(imageView, dp(5));
        } else {
            lp.width = dp(52);
            lp.height = dp(52);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackground(null);
            roundClip(imageView, density * 12f);
        }
        imageView.setLayoutParams(lp);
    }

    private void roundClip(ImageView imageView) {
        roundClip(imageView, density * 12f);
    }

    private void roundClip(ImageView imageView, float radius) {
        if (imageView == null) return;
        imageView.setClipToOutline(true);
        imageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) {
                    outline.setRoundRect(0, 0, 1, 1, radius);
                } else {
                    outline.setRoundRect(0, 0, w, h, radius);
                }
            }
        });
        imageView.invalidateOutline();
    }

    private void refreshPlayer() {
        Station station = PlayerService.current;
        if (station == null) {
            nowTitle.setText("Kein Sender");
            nowMeta.setText("Wähle einen Sender zum Hören");
            play.setImageResource(android.R.drawable.ic_media_play);
            ArtLoader.bind(nowArt, "", "R", theme == null ? Theme.current(this) : theme);
            roundClip(nowArt);
            return;
        }
        nowTitle.setText(station.name);
        nowMeta.setText(PlayerService.buffering ? "Verbindet…"
                : (station.country == null || station.country.isEmpty() ? "Live" : station.country));
        play.setImageResource(PlayerService.playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
        ArtLoader.bind(nowArt, station.favicon, station.name, theme == null ? Theme.current(this) : theme);
        roundClip(nowArt);
    }

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 1 : 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = rows.get(position);
            if (item instanceof String) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                tv.setText((String) item);
                tv.setTextColor(theme == null ? getColor(R.color.paper) : theme.fg);
                tv.setTextSize(22f);
                tv.setPadding(16, 28, 16, 12);
                tv.setTypeface(Typeface.SERIF, Typeface.ITALIC);
                return tv;
            }

            View view = (convertView == null || convertView instanceof TextView)
                    ? LayoutInflater.from(MainActivity.this).inflate(R.layout.item_row, parent, false)
                    : convertView;
            TextView title = view.findViewById(R.id.title);
            TextView meta = view.findViewById(R.id.meta);
            ImageView cover = view.findViewById(R.id.cover);
            ImageButton heart = view.findViewById(R.id.heart);
            Theme current = theme == null ? Theme.current(MainActivity.this) : theme;
            title.setTextColor(current.fg);
            meta.setTextColor(current.muted);

            if (item instanceof Station) {
                Station station = (Station) item;
                styleCover(cover, false);
                title.setText(station.name);
                meta.setText(station.meta());
                ArtLoader.bind(cover, station.favicon, station.name, current);
                heart.setVisibility(View.VISIBLE);
                boolean fav = favorites.has(station.id);
                heart.setImageResource(fav ? R.drawable.ic_star : R.drawable.ic_star_outline);
                heart.setColorFilter(fav ? current.accent : current.muted);
                heart.setOnClickListener(v -> {
                    favorites.toggle(station);
                    if ("favorites".equals(tab) && detailCode.isEmpty()) showFavorites();
                    else notifyDataSetChanged();
                });
                view.setOnClickListener(v -> playStation(station));
            } else if (item instanceof NamedCount) {
                NamedCount nc = (NamedCount) item;
                boolean flag = "countries".equals(tab) && nc.code.length() == 2;
                styleCover(cover, flag);
                title.setText(nc.name);
                meta.setText(nc.count + " Sender");
                String art = flag ? "https://flagcdn.com/80x60/" + nc.code.toLowerCase() + ".png" : "";
                ArtLoader.bind(cover, art, nc.name, current);
                heart.setVisibility(View.GONE);
                view.setOnClickListener(v -> {
                    if ("languages".equals(tab)) openLanguage(nc);
                    else openCountry(nc);
                });
            }
            return view;
        }
    }
}
