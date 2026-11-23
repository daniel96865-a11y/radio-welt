# Radio Welt – R8-Regeln für den Release-Build (ab 4.8).
#
# Wir verkleinern (Shrinking) und optimieren, benennen aber nichts um.
# Grund: Ohne hochgeladene Mapping-Datei wären Sentry-Stacktraces sonst unlesbar.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Einstiegspunkte stehen im Manifest und werden von AGP automatisch behalten.
# Eigene Modellklassen werden nur manuell mit org.json gelesen/geschrieben (keine
# Reflection). Zur Sicherheit bleiben Felder der Modellklassen erhalten.
-keepclassmembers class fm.welle.radio.Station { <fields>; }
-keepclassmembers class fm.welle.radio.UpdateChecker$Info { <fields>; }
-keepclassmembers class fm.welle.radio.NamedCount { <fields>; }

# Media3 und Sentry bringen eigene consumer-rules mit (u. a. für per Reflection
# geladene Extensions/Integrationen). Die folgenden Zeilen verhindern nur Warnungen
# zu optionalen Abhängigkeiten, die die App bewusst nicht einbindet.
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
