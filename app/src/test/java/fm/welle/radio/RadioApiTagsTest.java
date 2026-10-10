package fm.welle.radio;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RadioApiTagsTest {
    @Test public void keepsPopularTagsAndSortsByStationCount() throws Exception {
        JSONArray array = new JSONArray()
                .put(new JSONObject().put("name", "jazz").put("stationcount", 40))
                .put(new JSONObject().put("name", "a").put("stationcount", 100))
                .put(new JSONObject().put("name", "pop").put("stationcount", 500))
                .put(new JSONObject().put("name", "weird/mix").put("stationcount", 80))
                .put(new JSONObject().put("name", "tiny").put("stationcount", 3));

        List<NamedCount> tags = RadioApi.tagsFrom(array);

        assertEquals(2, tags.size());
        assertEquals("Pop", tags.get(0).name);
        assertEquals("pop", tags.get(0).code);
        assertEquals(500, tags.get(0).count);
        assertEquals("Jazz", tags.get(1).name);
    }
}
