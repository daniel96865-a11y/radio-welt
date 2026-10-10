package fm.welle.radio;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TrackTitleTest {
    @Test public void keepsArtistAndSong() {
        assertEquals("Daft Punk - One More Time", TrackTitle.clean("  Daft Punk - One More Time  "));
    }

    @Test public void stripsQuotesAndCollapsesSpace() {
        assertEquals("Artist - Title", TrackTitle.clean("'Artist   -   Title'"));
    }

    @Test public void dropsPlaceholdersAndUrls() {
        assertEquals("", TrackTitle.clean(null));
        assertEquals("", TrackTitle.clean("  "));
        assertEquals("", TrackTitle.clean("unknown"));
        assertEquals("", TrackTitle.clean("https://example.invalid/stream"));
    }
}
