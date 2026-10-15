package fm.welle.radio;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w400dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PhoneBehaviorTest {
    public static class TestActivity extends MainActivity {
        int plays;
        String playedId;
        @Override void checkForUpdate(boolean manual) { }
        @Override public void playStation(Station station) { plays++; playedId = station.id; }
        @Override void loadRows(String key, String message, boolean focus, RowLoader unused) {
            super.loadRows(key, message, focus, () -> new ArrayList<>(Arrays.asList(
                    "Sender", station("one"), station("two"), station("three"))));
        }
    }

    @Before public void prepare() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, false);
        Prefs.autoplay(RuntimeEnvironment.getApplication(), false);
        PlayerService.current = null;
        PlayerService.playing = false;
        PlayerService.buffering = false;
        PlayerService.trackTitle = "";
        PlayerService.sleepUntil = 0L;
    }

    private static Station station(String id) throws Exception {
        return Station.from(new org.json.JSONObject()
                .put("stationuuid", id)
                .put("name", "Radio " + id)
                .put("url", "https://example.invalid/stream")
                .put("url_resolved", "https://example.invalid/stream"));
    }

    @Test public void genreBlockScrollsAwayWithTheStationList() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        ListView list = activity.findViewById(R.id.list);
        ViewGroup header = activity.findViewById(R.id.discover_header);
        ViewGroup genres = activity.findViewById(R.id.genre_row);
        assertEquals(1, list.getHeaderViewsCount());
        assertSame(list, header.getParent());
        assertEquals(4, genres.getChildCount());
        assertEquals("Jazz", ((TextView) genres.getChildAt(1)).getText().toString());

        activity.findViewById(R.id.tab_countries).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(0, list.getHeaderViewsCount());

        activity.findViewById(R.id.tab_discover).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(1, list.getHeaderViewsCount());
    }

    @Test public void tappingStationsStillWorksBelowTheGenreBlock() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        ListView list = activity.findViewById(R.id.list);
        list.performItemClick(null, 2, 0);
        assertEquals(1, activity.plays);
        assertEquals("one", activity.playedId);
    }

    @Test public void televisionKeepsTheGenreBlockOutsideTheList() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ListView list = activity.findViewById(R.id.list);
        ViewGroup header = activity.findViewById(R.id.discover_header);
        assertEquals(0, list.getHeaderViewsCount());
        assertFalse(header.getParent() instanceof ListView);
    }

    @Test public void tabsSitInTheBottomBarOnThePhone() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ViewGroup nav = activity.findViewById(R.id.bottom_nav);
        ViewGroup tabs = activity.findViewById(R.id.tabs);
        assertEquals(View.VISIBLE, nav.getVisibility());
        assertSame(nav, tabs.getParent());
        assertEquals(View.GONE, activity.findViewById(R.id.tabs_scroll).getVisibility());
        assertEquals(5, tabs.getChildCount());
        String[] labels = {"Start", "Genres", "Länder", "Sprachen", "Favoriten"};
        for (int i = 0; i < labels.length; i++) {
            assertEquals(labels[i], ((TextView) tabs.getChildAt(i)).getText().toString());
        }
    }

    @Test public void televisionKeepsTheTabsInTheRail() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        assertEquals(View.GONE, activity.findViewById(R.id.bottom_nav).getVisibility());
        assertSame(activity.findViewById(R.id.tv_rail), ((View) activity.findViewById(R.id.tabs)).getParent());
        assertEquals("Entdecken", ((TextView) activity.findViewById(R.id.tab_discover)).getText().toString());
    }

    @Test public void searchShowsScopeChipsAndKeepsTheScope() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View chips = activity.findViewById(R.id.search_chips_scroll);
        assertEquals(View.GONE, chips.getVisibility());
        android.widget.EditText search = activity.findViewById(R.id.search);
        search.setText("techno");
        search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        ShadowLooper.idleMainLooper();
        assertEquals(View.VISIBLE, chips.getVisibility());
        activity.findViewById(R.id.chip_countries).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(View.VISIBLE, chips.getVisibility());
        activity.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertEquals(View.GONE, chips.getVisibility());
    }

    @Test public void searchChipsStayHiddenOnTelevision() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        android.widget.EditText search = activity.findViewById(R.id.search);
        search.setText("techno");
        search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        ShadowLooper.idleMainLooper();
        assertEquals(View.GONE, activity.findViewById(R.id.search_chips_scroll).getVisibility());
    }

    @Test public void countryNamesAreGerman() {
        assertEquals("Vereinigtes Königreich", RadioApi.germanCountryName("GB", "United Kingdom"));
        assertEquals("Deutschland", RadioApi.germanCountryName("DE", "Germany"));
        assertEquals("Somewhere", RadioApi.germanCountryName("XX", "Somewhere"));
    }

    @Test public void removingTheTaskStopsTheMusic() throws Exception {
        PlayerService.current = station("live");
        PlayerService.playing = true;
        PlayerService.buffering = true;
        PlayerService.trackTitle = "BoonT - Anytime";
        PlayerService.sleepUntil = System.currentTimeMillis() + 60_000L;
        ServiceController<PlayerService> controller = Robolectric.buildService(PlayerService.class).create();
        PlayerService service = controller.get();
        service.onTaskRemoved(new Intent());
        assertNull(PlayerService.current);
        assertFalse(PlayerService.playing);
        assertFalse(PlayerService.buffering);
        assertEquals("", PlayerService.trackTitle);
        assertEquals(0L, PlayerService.sleepUntil);
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
    }

    @Test public void settingsShowCardHeadersAndSwitchesToggle() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertTrue(hasText(activity.getWindow().getDecorView(), "WIEDERGABE"));
        assertTrue(hasText(activity.getWindow().getDecorView(), "SCHLAF-TIMER"));
        assertTrue(hasText(activity.getWindow().getDecorView(), "AUSSEHEN"));
        SettingsActivity.ToggleView toggle = findToggle(activity.getWindow().getDecorView());
        assertTrue(toggle != null);
        boolean before = toggle.isOn();
        ((View) toggle.getParent()).performClick();
        assertEquals(!before, toggle.isOn());
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static SettingsActivity.ToggleView findToggle(View view) {
        if (view instanceof SettingsActivity.ToggleView) return (SettingsActivity.ToggleView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SettingsActivity.ToggleView found = findToggle(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
