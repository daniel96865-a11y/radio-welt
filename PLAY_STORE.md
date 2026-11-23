# Radio Welt im Google Play Store

Es gibt zwei Vertriebsvarianten aus demselben Code:

| | GitHub-APK (Standard) | Google Play (`-Pdistribution=play`) |
|---|---|---|
| Updates | eigener Updater (`src/github/.../UpdateChecker.java`, GitHub-Feed, APK-Download) | Google Play In-App-Updates (`src/play/.../UpdateChecker.java`, Play Core `app-update`) |
| `REQUEST_INSTALL_PACKAGES` | ja | nein (per `src/play/AndroidManifest.xml` entfernt) |
| Update-FileProvider | ja | nein |
| versionCode 4.8 | 33 | 34 |
| `BuildConfig.PLAY_BUILD` | false | true |

Paket-ID bleibt in beiden Varianten `fm.welle.radio`. Eine App für Handy, Tablet und Android TV
(`LEANBACK_LAUNCHER`, `leanback` und `touchscreen` mit `required="false"`, TV-Banner).

## Bauen

```bash
# Play-AAB (Signatur und Sentry-DSN nur über Umgebungsvariablen, nie im Repo)
./gradlew -Pdistribution=play :app:bundleRelease
# Tests und Lint der Play-Variante
./gradlew -Pdistribution=play :app:testReleaseUnitTest :app:lintRelease
```

Signatur: `RADIO_WELT_KEYSTORE`, `RADIO_WELT_STORE_PASSWORD`, `RADIO_WELT_KEY_PASSWORD`,
`RADIO_WELT_KEY_ALIAS` (siehe `SIGNING.md`). Für Play wird mit einem eigenen Upload-Schlüssel
signiert; Play App Signing nutzt den bisherigen Radio-Welt-Schlüssel (SHA-256 `69:19:C7:34…:3A:E3:7E`),
damit GitHub-APK und Play-Version über­einander installierbar bleiben.

## Verhalten der Play-Variante
- Handy/Tablet: Start-Hinweis „Neue Version“, wenn Play ein Update meldet; Tippen startet das
  sofortige Play-Update (Download und Neustart durch Play). Falls nicht möglich: Store-Seite.
- Android TV: Play bietet dort keine In-App-Updates; die App zeigt keinen Hinweis, der Play Store
  aktualisiert selbst. „Auf Update prüfen“ in den Einstellungen öffnet die Store-Seite.
- Die App lädt und installiert keine APK.
