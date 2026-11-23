package fm.welle.radio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppInfoTest {
    @Test public void userAgentFollowsVersionName() {
        assertEquals("RadioWelt/" + BuildConfig.VERSION_NAME + " (Android)", AppInfo.userAgent());
        assertEquals("RadioWelt/4.8 (Android)", AppInfo.userAgent());
    }
}
