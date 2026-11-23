package fm.welle.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.robolectric.RuntimeEnvironment;
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager;
import com.google.android.play.core.install.model.AppUpdateType;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Play-Variante: keine APK-Installation, Updates nur über Google Play. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlayUpdateTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void playBuildFlagIsSet() {
        assertTrue(BuildConfig.PLAY_BUILD);
    }

    @Test
    public void manifestHasNoInstallPermission() throws Exception {
        PackageInfo info = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        String[] perms = info.requestedPermissions == null ? new String[0] : info.requestedPermissions;
        assertFalse(Arrays.asList(perms).contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertTrue(Arrays.asList(perms).contains("android.permission.INTERNET"));
    }

    @Test
    public void availableUpdateUsesInAppFlow() {
        FakeAppUpdateManager fake = new FakeAppUpdateManager(context);
        fake.setUpdateAvailable(40, AppUpdateType.IMMEDIATE);
        com.google.android.gms.tasks.Task<com.google.android.play.core.appupdate.AppUpdateInfo> task = fake.getAppUpdateInfo();
        ShadowLooper.idleMainLooper();
        UpdateChecker.Info info = UpdateChecker.toInfo(task.getResult(), 34);
        assertEquals(40, info.versionCode);
        assertEquals(UpdateChecker.IN_APP, info.url);
    }

    @Test
    public void noUpdateReportsInstalledVersion() {
        FakeAppUpdateManager fake = new FakeAppUpdateManager(context);
        fake.setUpdateNotAvailable();
        com.google.android.gms.tasks.Task<com.google.android.play.core.appupdate.AppUpdateInfo> task = fake.getAppUpdateInfo();
        ShadowLooper.idleMainLooper();
        UpdateChecker.Info info = UpdateChecker.toInfo(task.getResult(), 34);
        assertEquals(34, info.versionCode);
        assertEquals(UpdateChecker.STORE, info.url);
    }
}
