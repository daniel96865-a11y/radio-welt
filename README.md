# Radio Welt

Internetradio-App für Android-Handys und Android TV / Fire TV (`fm.welle.radio`) von DG Studios. Tausende Sender weltweit, im dunklen „Atelier"-Look mit goldenen Akzenten.

## Download

**Neueste Version (APK):**
https://github.com/daniel96865-a11y/radio-welt/releases/latest/download/RadioWelt-latest.apk

Alle Versionen: [Releases](https://github.com/daniel96865-a11y/radio-welt/releases) · Änderungen: [releases/CHANGELOG.md](releases/CHANGELOG.md)

Die App prüft selbst auf Updates (Tab **Updates** bzw. Hinweis in der App). Der Update-Feed liegt in [`docs/updates.json`](docs/updates.json).

> Hinweis: Ab Version 4.0 ist die App mit einem neuen Schlüssel signiert. Wer noch eine Version vor 4.0 hat, muss sie einmal deinstallieren. Details: [SIGNING.md](SIGNING.md).

## Funktionen

- Sendersuche mit Filtern für Sender, Genres und Länder
- Bunte Genre-Kacheln, Länder (mit deutschen Namen) und Sprachen
- Favoriten, Hörverlauf und „Weiterhören"
- Player mit großem Cover, aktuellem Titel, „Ähnliche Sender", Teilen und Schlaf-Timer
- Mini-Player, Lautstärke- und Puffer-Einstellungen
- Bedienung per Touch (untere Leiste) und per Fernbedienung am TV (Leiste links, goldener Fokus)
- Automatische Update-Prüfung

## Selbst bauen

Voraussetzungen: JDK 17 und Android SDK (minSdk 26, targetSdk 34).

```bash
./gradlew testDebugUnitTest   # Tests
./gradlew assembleDebug       # Debug-APK unter app/build/outputs/apk/debug/
```

Signierte Release-Builds brauchen einen privaten Schlüssel außerhalb von Git (Umgebungsvariablen siehe [SIGNING.md](SIGNING.md)). Wie ein neues Release veröffentlicht wird, steht in [docs/PUBLISH.md](docs/PUBLISH.md).

## Aufbau

- `app/` – Android-App (Java)
- `docs/` – Update-Feeds und Veröffentlichungsanleitung
- `releases/` – Changelog, Prüfsumme und APK-Teile für den Release-Workflow
- `signing/` – öffentliche Zertifikate und Fingerabdrücke (keine privaten Schlüssel)
- `.github/workflows/` – automatische Veröffentlichung der Releases
