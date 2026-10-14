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


## Neuer privater Release-Key ab 20.09.2026

Für zukünftige Radio-Welt-Releases wurde ein neuer privater RSA-3072-Release-Key
erstellt. Der private Keystore und seine Passwörter liegen bewusst **nicht** in GitHub.

Das öffentliche Zertifikat liegt unter:
`signing/RadioWelt-release-2026-public.crt`

Die Fingerprints liegen unter:
`signing/RadioWelt-release-2026-fingerprints.txt`

SHA-256:
`FA:86:A7:DE:FD:87:C4:56:2A:7C:3F:40:80:06:35:42:32:67:D9:DF:CF:DE:FA:7E:AC:EC:D7:8D:6A:97:F8:8E`

Der neue Key wird erst für einen neuen Release verwendet, wenn die Update-Strategie
für bestehende 3.7-Installationen geprüft wurde. Ohne Signing-Lineage bzw. auf älteren
Android-Versionen kann ein Schlüsselwechsel eine Neuinstallation erfordern.

## Aktivierung ab Radio Welt 3.8

Radio Welt 3.8 ist der erste Release mit dem Release-Zertifikat vom 20.09.2026.
Die APK wurde mit einer Signing-Lineage vom bisherigen 3.7-Schlüssel auf den
neuen Schlüssel signiert. Die Rotation ist auf Android 9 (API 28) und neuer
ausgelegt. Die öffentliche Lineage liegt unter
`signing/RadioWelt-release-2026.lineage`.

Android 8 und älter unterstützen diesen Schlüsselwechsel nicht als normales
Update; dort kann eine Neuinstallation erforderlich sein.

## Neuer Release-Key ab 10.10.2026

Der private Schlüssel vom 20.09.2026 ist nicht mehr vorhanden. Ohne ihn kann
keine weitere Signing-Lineage erzeugt werden. Für Radio Welt 3.9 wurde deshalb
ein neuer privater RSA-3072-Schlüssel erstellt.

Öffentliches Zertifikat:
`signing/RadioWelt-release-2026-10-public.crt`

Fingerprints:
`signing/RadioWelt-release-2026-10-fingerprints.txt`

SHA-256:
`9D:FE:19:1E:FE:2C:E4:AF:13:93:FC:DC:23:2A:A9:1C:3C:64:F5:6C:19:43:BA:50:9F:F3:1A:51:49:AE:EA:E9`

Der private Keystore und die Passwörter liegen nicht in Git. Radio Welt 3.9
installiert sich nicht über 3.7 oder 3.8. Die alte App muss zuerst deinstalliert
werden, danach wird 3.9 neu installiert.

## Neuer Release-Key ab 14.10.2026

Der private Schlüssel vom 10.10.2026 stand für den Build von Radio Welt 4.0
nicht zur Verfügung. Ohne ihn kann keine Signing-Lineage erzeugt werden.
4.0 ist deshalb mit einem neuen privaten RSA-3072-Schlüssel signiert.

Öffentliches Zertifikat:
`signing/RadioWelt-release-2026-10-14-public.crt`

Fingerprints:
`signing/RadioWelt-release-2026-10-14-fingerprints.txt`

SHA-256:
`D5:6D:31:3D:2F:EA:D9:72:D6:61:4D:8A:04:C2:CC:4E:3D:72:48:66:56:C4:DB:1C:AF:7E:9C:41:3F:0F:05:61`

Der private Keystore und die Passwörter liegen nicht in Git. Radio Welt 4.0
installiert sich nicht über 3.9. Die alte App muss zuerst deinstalliert werden,
danach wird 4.0 neu installiert.
