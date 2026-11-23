package fm.welle.radio;

/** Gemeinsame App-Kennung für alle HTTP-Anfragen (Radio-Browser, Streams, Logos, Updates). */
final class AppInfo {
    private AppInfo() { }

    /** Beispiel: "RadioWelt/4.8 (Android)". Folgt automatisch der versionName aus build.gradle. */
    static String userAgent() {
        return "RadioWelt/" + BuildConfig.VERSION_NAME + " (Android)";
    }
}
