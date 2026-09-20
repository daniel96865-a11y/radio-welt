# Radio Welt – sichere Release-Signierung

Der bisher im öffentlichen Repository enthaltene Android-Debug-Key darf nicht mehr
für neue Releases verwendet werden. Da der Schlüssel öffentlich verfügbar war, gilt
er als kompromittiert. Das Entfernen aus dem aktuellen Git-Stand macht frühere Kopien
nicht wieder geheim.

Die aktuell veröffentlichte Version 3.7 bleibt unverändert verfügbar. Diese Änderung
veröffentlicht bewusst keine neue APK und ändert keine App-Funktion.

## Neue Releases

Private Signierdaten gehören ausschließlich außerhalb von Git. Für einen signierten
Release-Build werden diese Umgebungsvariablen verwendet:

- `RADIO_WELT_KEYSTORE`: absoluter Pfad zum privaten Keystore
- `RADIO_WELT_STORE_PASSWORD`: Keystore-Passwort
- `RADIO_WELT_KEY_PASSWORD`: Passwort des privaten Schlüssels
- `RADIO_WELT_KEY_ALIAS`: optional; Standard `radiowelt`

Ohne diese Variablen erzeugt `./gradlew :app:assembleRelease` bewusst nur eine
unsignierte APK. Dadurch kann ein Release nicht mehr versehentlich mit einem im
Repository liegenden Debug-Key signiert werden.

## Bestehende Installationen

Ein neuer Release-Key ist nicht identisch mit dem Schlüssel der bisherigen Releases.
Ein direkter Schlüsselwechsel muss deshalb vor Veröffentlichung geplant und auf echten
Geräten getestet werden. Android 9+ unterstützt Signaturschlüssel-Rotation mit einer
Signing-Lineage; ältere Android-Versionen benötigen bei einem Schlüsselwechsel in der
Regel eine Neuinstallation.

Der neue private Release-Key und eine gegebenenfalls verwendete Signing-Lineage dürfen
niemals in dieses Repository eingecheckt werden.
