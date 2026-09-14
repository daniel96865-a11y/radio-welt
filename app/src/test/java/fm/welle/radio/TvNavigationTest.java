package fm.welle.radio;

import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w960dp-h540dp-land-mdpi", manifest = Config.DEFAULT_MANIFEST_NAME)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvNavigationTest {
    public static class TestActivity extends MainActivity {
        // ShadowActivity.getCurrentFocus() is a manually-set stub in Robolectric 4.12.
        // Use the real window focus hierarchy for these interaction tests.
        @Override public View getCurrentFocus() { return getWindow().getCurrentFocus(); }
        int plays;
        String playedId;
        CountDownLatch countryEntered;
        CountDownLatch releaseCountry;
        @Override void checkForUpdate(boolean manual) { }
        @Override public void playStation(Station station) { plays++; playedId = station.id; }
        @Override void loadRows(String key, String message, boolean focus, RowLoader unused) {
            super.loadRows(key, message, focus, () -> {
                if (key.equals("countries") && countryEntered != null) {
                    countryEntered.countDown();
                    // Simulate a provider finishing even after cancellation.
                    boolean waiting = true;
                    while (waiting) {
                        try { releaseCountry.await(); waiting = false; }
                        catch (InterruptedException ignored) { }
                    }
                }
                if (key.equals("countries")) return new ArrayList<>(Arrays.asList(new NamedCount("Deutschland", "DE", 10)));
                if (key.startsWith("search:")) return new ArrayList<>(Arrays.asList("Suchergebnisse", station("search")));
                return new ArrayList<>(Arrays.asList("Sender", station("one"), "Weitere", station("two")));
            });
        }
    }

    public static class TestSettingsActivity extends SettingsActivity {
        @Override public View getCurrentFocus() { return getWindow().getCurrentFocus(); }
    }

    @Before public void prepare() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        Prefs.autoplay(RuntimeEnvironment.getApplication(), false);
        PlayerService.current = null;
        PlayerService.playing = false;
        PlayerService.buffering = false;
    }

    private static Station station(String id) throws Exception {
        return Station.from(new org.json.JSONObject().put("stationuuid", id).put("name", "Radio " + id)
                .put("url", "https://example.invalid/stream").put("url_resolved", "https://example.invalid/stream"));
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }

    private static void settle(TestActivity activity) throws Exception {
        Future<?> work = (Future<?>) field(activity, "contentTask");
        if (work != null) work.get(3, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        layout(activity);
    }

    private static void layout(android.app.Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 960, 540);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void key(android.app.Activity activity, int key) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        shadowOf(Looper.getMainLooper()).idle();
        layout(activity);
    }

    @Test public void listNavigationSkipsHeadingsAndReturnsToSameStation() throws Exception {
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup().visible().windowFocusChanged(true)) {
            TestActivity activity = controller.get(); settle(activity);
            ListView list = activity.findViewById(R.id.list);
            assertFalse(list.getAdapter().isEnabled(0));
            assertFalse(list.getAdapter().isEnabled(2));
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue(list.hasFocus());
            assertEquals(1, list.getSelectedItemPosition());
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            assertEquals(3, list.getSelectedItemPosition());
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT);
            assertTrue(activity.findViewById(R.id.play).hasFocus());
            key(activity, KeyEvent.KEYCODE_DPAD_UP);
            assertTrue(list.hasFocus());
            assertEquals(3, list.getSelectedItemPosition());
            key(activity, KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue(activity.findViewById(R.id.tab_discover).hasFocus());
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            assertEquals(3, list.getSelectedItemPosition());
            assertEquals(1f, list.getSelectedView().getScaleX(), 0f);
        }
    }

    @Test public void okStartsOneStationAndMenuTogglesFavoriteOnce() throws Exception {
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup().visible().windowFocusChanged(true)) {
            TestActivity activity = controller.get(); settle(activity);
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            key(activity, KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals(1, activity.plays);
            assertEquals("one", activity.playedId);
            key(activity, KeyEvent.KEYCODE_MENU);
            assertTrue(new Favorites(activity).has("one"));
            assertEquals(1, activity.plays);
            ListView list = activity.findViewById(R.id.list);
            assertEquals(1, list.getSelectedItemPosition());
        }
    }

    @Test public void longOkAddsFavoriteWithoutStartingPlayback() throws Exception {
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup().visible().windowFocusChanged(true)) {
            TestActivity activity = controller.get(); settle(activity);
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(new Favorites(activity).has("one"));
            assertEquals(0, activity.plays);
        }
    }

    @Test public void lateCountryResultCannotReplaceFavoritesOrStealFocus() throws Exception {
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup().visible().windowFocusChanged(true)) {
            TestActivity activity = controller.get(); settle(activity);
            activity.countryEntered = new CountDownLatch(1);
            activity.releaseCountry = new CountDownLatch(1);
            activity.findViewById(R.id.tab_countries).performClick();
            assertTrue(activity.countryEntered.await(2, TimeUnit.SECONDS));
            View favorites = activity.findViewById(R.id.tab_favorites);
            favorites.requestFocus(); favorites.performClick();
            activity.releaseCountry.countDown();
            // Drain the executor after the cancelled provider callback has posted.
            java.util.concurrent.ExecutorService io = (java.util.concurrent.ExecutorService) field(activity, "io");
            io.shutdown(); assertTrue(io.awaitTermination(3, TimeUnit.SECONDS));
            shadowOf(Looper.getMainLooper()).idle(); layout(activity);
            assertEquals("favorites", field(activity, "contentKey"));
            assertEquals(0, ((ListView)activity.findViewById(R.id.list)).getCount());
            assertTrue(favorites.hasFocus());
        }
    }

    @Test public void searchClosesIntoResultsAndOkPlaysTheResult() throws Exception {
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup().visible().windowFocusChanged(true)) {
            TestActivity activity = controller.get(); settle(activity);
            android.widget.EditText search = activity.findViewById(R.id.search);
            search.requestFocus(); search.setText("test");
            search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            settle(activity);
            ListView list = activity.findViewById(R.id.list);
            assertTrue(list.hasFocus());
            assertEquals(1, list.getSelectedItemPosition());
            key(activity, KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("search", activity.playedId);
            assertEquals(1, activity.plays);
        }
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return (TextView)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    @Test public void settingsKeepsFocusAndScrollAfterChangingTimerAndTheme() {
        try (ActivityController<TestSettingsActivity> controller = Robolectric.buildActivity(TestSettingsActivity.class).setup().visible().windowFocusChanged(true)) {
            SettingsActivity activity = controller.get(); layout(activity);
            TextView ninety = findText(activity.getWindow().getDecorView(), "90");
            assertNotNull(ninety); assertTrue(ninety.requestFocus());
            shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS);
            assertSame(ninety, activity.getCurrentFocus());
            int control = ninety.getId();
            View ancestor = ninety;
            while (!(ancestor instanceof ScrollView)) ancestor = (View) ancestor.getParent();
            int scrollOffset = ((ScrollView) ancestor).getScrollY();
            ninety.performClick(); shadowOf(Looper.getMainLooper()).idle(); layout(activity);
            assertEquals(90, Prefs.sleepMin(activity));
            assertEquals(control, activity.getCurrentFocus().getId());
            ancestor = activity.getCurrentFocus();
            while (!(ancestor instanceof ScrollView)) ancestor = (View) ancestor.getParent();
            assertEquals(scrollOffset, ((ScrollView) ancestor).getScrollY());
            assertEquals(1f, activity.getCurrentFocus().getScaleX(), 0f);
            TextView theme = findText(activity.getWindow().getDecorView(), "Ozean");
            assertNotNull(theme);
            View tile = (View)theme.getParent(); tile.requestFocus(); shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS);
            control = tile.getId(); tile.performClick();
            shadowOf(Looper.getMainLooper()).idle(); layout(activity);
            assertEquals("ozean", Theme.current(activity).id);
            assertEquals(control, activity.getCurrentFocus().getId());
        }
    }
}
