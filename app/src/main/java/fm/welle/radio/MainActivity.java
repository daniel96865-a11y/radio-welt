package fm.welle.radio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    public static final String STATE = "fm.welle.radio.STATE";
    private static final String[] TABS = {"discover", "countries", "languages", "favorites"};
    private RowAdapter adapter;
    private ProgressBar busy;
    private float density;
    private TextView empty;
    private Favorites favorites;
    private ListView list;
    private ImageView nowArt;
    private TextView nowMeta;
    private TextView nowTitle;
    private ImageButton play;
    private EditText search;
    private ImageButton settingsBtn;
    private TextView[] tabViews;
    private Theme theme;
    private LinearLayout updateBar;
    private TextView updateGo;
    private TextView updateText;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Object> rows = new ArrayList();
    private String tab = "discover";
    private String detailCode = "";
    private String detailTitle = "";
    private String updateUrl = "";
    private final BroadcastReceiver stateRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MainActivity.this.refreshPlayer();
            MainActivity.this.adapter.notifyDataSetChanged();
        }
    };

    public static void broadcastState(Context context) {
        context.sendBroadcast(new Intent(STATE).setPackage(context.getPackageName()));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        this.favorites = new Favorites(this);
        this.density = getResources().getDisplayMetrics().density;
        ArtLoader.init(this);
        this.list = (ListView) findViewById(R.id.list);
        this.empty = (TextView) findViewById(R.id.empty);
        this.busy = (ProgressBar) findViewById(R.id.busy);
        this.nowTitle = (TextView) findViewById(R.id.now_title);
        this.nowMeta = (TextView) findViewById(R.id.now_meta);
        this.nowArt = (ImageView) findViewById(R.id.now_art);
        this.play = (ImageButton) findViewById(R.id.play);
        this.settingsBtn = (ImageButton) findViewById(R.id.settings);
        this.search = (EditText) findViewById(R.id.search);
        this.updateBar = (LinearLayout) findViewById(R.id.update_bar);
        this.updateText = (TextView) findViewById(R.id.update_text);
        TextView textView = (TextView) findViewById(R.id.update_go);
        this.updateGo = textView;
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$0(view);
            }
        });
        this.updateBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$1(view);
            }
        });
        this.tabViews = new TextView[]{(TextView) findViewById(R.id.tab_discover), (TextView) findViewById(R.id.tab_countries), (TextView) findViewById(R.id.tab_languages), (TextView) findViewById(R.id.tab_favorites)};
        RowAdapter rowAdapter = new RowAdapter();
        this.adapter = rowAdapter;
        this.list.setAdapter((ListAdapter) rowAdapter);
        this.list.setItemsCanFocus(true);
        this.list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        this.list.setOnItemClickListener((parent, view, position, id) -> activateRow(position));
        int i = 0;
        while (true) {
            TextView[] textViewArr = this.tabViews;
            if (i >= textViewArr.length) {
                break;
            }
            final String str = TABS[i];
            textViewArr[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    MainActivity.this.lambda$onCreate$2(str, view);
                }
            });
            i++;
        }
        this.play.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$3(view);
            }
        });
        this.settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$4(view);
            }
        });
        this.search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public final boolean onEditorAction(TextView textView2, int i2, KeyEvent keyEvent) {
                boolean lambda$onCreate$5;
                lambda$onCreate$5 = MainActivity.this.lambda$onCreate$5(textView2, i2, keyEvent);
                return lambda$onCreate$5;
            }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        selectTab("discover");
        applyTheme();
        refreshPlayer();
        maybeAutoplay();
        checkForUpdate(false);
        if (Prefs.isTelevision(this)) {
            this.list.post(() -> {
                this.list.requestFocus();
                focusFirstPlayableRow();
            });
        }
    }

        public /* synthetic */ void lambda$onCreate$0(View view) {
        if (this.updateUrl.isEmpty()) {
            return;
        }
        UpdateChecker.open(this, this.updateUrl);
    }

        public /* synthetic */ void lambda$onCreate$1(View view) {
        this.updateGo.performClick();
    }

        public /* synthetic */ void lambda$onCreate$2(String str, View view) {
        selectTab(str);
    }

        public /* synthetic */ void lambda$onCreate$3(View view) {
        if (PlayerService.current == null) {
            Toast.makeText(this, "Wähle zuerst einen Sender", 0).show();
        } else {
            PlayerService.toggle(this);
        }
    }

        public /* synthetic */ void lambda$onCreate$4(View view) {
        startActivity(new Intent(this, (Class<?>) SettingsActivity.class));
    }

        public /* synthetic */ boolean lambda$onCreate$5(TextView textView, int i, KeyEvent keyEvent) {
        if (i != 3 && (keyEvent == null || keyEvent.getKeyCode() != 66)) {
            return false;
        }
        String trim = this.search.getText().toString().trim();
        if (trim.isEmpty()) {
            return true;
        }
        runSearch(trim);
        return true;
    }

    private void maybeAutoplay() {
        Station last;
        if (!Prefs.autoplay(this) || PlayerService.playing || PlayerService.buffering || (last = Prefs.last(this)) == null) {
            return;
        }
        playStation(last);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.stateRx, intentFilter, 4);
        } else {
            registerReceiver(this.stateRx, intentFilter);
        }
        applyTheme();
        refreshPlayer();
        RowAdapter rowAdapter = this.adapter;
        if (rowAdapter != null) {
            rowAdapter.notifyDataSetChanged();
        }
        checkForUpdate(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(this.stateRx);
        } catch (Exception unused) {
        }
    }

    @Override
    public void onBackPressed() {
        if (!this.detailCode.isEmpty()) {
            String str = this.tab;
            this.detailCode = "";
            this.detailTitle = "";
            selectTab(str);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (PlayerService.playing) {
                    PlayerService.toggle(this);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (PlayerService.current == null) {
                    playFocusedOrFirstStation();
                } else if (!PlayerService.playing) {
                    PlayerService.toggle(this);
                }
                return true;
            }
            // PLAY_PAUSE / BUTTON_A: toggle if station loaded, else play focused/first
            if (PlayerService.current != null) {
                PlayerService.toggle(this);
            } else {
                playFocusedOrFirstStation();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void playFocusedOrFirstStation() {
        int pos = this.list.getSelectedItemPosition();
        if (pos < 0) {
            pos = this.list.getCheckedItemPosition();
        }
        if (pos >= 0 && pos < this.rows.size()) {
            activateRow(pos);
            return;
        }
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i) instanceof Station) {
                playStation((Station) this.rows.get(i));
                return;
            }
        }
        Toast.makeText(this, "Wähle zuerst einen Sender", Toast.LENGTH_SHORT).show();
    }

    private void focusFirstPlayableRow() {
        for (int i = 0; i < this.rows.size(); i++) {
            Object row = this.rows.get(i);
            if (row instanceof Station || row instanceof NamedCount) {
                this.list.setSelection(i);
                final int target = i;
                this.list.post(() -> {
                    View child = this.list.getChildAt(target - this.list.getFirstVisiblePosition());
                    if (child != null) {
                        child.requestFocus();
                    }
                });
                return;
            }
        }
    }

    private void activateRow(int position) {
        if (position < 0 || position >= this.rows.size()) {
            return;
        }
        Object obj = this.rows.get(position);
        if (obj instanceof Station) {
            playStation((Station) obj);
        } else if (obj instanceof NamedCount) {
            NamedCount namedCount = (NamedCount) obj;
            if ("languages".equals(this.tab)) {
                openLanguage(namedCount);
            } else {
                openCountry(namedCount);
            }
        }
    }

    private void selectTab(String str) {
        this.tab = str;
        this.detailCode = "";
        this.detailTitle = "";
        for (int i = 0; i < this.tabViews.length; i++) {
            boolean equals = TABS[i].equals(str);
            Theme theme = this.theme;
            if (theme != null) {
                this.tabViews[i].setBackground(theme.roundColor(equals ? theme.accent : theme.chip, this.density * 20.0f));
                this.tabViews[i].setTextColor(equals ? this.theme.onAccent : this.theme.fg);
            } else {
                this.tabViews[i].setBackgroundResource(equals ? R.drawable.bg_tab_on : R.drawable.bg_tab);
                this.tabViews[i].setTextColor(getColor(equals ? R.color.ink : R.color.paper));
            }
        }
        if ("favorites".equals(str)) {
            showFavorites();
        } else if ("discover".equals(str)) {
            loadDiscover();
        } else if ("countries".equals(str)) {
            loadCountries();
        } else if ("languages".equals(str)) {
            loadLanguages();
        }
    }

    private void loadDiscover() {
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$loadDiscover$8();
            }
        });
    }

        public /* synthetic */ void lambda$loadDiscover$8() {
        try {
            final List<Object> buildPersonal = buildPersonal(ListenHistory.get(this));
            final List<Station> search = RadioApi.search(null, "DE", null, null, 20);
            final List<Station> search2 = RadioApi.search(null, null, null, null, 30);
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$loadDiscover$6(buildPersonal, search, search2);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$loadDiscover$6(List list, List list2, List list3) {
        this.rows.clear();
        this.rows.addAll(list);
        this.rows.add("TECHNO4EVER");
        this.rows.addAll(Featured.techno4ever());
        this.rows.add("Beliebt in Deutschland");
        this.rows.addAll(list2);
        this.rows.add("Weltweit");
        this.rows.addAll(list3);
        done("");
    }

    private List<Object> buildPersonal(ListenHistory listenHistory) {
        ArrayList arrayList = new ArrayList();
        List<Station> pVar = listenHistory.top(6);
        if (pVar.isEmpty()) {
            return arrayList;
        }
        Set<String> knownIds = listenHistory.knownIds();
        arrayList.add("Oft gehört");
        arrayList.addAll(pVar);
        String str = listenHistory.topCountry();
        String str2 = listenHistory.topCountryName();
        String str3 = listenHistory.topTag();
        String str4 = listenHistory.topLanguage();
        if (str != null) {
            try {
                List<Station> takeNew = takeNew(RadioApi.search(null, str, null, null, 18), knownIds, 8);
                if (!takeNew.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Weil du ");
                    if (str2 != null) {
                        str = str2;
                    }
                    arrayList.add(sb.append(str).append(" hörst").toString());
                    arrayList.addAll(takeNew);
                }
            } catch (Exception unused) {
            }
        }
        if (str3 != null) {
            try {
                List<Station> takeNew2 = takeNew(RadioApi.search(null, null, null, str3, 16), knownIds, 6);
                if (!takeNew2.isEmpty()) {
                    arrayList.add("Weil du " + (str3.substring(0, 1).toUpperCase() + str3.substring(1)) + " hörst");
                    arrayList.addAll(takeNew2);
                }
            } catch (Exception unused) {
            }
        } else if (str4 != null) {
            try {
                List<Station> takeNew3 = takeNew(RadioApi.search(null, null, str4, null, 16), knownIds, 6);
                if (!takeNew3.isEmpty()) {
                    arrayList.add("Auf " + (str4.substring(0, 1).toUpperCase() + str4.substring(1)));
                    arrayList.addAll(takeNew3);
                }
            } catch (Exception unused) {
            }
        }
        return arrayList;
    }

    private List<Station> takeNew(List<Station> list, Set<String> set, int i) {
        ArrayList arrayList = new ArrayList();
        if (list == null) {
            return arrayList;
        }
        for (Station station : list) {
            if (station != null && station.id != null && set.add(station.id)) {
                arrayList.add(station);
                if (arrayList.size() >= i) {
                    break;
                }
            }
        }
        return arrayList;
    }

    private void loadCountries() {
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$loadCountries$11();
            }
        });
    }

        public /* synthetic */ void lambda$loadCountries$11() {
        try {
            final List<NamedCount> countries = RadioApi.countries();
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$loadCountries$9(countries);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$loadCountries$9(List list) {
        this.rows.clear();
        this.rows.addAll(list);
        done("Keine Länder gefunden");
    }

    private void loadLanguages() {
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$loadLanguages$14();
            }
        });
    }

        public /* synthetic */ void lambda$loadLanguages$14() {
        try {
            final List<NamedCount> languages = RadioApi.languages();
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$loadLanguages$12(languages);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$loadLanguages$12(List list) {
        this.rows.clear();
        this.rows.addAll(list);
        done("Keine Sprachen gefunden");
    }

        public void showFavorites() {
        this.rows.clear();
        this.rows.addAll(this.favorites.all());
        done("Noch keine Favoriten — tippe den Stern bei einem Sender.");
    }

    private void runSearch(final String str) {
        this.tab = "discover";
        this.detailCode = "";
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$runSearch$17(str);
            }
        });
    }

        public /* synthetic */ void lambda$runSearch$17(final String str) {
        try {
            final List<Station> search = RadioApi.search(str, null, null, null, 50);
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$runSearch$15(str, search);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$runSearch$15(String str, List list) {
        this.rows.clear();
        this.rows.add("Suche: " + str);
        if (Featured.matchesSearch(str)) {
            this.rows.addAll(Featured.techno4ever());
        }
        this.rows.addAll(list);
        done("Nichts gefunden für „" + str + "“");
    }

        public void openCountry(final NamedCount namedCount) {
        this.detailCode = namedCount.code;
        this.detailTitle = namedCount.name;
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$openCountry$20(namedCount);
            }
        });
    }

        public /* synthetic */ void lambda$openCountry$20(final NamedCount namedCount) {
        try {
            final List<Station> search = RadioApi.search(null, namedCount.code, null, null, 60);
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$openCountry$18(namedCount, search);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$openCountry$18(NamedCount namedCount, List list) {
        this.rows.clear();
        this.rows.add(namedCount.name);
        this.rows.addAll(list);
        done("Keine Sender");
    }

        public void openLanguage(final NamedCount namedCount) {
        this.detailCode = namedCount.name;
        this.detailTitle = namedCount.name;
        setBusy(true);
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$openLanguage$23(namedCount);
            }
        });
    }

        public /* synthetic */ void lambda$openLanguage$23(final NamedCount namedCount) {
        try {
            final List<Station> search = RadioApi.search(null, null, namedCount.name, null, 60);
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$openLanguage$21(namedCount, search);
                }
            });
        } catch (Exception e) {
            this.ui.post(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fail(e);
                }
            });
        }
    }

        public /* synthetic */ void lambda$openLanguage$21(NamedCount namedCount, List list) {
        this.rows.clear();
        this.rows.add(namedCount.name);
        this.rows.addAll(list);
        done("Keine Sender");
    }

        public void playStation(final Station station) {
        Prefs.saveLast(this, station);
        Toast.makeText(this, "Verbindet " + station.name, 0).show();
        this.io.execute(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$playStation$25(station);
            }
        });
        refreshPlayer();
        this.adapter.notifyDataSetChanged();
    }

        public /* synthetic */ void lambda$playStation$25(final Station station) {
        final String resolve = RadioApi.resolve(station.id, station.url);
        this.ui.post(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$playStation$24(station, resolve);
            }
        });
    }

        public /* synthetic */ void lambda$playStation$24(Station station, String str) {
        PlayerService.play(this, station, str);
    }

    private void setBusy(boolean z) {
        this.busy.setVisibility(z ? 0 : 8);
        if (z) {
            this.empty.setVisibility(8);
        }
    }

    private void done(String str) {
        setBusy(false);
        this.adapter.notifyDataSetChanged();
        boolean z = true;
        if (!this.rows.isEmpty() && (this.rows.size() != 1 || !(this.rows.get(0) instanceof String))) {
            z = false;
        }
        this.empty.setText(str);
        this.empty.setVisibility(z ? 0 : 8);
        if (Prefs.isTelevision(this) && !z) {
            this.list.post(this::focusFirstPlayableRow);
        }
    }
    public void fail(Exception exc) {
        setBusy(false);
        this.rows.clear();
        this.adapter.notifyDataSetChanged();
        this.empty.setText("Verzeichnis nicht erreichbar. Prüfe deine Verbindung.");
        this.empty.setVisibility(0);
    }

    private void applyTheme() {
        this.theme = Theme.current(this);
        findViewById(R.id.root).setBackgroundColor(this.theme.bg);
        getWindow().setStatusBarColor(this.theme.bg);
        getWindow().setNavigationBarColor(this.theme.bg);
        int systemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(this.theme.light ? systemUiVisibility | 8208 : systemUiVisibility & (-8209));
        ((TextView) findViewById(R.id.brand)).setTextColor(this.theme.fg);
        EditText editText = this.search;
        Theme theme = this.theme;
        editText.setBackground(theme.roundColor(theme.surface, this.density * 22.0f));
        this.search.setTextColor(this.theme.fg);
        this.search.setHintTextColor(this.theme.muted);
        this.settingsBtn.setColorFilter(this.theme.fg);
        this.empty.setTextColor(this.theme.muted);
        findViewById(R.id.player).setBackgroundColor(this.theme.surface);
        this.nowTitle.setTextColor(this.theme.fg);
        this.nowMeta.setTextColor(this.theme.muted);
        this.play.setBackground(focusableOval(this.theme.accent, this.theme.onAccent));
        this.play.setColorFilter(this.theme.onAccent);
        this.list.setDivider(new ColorDrawable(this.theme.line));
        this.list.setDividerHeight(Math.max(1, (int) this.density));
        for (int i = 0; i < this.tabViews.length; i++) {
            boolean equals = TABS[i].equals(this.tab);
            TextView textView = this.tabViews[i];
            Theme theme3 = this.theme;
            textView.setBackground(theme3.roundColor(equals ? theme3.accent : theme3.chip, this.density * 20.0f));
            this.tabViews[i].setTextColor(equals ? this.theme.onAccent : this.theme.fg);
        }
        LinearLayout linearLayout = this.updateBar;
        if (linearLayout != null) {
            linearLayout.setBackgroundColor(this.theme.accent);
            this.updateText.setTextColor(this.theme.onAccent);
            this.updateGo.setTextColor(this.theme.onAccent);
        }
        roundClip(this.nowArt);
    }

    void checkForUpdate(final boolean z) {
        long j = getSharedPreferences("welle", 0).getLong("update_checked", 0L);
        if (z || System.currentTimeMillis() - j >= 1800000) {
            this.io.execute(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$checkForUpdate$27(z);
                }
            });
        }
    }

        public /* synthetic */ void lambda$checkForUpdate$27(final boolean z) {
        final UpdateChecker.Info fetch = UpdateChecker.fetch();
        getSharedPreferences("welle", 0).edit().putLong("update_checked", System.currentTimeMillis()).apply();
        final int installedCode = UpdateChecker.installedCode(this);
        this.ui.post(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$checkForUpdate$26(fetch, installedCode, z);
            }
        });
    }

        public /* synthetic */ void lambda$checkForUpdate$26(UpdateChecker.Info info, int i, boolean z) {
        if (info == null || info.versionCode <= i || info.url.isEmpty()) {
            LinearLayout linearLayout = this.updateBar;
            if (linearLayout != null) {
                linearLayout.setVisibility(8);
            }
            if (z) {
                Toast.makeText(this, "Du hast die neueste Version.", 0).show();
                return;
            }
            return;
        }
        this.updateUrl = info.url;
        this.updateText.setText("Neue Version" + (info.versionName.isEmpty() ? "" : " " + info.versionName) + " — tippen zum Laden");
        this.updateBar.setVisibility(0);
    }

    private int dp(int i) {
        return Math.round(i * this.density);
    }

        public void styleCover(ImageView imageView, boolean z) {
        if (imageView == null) {
            return;
        }
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) imageView.getLayoutParams();
        if (z) {
            layoutParams.width = dp(58);
            layoutParams.height = dp(40);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            Theme theme = this.theme;
            if (theme != null) {
                imageView.setBackground(theme.roundColor(theme.line, dp(5)));
            }
            roundClip(imageView, dp(5));
        } else {
            layoutParams.width = dp(52);
            layoutParams.height = dp(52);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackground(null);
            roundClip(imageView, this.density * 12.0f);
        }
        imageView.setLayoutParams(layoutParams);
    }


    private StateListDrawable focusableOval(int fill, int focusedStroke) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(fill);
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.OVAL);
        focused.setColor(fill);
        focused.setStroke(dp(3), Color.WHITE);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void roundClip(ImageView imageView) {
        roundClip(imageView, this.density * 12.0f);
    }

    private void roundClip(ImageView imageView, final float f) {
        if (imageView == null) {
            return;
        }
        imageView.setClipToOutline(true);
        imageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int width = view.getWidth();
                int height = view.getHeight();
                if (width <= 0 || height <= 0) {
                    outline.setRoundRect(0, 0, 1, 1, f);
                } else {
                    outline.setRoundRect(0, 0, width, height, f);
                }
            }
        });
        imageView.invalidateOutline();
    }

        public void refreshPlayer() {
        Station station = PlayerService.current;
        int i = android.R.drawable.ic_media_play;
        if (station == null) {
            this.nowTitle.setText("Kein Sender");
            this.nowMeta.setText("Wähle einen Sender zum Hören");
            this.play.setImageResource(android.R.drawable.ic_media_play);
            ImageView imageView = this.nowArt;
            Theme theme = this.theme;
            if (theme == null) {
                theme = Theme.current(this);
            }
            ArtLoader.bind(imageView, "", "R", theme);
            roundClip(this.nowArt);
            return;
        }
        this.nowTitle.setText(station.name);
        if (PlayerService.buffering) {
            this.nowMeta.setText("Verbindet…");
        } else {
            this.nowMeta.setText((station.country == null || station.country.isEmpty()) ? "Live" : station.country);
        }
        ImageButton imageButton = this.play;
        if (PlayerService.playing) {
            i = android.R.drawable.ic_media_pause;
        }
        imageButton.setImageResource(i);
        ImageView imageView2 = this.nowArt;
        String str = station.favicon;
        String str2 = station.name;
        Theme theme2 = this.theme;
        if (theme2 == null) {
            theme2 = Theme.current(this);
        }
        ArtLoader.bind(imageView2, str, str2, theme2);
        roundClip(this.nowArt);
    }

        class RowAdapter extends BaseAdapter {
        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        private RowAdapter() {
        }

        @Override
        public int getCount() {
            return MainActivity.this.rows.size();
        }

        @Override
        public Object getItem(int i) {
            return MainActivity.this.rows.get(i);
        }

        @Override
        public int getItemViewType(int i) {
            return MainActivity.this.rows.get(i) instanceof String ? 1 : 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            String str;
            Object obj = MainActivity.this.rows.get(i);
            if (obj instanceof String) {
                TextView textView = view instanceof TextView ? (TextView) view : new TextView(MainActivity.this);
                textView.setText((String) obj);
                textView.setTextColor(MainActivity.this.theme == null ? MainActivity.this.getColor(R.color.paper) : MainActivity.this.theme.fg);
                textView.setTextSize(22.0f);
                textView.setPadding(16, 28, 16, 12);
                textView.setTypeface(Typeface.SERIF, 2);
                textView.setFocusable(false);
                textView.setClickable(false);
                textView.setEnabled(false);
                return textView;
            }
            boolean z = false;
            if (view == null || (view instanceof TextView)) {
                view = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_row, viewGroup, false);
            }
            TextView textView2 = (TextView) view.findViewById(R.id.title);
            TextView textView3 = (TextView) view.findViewById(R.id.meta);
            ImageView imageView = (ImageView) view.findViewById(R.id.cover);
            ImageButton imageButton = (ImageButton) view.findViewById(R.id.heart);
            Theme current = MainActivity.this.theme == null ? Theme.current(MainActivity.this) : MainActivity.this.theme;
            textView2.setTextColor(current.fg);
            textView3.setTextColor(current.muted);
            if (obj instanceof Station) {
                final Station station = (Station) obj;
                MainActivity.this.styleCover(imageView, false);
                textView2.setText(station.name);
                textView3.setText(station.meta());
                ArtLoader.bind(imageView, station.favicon, station.name, current);
                imageButton.setVisibility(0);
                boolean has = MainActivity.this.favorites.has(station.id);
                imageButton.setImageResource(has ? R.drawable.ic_star : R.drawable.ic_star_outline);
                imageButton.setColorFilter(has ? current.accent : current.muted);
                imageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view2) {
                        MainActivity.RowAdapter.this.lambda$getView$0(station, view2);
                    }
                });
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view2) {
                        MainActivity.RowAdapter.this.lambda$getView$1(station, view2);
                    }
                });
                view.setFocusable(true);
                view.setClickable(true);
                view.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_BUTTON_A
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                        MainActivity.this.playStation(station);
                        return true;
                    }
                    return false;
                });
            } else if (obj instanceof NamedCount) {
                final NamedCount namedCount = (NamedCount) obj;
                if (MainActivity.this.tab.equals("countries") && namedCount.code.length() == 2) {
                    z = true;
                }
                MainActivity.this.styleCover(imageView, z);
                textView2.setText(namedCount.name);
                textView3.setText(namedCount.count + " Sender");
                if (z) {
                    str = "https://flagcdn.com/80x60/" + namedCount.code.toLowerCase() + ".png";
                } else {
                    str = "";
                }
                ArtLoader.bind(imageView, str, namedCount.name, current);
                imageButton.setVisibility(8);
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view2) {
                        MainActivity.RowAdapter.this.lambda$getView$2(namedCount, view2);
                    }
                });
                view.setFocusable(true);
                view.setClickable(true);
                view.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                        if (MainActivity.this.tab.equals("languages")) {
                            MainActivity.this.openLanguage(namedCount);
                        } else {
                            MainActivity.this.openCountry(namedCount);
                        }
                        return true;
                    }
                    return false;
                });
            }
            return view;
        }

                public /* synthetic */ void lambda$getView$0(Station station, View view) {
            MainActivity.this.favorites.toggle(station);
            if (MainActivity.this.tab.equals("favorites") && MainActivity.this.detailCode.isEmpty()) {
                MainActivity.this.showFavorites();
            } else {
                notifyDataSetChanged();
            }
        }

                public /* synthetic */ void lambda$getView$1(Station station, View view) {
            MainActivity.this.playStation(station);
        }

                public /* synthetic */ void lambda$getView$2(NamedCount namedCount, View view) {
            if (MainActivity.this.tab.equals("languages")) {
                MainActivity.this.openLanguage(namedCount);
            } else {
                MainActivity.this.openCountry(namedCount);
            }
        }
    }
}
