# KlarTaste

Android-Tastatur mit Autokorrektur auf dem Gerät und optionaler KI für ganze Sätze.

Tippfehler wie `Tastaur`, `villeicht` oder `recieve` werden beim Leerzeichen ersetzt. Bekannte Wörter und angefangene Wörter bleiben stehen. Wenn du eine Korrektur sofort rückgängig machst, merkt sich die Tastatur deine Schreibweise.

Grammatik korrigiert ein Sprachmodell, sobald du in den Einstellungen eine OpenAI-kompatible Adresse einträgst (OpenAI, Groq oder Ollama). Ohne Schlüssel verlässt kein Satz das Handy. Passwörter werden weder korrigiert noch verschickt.

## Installieren

1. `releases/KlarTaste-1.0.0.apk` auf das Handy kopieren und öffnen.
2. Installation aus dieser Quelle erlauben.
3. KlarTaste öffnen, **Tastatur aktivieren**, danach **Als Tastatur auswählen**.
4. In einem Textfeld die Tastatur auf KlarTaste stellen.

Die APK ist mit dem Debug-Schlüssel dieses Builds signiert. Ein späteres Update braucht denselben Schlüssel, sonst muss die alte App zuerst deinstalliert werden.

SHA-256: `63125e7d77235aa84a3b21143d1b1cff6fbe5e2136fc02a594f6c72d2388856c`

## KI

In **KI und Korrektur**:

- **OpenAI:** `https://api.openai.com/v1`, Modell `gpt-4o-mini`, dazu der eigene Schlüssel.
- **Groq:** `https://api.groq.com/openai/v1`, Modell `llama-3.3-70b-versatile`.
- **Ollama:** `http://192.168.x.x:11434/v1` (die IP des Rechners, nicht `127.0.0.1`), Modell zum Beispiel `llama3.2`.

Die KI läuft nach einer kurzen Pause am Satzende und über die Taste **KI**.

## Bauen

Java 17 und Android SDK 34.

```bash
cd klartaste
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :engine:test :app:assembleDebug
```

Die installierbare Datei liegt danach unter `app/build/outputs/apk/debug/`.

## Privatsphäre

- Wörterbuch, Vorschläge und Autokorrektur laufen offline.
- Gelernte Wörter bleiben in den App-Einstellungen auf dem Gerät.
- Nur der aktuelle Satz geht an die eingetragene Adresse, und nur wenn die KI an ist.
- Passwort- und Zahlenfelder sind ausgenommen.
