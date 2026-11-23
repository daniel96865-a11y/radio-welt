package fm.welle.radio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.widget.Toast;
import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import java.util.concurrent.TimeUnit;

/**
 * Google-Play-Variante (-Pdistribution=play): Updates kommen ausschließlich über Google Play.
 * Die App lädt keine APK und installiert nichts selbst (kein REQUEST_INSTALL_PACKAGES).
 *
 * - Handy/Tablet: Play-In-App-Update (sofortiges Update, Play zeigt Download und Neustart).
 * - Android TV unterstützt In-App-Updates nicht; dort meldet Play einen Fehler, die App zeigt
 *   dann keinen Hinweis (der Play Store aktualisiert selbst). „Auf Update prüfen“ öffnet den Store.
 *
 * Gleiche öffentliche Schnittstelle wie die GitHub-Variante (src/github).
 */
public class UpdateChecker {
    /** Marker-URLs für {@link #open(Context, String)}. */
    static final String IN_APP = "play:in-app";
    static final String STORE = "play:store";

    public static class Info {
        public int versionCode;
        public String versionName = "";
        public String title = "Update verfügbar";
        public String message = "Eine neue Version von Radio Welt ist bei Google Play verfügbar.";
        public String url = "";
    }

    public static int installedCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception unused) {
            return 0;
        }
    }

    public static String installedName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName == null ? "" : packageInfo.versionName;
        } catch (Exception unused) {
            return "";
        }
    }

    /**
     * Fragt Google Play nach einem Update. Nur im Hintergrund-Thread aufrufen.
     * @return null, wenn Play nicht antwortet (z. B. Android TV, nicht aus Play installiert);
     *         sonst Info mit versionCode = installierte Version (kein Update) oder neuere Version.
     */
    public static Info fetch(Context context) {
        try {
            AppUpdateManager manager = AppUpdateManagerFactory.create(context.getApplicationContext());
            AppUpdateInfo update = Tasks.await(manager.getAppUpdateInfo(), 20, TimeUnit.SECONDS);
            return toInfo(update, installedCode(context));
        } catch (Exception unused) {
            return null;
        }
    }

    static Info toInfo(AppUpdateInfo update, int installed) {
        Info info = new Info();
        int availability = update.updateAvailability();
        if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            info.versionCode = Math.max(update.availableVersionCode(), installed + 1);
            info.url = IN_APP;
        } else if (availability == UpdateAvailability.UPDATE_AVAILABLE) {
            info.versionCode = update.availableVersionCode();
            info.url = update.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ? IN_APP : STORE;
        } else {
            info.versionCode = installed;
            info.url = STORE;
        }
        return info;
    }

    /** Startet das Play-Update (Handy) oder öffnet die App im Play Store. */
    public static void open(final Context context, String url) {
        if (IN_APP.equals(url) && context instanceof Activity) {
            final Activity activity = (Activity) context;
            final AppUpdateManager manager = AppUpdateManagerFactory.create(activity.getApplicationContext());
            manager.getAppUpdateInfo()
                    .addOnSuccessListener(activity, update -> {
                        int availability = update.updateAvailability();
                        boolean startable = (availability == UpdateAvailability.UPDATE_AVAILABLE
                                && update.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
                                || availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
                        if (!startable) {
                            openStore(activity);
                            return;
                        }
                        manager.startUpdateFlow(update, activity,
                                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
                                .addOnFailureListener(activity, e -> openStore(activity));
                    })
                    .addOnFailureListener(activity, e -> openStore(activity));
            return;
        }
        openStore(context);
    }

    /** Öffnet die Seite der App im Play Store (App, sonst Browser). */
    public static void openStore(Context context) {
        String pkg = context.getPackageName();
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        if (!(context instanceof Activity)) market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(market);
        } catch (ActivityNotFoundException e) {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
            if (!(context instanceof Activity)) web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(web);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(context, "Google Play ist nicht verfügbar.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
