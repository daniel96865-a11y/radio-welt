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
import android.os.Parcelable;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import java.util.HashMap;
import java.util.Map;
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
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    public static final String STATE = "fm.welle.radio.STATE";
    private static final String[] TABS = {"discover", "countries", "languages", "favorites"};
    private boolean television;
    private boolean loading;
    private int contentRequest;
    private Future<?> contentTask;
    private int playbackRequest;
    private int lastListPosition;
    private String contentKey = "";
    private String searchQuery = "";
    private final Map<String, ContentPosition> positions = new HashMap<>();

    private static class ContentPosition {
        final Parcelable state;
        final int selected;
        ContentPosition(Parcelable state, int selected) {
            this.state = state;
            this.selected = selected;
        }
    }

    interface RowLoader { List<Object> load() throws Exception; }

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
        this.television = Prefs.isTelevision(this);
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
                MainActivity.this.onUpdateClick(view);
            }
        });
        this.updateBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.onUpdateBarClick(view);
            }
        });
        this.tabViews = new TextView[]{(TextView) findViewById(R.id.tab_discover), (TextView) findViewById(R.id.tab_countries), (TextView) findViewById(R.id.tab_languages), (TextView) findViewById(R.id.tab_favorites)};
        RowAdapter rowAdapter = new RowAdapter();
        this.adapter = rowAdapter;
        this.list.setAdapter((ListAdapter) rowAdapter);
        this.list.setItemsCanFocus(!this.television);
        this.list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        this.list.setOnItemClickListener((parent, view, position, id) -> activateRow(position));
        this.list.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!loading && rows.get(position) instanceof Station) {
                toggleFavorite((Station) rows.get(position));
                return true;
            }
            return false;
        });
        this.list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!loading && position >= 0) lastListPosition = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
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
                    MainActivity.this.onTabClick(str, view);
                }
            });
            i++;
        }
        this.play.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.onPlayerClick(view);
            }
        });
        this.settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.onSettingsClick(view);
            }
        });
        this.search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public final boolean onEditorAction(TextView textView2, int i2, KeyEvent keyEvent) {
                boolean onSearchSubmit;
                onSearchSubmit = MainActivity.this.onSearchSubmit(textView2, i2, keyEvent);
                return onSearchSubmit;
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
        wireTvNavigation();
        if (this.television) {
            findViewById(R.id.root).setPadding(dp(24), dp(12), dp(24), dp(12));
            findViewById(R.id.tv_help).setVisibility(View.VISIBLE);
            activeTab().requestFocus();
        }
    }

    public void onUpdateClick(View view) {
        if (this.updateUrl.isEmpty()) {
            return;
        }
        UpdateChecker.open(this, this.updateUrl);
    }

    public void onUpdateBarClick(View view) {
        this.updateGo.performClick();
    }

    public void onTabClick(String str, View view) {
        selectTab(str);
    }

    public void onPlayerClick(View view) {
        if (PlayerService.current == null) {
            Toast.makeText(this, "Wähle zuerst einen Sender", 0).show();
        } else {
            PlayerService.toggle(this);
        }
    }

    public void onSettingsClick(View view) {
        startActivity(new Intent(this, (Class<?>) SettingsActivity.class));
    }

    public boolean onSearchSubmit(TextView textView, int action, KeyEvent event) {
        if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                && (event == null || event.getKeyCode() != KeyEvent.KEYCODE_ENTER)) return false;
        if (event != null && (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled())) return true;
        String query = this.search.getText().toString().trim();
        if (query.isEmpty()) return true;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(this.search.getWindowToken(), 0);
        runSearch(query);
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
        if (!this.searchQuery.isEmpty()) {
            this.searchQuery = "";
            this.search.setText("");
            selectTab("discover");
        } else if (!this.detailCode.isEmpty()) {
            selectTab(this.tab);
        } else if (this.television && !activeTab().hasFocus()) {
            activeTab().requestFocus();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        ++this.contentRequest;
        ++this.playbackRequest;
        this.io.shutdownNow();
        this.ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isFavoriteKey(keyCode) && this.list.hasFocus()) {
            if (event.getRepeatCount() == 0) toggleFavoriteOnFocusedRow();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (event.getRepeatCount() > 0) return true;
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (PlayerService.playing || PlayerService.buffering) PlayerService.toggle(this);
            } else if (PlayerService.current == null) {
                playFocusedOrFirstStation();
            } else if (keyCode != KeyEvent.KEYCODE_MEDIA_PLAY
                    || (!PlayerService.playing && !PlayerService.buffering)) {
                PlayerService.toggle(this);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isFavoriteKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BOOKMARK;
    }

    private void playFocusedOrFirstStation() {
        if (this.loading) return;
        int pos = this.list.getSelectedItemPosition();
        if (pos >= 0 && pos < this.rows.size() && this.rows.get(pos) instanceof Station) {
            playStation((Station) this.rows.get(pos));
            return;
        }
        for (Object row : this.rows) {
            if (row instanceof Station) {
                playStation((Station) row);
                return;
            }
        }
        Toast.makeText(this, "Wähle zuerst einen Sender", Toast.LENGTH_SHORT).show();
    }

    private TextView activeTab() {
        for (int i = 0; i < TABS.length; i++) if (TABS[i].equals(this.tab)) return this.tabViews[i];
        return this.tabViews[0];
    }

    private int enabledPosition(int from, int direction) {
        for (int i = from; i >= 0 && i < this.rows.size(); i += direction) {
            if (!(this.rows.get(i) instanceof String)) return i;
        }
        return -1;
    }

    private void focusList() {
        int position = enabledPosition(Math.min(this.lastListPosition, this.rows.size() - 1), 1);
        if (position < 0) position = enabledPosition(this.rows.size() - 1, -1);
        if (position < 0) {
            this.play.requestFocus();
            return;
        }
        this.list.requestFocus();
        if (this.list.getSelectedItemPosition() != position) this.list.setSelection(position);
    }

    private void wireTvNavigation() {
        if (!this.television) return;
        this.search.setNextFocusRightId(R.id.settings);
        this.search.setNextFocusLeftId(R.id.search);
        this.search.setNextFocusUpId(R.id.search);
        this.search.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) activeTab().requestFocus();
                return true;
            }
            return false;
        });
        this.settingsBtn.setNextFocusLeftId(R.id.search);
        this.settingsBtn.setNextFocusRightId(R.id.settings);
        this.settingsBtn.setNextFocusDownId(R.id.tab_favorites);
        this.settingsBtn.setNextFocusUpId(R.id.settings);
        for (int i = 0; i < this.tabViews.length; i++) {
            final int index = i;
            TextView chip = this.tabViews[i];
            chip.setNextFocusUpId(i == this.tabViews.length - 1 ? R.id.settings : R.id.search);
            chip.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT
                        && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        int next = Math.max(0, Math.min(this.tabViews.length - 1,
                                index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                        this.tabViews[next].requestFocus();
                    } else if (this.updateBar.getVisibility() == View.VISIBLE) {
                        this.updateGo.requestFocus();
                    } else {
                        focusList();
                    }
                }
                return true;
            });
        }
        this.list.setOnKeyListener((v, key, event) -> {
            int position = this.list.getSelectedItemPosition();
            boolean toTabs = key == KeyEvent.KEYCODE_DPAD_LEFT
                    || (key == KeyEvent.KEYCODE_DPAD_UP && enabledPosition(position - 1, -1) < 0);
            boolean toPlayer = key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || (key == KeyEvent.KEYCODE_DPAD_DOWN && enabledPosition(position + 1, 1) < 0);
            if (!toTabs && !toPlayer) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (toTabs) activeTab().requestFocus(); else this.play.requestFocus();
            }
            return true;
        });
        this.play.setNextFocusDownId(R.id.play);
        this.play.setNextFocusRightId(R.id.play);
        this.play.setOnKeyListener((v, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_LEFT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (enabledPosition(0, 1) >= 0) focusList(); else activeTab().requestFocus();
            }
            return true;
        });
        this.updateBar.setFocusable(false);
        this.updateGo.setOnKeyListener((v, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) activeTab().requestFocus(); else focusList();
            }
            return true;
        });
    }

    private void attachFocusScale(View view, float ignoredScale) {
        // Keep the hit targets and layout stationary; the drawable supplies the focus ring.
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setElevation(0f);
        view.setOnFocusChangeListener(null);
    }

    private boolean toggleFavoriteOnFocusedRow() {
        int position = this.list.getSelectedItemPosition();
        if (!this.loading && position >= 0 && position < this.rows.size()
                && this.rows.get(position) instanceof Station) {
            toggleFavorite((Station) this.rows.get(position));
            return true;
        }
        return false;
    }

    private void toggleFavorite(Station station) {
        boolean now = !this.favorites.has(station.id);
        this.favorites.toggle(station);
        Toast.makeText(this, now ? "Zu Favoriten" : "Favorit entfernt", Toast.LENGTH_SHORT).show();
        if (this.tab.equals("favorites") && this.detailCode.isEmpty()) {
            showFavorites();
        } else if (this.adapter != null) {
            this.adapter.notifyDataSetChanged();
        }
    }

    private void activateRow(int position) {
        if (this.loading || position < 0 || position >= this.rows.size()) {
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
        this.searchQuery = "";
        for (int i = 0; i < this.tabViews.length; i++) {
            boolean equals = TABS[i].equals(str);
            if (this.theme != null) {
                paintTab(this.tabViews[i], equals);
            } else {
                this.tabViews[i].setBackgroundResource(equals ? R.drawable.bg_tab_on : R.drawable.bg_tab_focus);
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
        loadRows("discover", "Keine Sender gefunden", false, () -> {
            List<Object> result = buildPersonal(ListenHistory.get(this));
            result.add("TECHNO4EVER");
            result.addAll(Featured.techno4ever());
            result.add("Beliebt in Deutschland");
            result.addAll(RadioApi.search(null, "DE", null, null, 20));
            result.add("Weltweit");
            result.addAll(RadioApi.search(null, null, null, null, 30));
            return result;
        });
    }

    private int beginContent(String key) {
        if (this.contentTask != null) this.contentTask.cancel(true);
        if (!this.loading && !this.contentKey.isEmpty()) {
            positions.put(this.contentKey, new ContentPosition(this.list.onSaveInstanceState(), this.lastListPosition));
        }
        this.contentKey = key;
        ContentPosition saved = this.positions.get(key);
        this.lastListPosition = saved == null ? 0 : saved.selected;
        this.loading = true;
        this.rows.clear();
        this.adapter.notifyDataSetChanged();
        setBusy(true);
        return ++this.contentRequest;
    }

    void loadRows(String key, String emptyMessage, boolean focusResult, RowLoader loader) {
        final View focusOwner = getCurrentFocus();
        final int request = beginContent(key);
        this.contentTask = this.io.submit(() -> {
            try {
                List<Object> result = loader.load();
                this.ui.post(() -> {
                    if (request != this.contentRequest || isDestroyed()) return;
                    this.rows.addAll(result);
                    done(emptyMessage);
                    if (this.television && focusResult && getCurrentFocus() == focusOwner) {
                        if (enabledPosition(0, 1) >= 0) focusList(); else activeTab().requestFocus();
                    }
                });
            } catch (Exception error) {
                this.ui.post(() -> {
                    if (request == this.contentRequest && !isDestroyed()) fail(error);
                });
            }
        });
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
        loadRows("countries", "Keine Länder gefunden", false, () -> new ArrayList<Object>(RadioApi.countries()));
    }

    private void loadLanguages() {
        loadRows("languages", "Keine Sprachen gefunden", false, () -> new ArrayList<Object>(RadioApi.languages()));
    }

    public void showFavorites() {
        boolean hadListFocus = this.list.hasFocus();
        beginContent("favorites");
        this.rows.addAll(this.favorites.all());
        done(this.television ? "Noch keine Favoriten. Bei einem Sender OK gedrückt halten oder die Menü-Taste drücken."
                : "Noch keine Favoriten — tippe den Stern bei einem Sender.");
        if (this.television && hadListFocus) {
            if (enabledPosition(0, 1) >= 0) focusList(); else activeTab().requestFocus();
        }
    }

    private void runSearch(final String query) {
        this.tab = "discover";
        this.detailCode = "";
        this.detailTitle = "";
        this.searchQuery = query;
        for (int i = 0; i < this.tabViews.length; i++) paintTab(this.tabViews[i], TABS[i].equals(this.tab));
        loadRows("search:" + query, "Nichts gefunden für „" + query + "“", true, () -> {
            List<Object> result = new ArrayList<>();
            result.add("Suche: " + query);
            if (Featured.matchesSearch(query)) result.addAll(Featured.techno4ever());
            result.addAll(RadioApi.search(query, null, null, null, 50));
            return result;
        });
    }

    public void openCountry(final NamedCount country) {
        this.detailCode = country.code;
        this.detailTitle = country.name;
        loadRows("country:" + country.code, "Keine Sender", true, () -> {
            List<Object> result = new ArrayList<>();
            result.add(country.name);
            result.addAll(RadioApi.search(null, country.code, null, null, 60));
            return result;
        });
    }

    public void openLanguage(final NamedCount language) {
        this.detailCode = language.name;
        this.detailTitle = language.name;
        loadRows("language:" + language.name, "Keine Sender", true, () -> {
            List<Object> result = new ArrayList<>();
            result.add(language.name);
            result.addAll(RadioApi.search(null, null, language.name, null, 60));
            return result;
        });
    }

    public void playStation(final Station station) {
        final int request = ++this.playbackRequest;
        Prefs.saveLast(this, station);
        Toast.makeText(this, "Verbindet " + station.name, Toast.LENGTH_SHORT).show();
        this.io.execute(() -> {
            String url = RadioApi.resolve(station.id, station.url);
            this.ui.post(() -> {
                if (request == this.playbackRequest && !isDestroyed()) PlayerService.play(this, station, url);
            });
        });
    }

    private void setBusy(boolean z) {
        this.busy.setVisibility(z ? 0 : 8);
        if (z) {
            this.empty.setVisibility(8);
        }
    }

    private void done(String str) {
        setBusy(false);
        this.loading = false;
        this.adapter.notifyDataSetChanged();
        ContentPosition saved = this.positions.get(this.contentKey);
        if (saved != null) this.list.onRestoreInstanceState(saved.state);
        boolean z = true;
        if (!this.rows.isEmpty() && (this.rows.size() != 1 || !(this.rows.get(0) instanceof String))) {
            z = false;
        }
        this.empty.setText(str);
        this.empty.setVisibility(z ? 0 : 8);

    }
    public void fail(Exception exc) {
        this.loading = false;
        setBusy(false);
        this.rows.clear();
        this.adapter.notifyDataSetChanged();
        this.empty.setText("Verzeichnis nicht erreichbar. Prüfe deine Verbindung.");
        this.empty.setVisibility(0);
        if (this.television && this.list.hasFocus()) activeTab().requestFocus();
    }

    private void applyTheme() {
        this.theme = Theme.current(this);
        findViewById(R.id.root).setBackgroundColor(this.theme.bg);
        getWindow().setStatusBarColor(this.theme.bg);
        getWindow().setNavigationBarColor(this.theme.bg);
        int systemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(this.theme.light ? systemUiVisibility | 8208 : systemUiVisibility & (-8209));
        ((TextView) findViewById(R.id.brand)).setTextColor(this.theme.fg);
        this.search.setBackground(focusableRound(this.theme.surface, brighten(this.theme.surface, 0.18f),
                this.theme.fg, this.density * 22.0f, 3));
        this.search.setTextColor(this.theme.fg);
        this.search.setHintTextColor(this.theme.muted);
        this.settingsBtn.setColorFilter(this.theme.fg);
        this.settingsBtn.setBackground(focusableOval(Color.TRANSPARENT, this.theme.fg, this.theme.chip));
        this.empty.setTextColor(this.theme.muted);
        ((TextView) findViewById(R.id.tv_help)).setTextColor(this.theme.muted);
        if (this.television) {
            this.list.setDrawSelectorOnTop(true);
            this.list.setSelector(focusableRound(Color.TRANSPARENT, Color.TRANSPARENT,
                    this.theme.fg, dp(10), 3));
        }
        findViewById(R.id.player).setBackgroundColor(this.theme.surface);
        this.nowTitle.setTextColor(this.theme.fg);
        this.nowMeta.setTextColor(this.theme.muted);
        this.play.setBackground(focusableOval(this.theme.accent, this.theme.fg, this.theme.accent));
        this.play.setColorFilter(this.theme.onAccent);
        this.list.setDivider(new ColorDrawable(this.theme.line));
        this.list.setDividerHeight(Math.max(1, (int) this.density));
        for (int i = 0; i < this.tabViews.length; i++) {
            paintTab(this.tabViews[i], TABS[i].equals(this.tab));
        }
        LinearLayout linearLayout = this.updateBar;
        if (linearLayout != null) {
            linearLayout.setBackground(focusableRound(this.theme.accent, brighten(this.theme.accent, 0.15f),
                    Color.WHITE, 0f, 3));
            this.updateText.setTextColor(this.theme.onAccent);
            this.updateGo.setTextColor(this.theme.onAccent);
            this.updateGo.setBackground(focusableRound(Color.argb(40, 255, 255, 255),
                    Color.argb(90, 255, 255, 255), Color.WHITE, this.density * 8.0f, 3));
        }
        roundClip(this.nowArt);
    }

    private void paintTab(TextView tab, boolean selected) {
        int fill = selected ? this.theme.accent : this.theme.chip;
        int focusedFill = brighten(fill, 0.22f);
        tab.setBackground(focusableRound(fill, focusedFill, this.theme.fg, this.density * 20.0f, 3));
        tab.setTextColor(selected ? this.theme.onAccent : this.theme.fg);
    }

    void checkForUpdate(final boolean z) {
        long j = getSharedPreferences("welle", 0).getLong("update_checked", 0L);
        if (z || System.currentTimeMillis() - j >= 1800000) {
            this.io.execute(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.fetchUpdate(z);
                }
            });
        }
    }

    public void fetchUpdate(final boolean z) {
        final UpdateChecker.Info fetch = UpdateChecker.fetch();
        getSharedPreferences("welle", 0).edit().putLong("update_checked", System.currentTimeMillis()).apply();
        final int installedCode = UpdateChecker.installedCode(this);
        this.ui.post(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.showUpdate(fetch, installedCode, z);
            }
        });
    }

    public void showUpdate(UpdateChecker.Info info, int i, boolean z) {
        if (isDestroyed()) return;
        if (info == null || info.versionCode <= i || info.url.isEmpty()) {
            LinearLayout linearLayout = this.updateBar;
            if (linearLayout != null) {
                if (this.television && linearLayout.hasFocus()) activeTab().requestFocus();
                linearLayout.setVisibility(8);
            }
            if (z) {
                Toast.makeText(this, "Du hast die neueste Version.", 0).show();
                return;
            }
            return;
        }
        this.updateUrl = info.url;
        String hint = Prefs.isTelevision(this) ? " — OK: Jetzt laden" : " — tippen: Jetzt laden";
        this.updateText.setText("Neue Version" + (info.versionName.isEmpty() ? "" : " " + info.versionName) + hint);
        this.updateBar.setVisibility(0);
        this.updateBar.setFocusable(true);
        this.updateBar.setClickable(true);
        this.updateGo.setFocusable(true);
        this.updateGo.setClickable(true);
        this.updateBar.setFocusable(!this.television);
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
        return focusableOval(fill, focusedStroke, brighten(fill, 0.2f));
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

    private StateListDrawable rowFocusDrawable(boolean playing) {
        int accent = this.theme != null ? this.theme.accent : Color.parseColor("#E8C27A");
        int surface = this.theme != null ? this.theme.surface : Color.parseColor("#141418");
        GradientDrawable focusedPlaying = new GradientDrawable();
        focusedPlaying.setColor(brighten(surface, 0.28f));
        focusedPlaying.setCornerRadius(this.density * 10f);
        focusedPlaying.setStroke(dp(4), accent);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(brighten(surface, 0.35f));
        focused.setCornerRadius(this.density * 10f);
        focused.setStroke(dp(4), Color.WHITE);
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(brighten(surface, 0.12f));
        selected.setCornerRadius(this.density * 10f);
        selected.setStroke(dp(2), accent);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius(this.density * 10f);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_activated}, focusedPlaying);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_activated}, selected);
        states.addState(new int[]{}, normal);
        return states;
    }

    private int brighten(int color, float amount) {
        int target = this.theme == null ? Color.WHITE : this.theme.fg;
        float blend = Math.min(0.12f, amount);
        return Color.rgb(
                Math.round(Color.red(color) * (1 - blend) + Color.red(target) * blend),
                Math.round(Color.green(color) * (1 - blend) + Color.green(target) * blend),
                Math.round(Color.blue(color) * (1 - blend) + Color.blue(target) * blend));
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
            this.play.setContentDescription("Wiedergabe starten");
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
        imageButton.setContentDescription(PlayerService.playing ? "Wiedergabe pausieren" : "Wiedergabe starten");
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
        public boolean areAllItemsEnabled() { return false; }

        @Override
        public boolean isEnabled(int position) { return !(getItem(position) instanceof String); }

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
                imageButton.setFocusable(false);
                imageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view2) {
                        MainActivity.RowAdapter.this.onFavoriteClick(station, view2);
                    }
                });
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view2) {
                        MainActivity.RowAdapter.this.onStationClick(station, view2);
                    }
                });
                boolean playing = PlayerService.current != null
                        && station.id != null
                        && station.id.equals(PlayerService.current.id);
                view.setActivated(playing);
                view.setTag(station);
                view.setBackground(MainActivity.this.rowFocusDrawable(playing));
                ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                view.setFocusable(true);
                view.setFocusableInTouchMode(false);
                view.setClickable(true);
                view.setOnLongClickListener(v -> {
                    MainActivity.this.toggleFavorite(station);
                    return true;
                });
                MainActivity.this.attachFocusScale(view, 1.03f);
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
                        MainActivity.RowAdapter.this.onCategoryClick(namedCount, view2);
                    }
                });
                view.setActivated(false);
                view.setTag(namedCount);
                view.setBackground(MainActivity.this.rowFocusDrawable(false));
                ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                view.setFocusable(true);
                view.setFocusableInTouchMode(false);
                view.setClickable(true);
                view.setOnLongClickListener(null);
                MainActivity.this.attachFocusScale(view, 1.03f);
            }
            if (MainActivity.this.television) {
                view.setFocusable(false);
                view.setOnClickListener(null);
                view.setOnLongClickListener(null);
                view.setClickable(false);
                view.setLongClickable(false);
                view.setOnKeyListener(null);
                imageButton.setFocusable(false);
                imageButton.setClickable(false);
            }
            return view;
        }

        public void onFavoriteClick(Station station, View view) {
            MainActivity.this.toggleFavorite(station);
        }

        public void onStationClick(Station station, View view) {
            MainActivity.this.playStation(station);
        }

        public void onCategoryClick(NamedCount namedCount, View view) {
            if (MainActivity.this.tab.equals("languages")) {
                MainActivity.this.openLanguage(namedCount);
            } else {
                MainActivity.this.openCountry(namedCount);
            }
        }
    }
}
