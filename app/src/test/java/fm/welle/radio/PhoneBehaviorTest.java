package fm.welle.radio;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
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
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w400dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PhoneBehaviorTest {
    public static class TestActivity extends MainActivity {
        @Override void checkForUpdate(boolean manual) { }
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

    @Test public void genreHeaderFoldsDownAndOpensAgainAtTheTop() throws Exception {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ViewGroup header = activity.findViewById(R.id.discover_header);
        TextView jazz = null;
        for (int i = 0; i < ((ViewGroup) activity.findViewById(R.id.genre_row)).getChildCount(); i++) {
            View child = ((ViewGroup) activity.findViewById(R.id.genre_row)).getChildAt(i);
            if (child instanceof TextView && "Jazz".equals(((TextView) child).getText().toString())) jazz = (TextView) child;
        }
        assertTrue(jazz != null);
        layout(activity);
        assertEquals(View.VISIBLE, header.getVisibility());
        assertTrue(header.getHeight() > 0);

        activity.onStationListScrolled(2, -80);
        ShadowLooper.idleMainLooper();
        assertEquals(0, header.getLayoutParams().height);
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, header.getDescendantFocusability());

        activity.onStationListScrolled(0, 0);
        ShadowLooper.idleMainLooper();
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, header.getLayoutParams().height);
        assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, header.getDescendantFocusability());
    }

    @Test public void televisionDoesNotFoldTheGenreHeader() {
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        ViewGroup header = activity.findViewById(R.id.discover_header);
        layout(activity);
        int height = header.getLayoutParams().height;
        activity.foldDiscoverHeader(true);
        ShadowLooper.idleMainLooper();
        assertEquals(height, header.getLayoutParams().height);
        assertFalse(header.getLayoutParams().height == 0 && header.getHeight() == 0);
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

    private static void layout(TestActivity activity) {
        View root = activity.getWindow().getDecorView();
        root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 1920);
    }
}
