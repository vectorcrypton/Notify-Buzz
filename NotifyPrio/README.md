# NotifyPrio – Benachrichtigungen priorisieren & bündeln

Kotlin + Jetpack Compose · Ziel: POCO X7 · HyperOS 3.0 (Android 16) · `compileSdk/targetSdk 36` · kein Root.

## Was die App tut

- **App- & Kanal-Regeln** pro App und pro Notification-Channel: *Normal · Wichtig · Bündeln · Blocken*.
  Kanal-Regel schlägt App-Regel (z. B. „App blocken, nur Kanal ‚Nachrichten‘ wichtig“).
- **Apps finden & sortieren**: Suche, Filter nach Kategorien (z. B. Musik, Shopping, Social, Finanzen oder Reisen)
  und „Nur mit Regel“. Beliebig viele benannte Gruppen lassen sich anlegen; Apps können jeweils einer Gruppe
  zugewiesen werden. Die Benachrichtigungen jeder Gruppe erscheinen gemeinsam in einer eigenen Zusammenfassung.
  Apps ohne Zuordnung landen in „Weitere Apps“.
- **Material You**: dynamische Systemfarben mit animiertem Auf- und Zuklappen der App-Karten.
- **Fokus-Modi**: *Aus* · *Fokus* (nur „Wichtig“ kommt durch, Rest wird still gebündelt) · *Ruhe* (Rest wird entfernt, im Protokoll abrufbar).
  Optional wird der Modus ins System-„Nicht stören“ gespiegelt.
- **Bündeln (app-übergreifend)**: Jede abgefangene Meldung wird als *eigene Karte* neu gepostet
  (App-Icon, Titel, Text, Zeit) und hängt unter der Zusammenfassung ihrer App-Gruppe.
  Auf-/Zuklappen übernimmt HyperOS (wie bei „Reddit(2)“). Ein Tipp auf eine Karte löst direkt die
  **Original-Aktion** der Ursprungs-App aus; Wegwischen entfernt sie aus dem Bündel.
  Die Tab-Liste „Bündel“ zeigt dieselben Einträge zusätzlich in der App.
- **Verlässlichkeit**: Foreground Service (`specialUse`), Listener-Watchdog (`requestRebind`), Boot-Receiver,
  optional Selbst-Reaktivierung des Zugriffs via `WRITE_SECURE_SETTINGS`.
- Nie angefasst werden: laufende/nicht löschbare Meldungen, Foreground-Service-Meldungen, Anrufe, Wecker, Navigation, Medien, Fortschritt, Vollbild-Intents.

---

## 1) Projekt öffnen & APK bauen (Android Studio)

1. **Android Studio** (aktuelle Version) installieren. JDK 17+ ist enthalten.
2. ZIP entpacken → `File ▸ Open…` → Ordner **NotifyPrio** wählen.
   - Der Gradle-Wrapper-JAR liegt nicht bei; Android Studio lädt Gradle 8.13 anhand von `gradle/wrapper/gradle-wrapper.properties` selbst.
   - Fragt Android Studio nach einem *AGP-/Gradle-Upgrade*: du kannst es ablehnen (Projekt ist auf AGP 8.13 / Kotlin 2.2.20 abgestimmt).
3. Warten bis **Gradle Sync** fertig ist (SDK-Platform *Android 16 / API 36* wird ggf. zum Download angeboten → akzeptieren).
4. Menü **`Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)`**.
5. Nach dem Build erscheint unten rechts *„locate“* → Datei:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Installieren:
   - **Per ADB (empfohlen):** `adb install -r app-debug.apk`
   - oder die APK aufs Handy kopieren und mit dem Dateimanager öffnen („Installation aus unbekannten Quellen“ erlauben).

Das Release-Build (`Build Variants ▸ release`) ist absichtlich mit dem Debug-Key signiert, damit es ebenfalls direkt installierbar ist.

---

## 2) Einrichtung per ADB (PC/Mac)

### Vorbereitung am POCO X7
1. `Einstellungen ▸ Über das Telefon` → 7× auf **„HyperOS-Version“/„Build-Nummer“** tippen (Entwickleroptionen freischalten).
2. `Einstellungen ▸ Zusätzliche Einstellungen ▸ Entwickleroptionen`:
   - **USB-Debugging** an
   - **USB-Debugging (Sicherheitseinstellungen)** an (nötig für `pm grant` & Co. auf Xiaomi; verlangt ggf. Mi-Konto/SIM)
   - **Über USB installieren** an (falls vorhanden)
3. Handy per USB verbinden, Debugging-Dialog bestätigen, am PC prüfen: `adb devices`

### Befehle (Paketname: `de.notifyprio.app`)

```bash
# Benachrichtigungen der App erlauben
adb shell pm grant de.notifyprio.app android.permission.POST_NOTIFICATIONS

# Benachrichtigungszugriff (Listener) direkt erteilen – umgeht „Eingeschränkte Einstellungen“
adb shell cmd notification allow_listener de.notifyprio.app/de.notifyprio.service.PriorityListenerService

# Optional: Listener kann sich nach Systemkills selbst wieder aktivieren
adb shell pm grant de.notifyprio.app android.permission.WRITE_SECURE_SETTINGS

# Optional: „Nicht stören“-Zugriff (für „System-DND mitschalten“)
adb shell cmd notification allow_dnd de.notifyprio.app

# Akku/Doze: App von Optimierung ausnehmen
adb shell dumpsys deviceidle whitelist +de.notifyprio.app
adb shell cmd appops set de.notifyprio.app RUN_IN_BACKGROUND allow
adb shell cmd appops set de.notifyprio.app RUN_ANY_IN_BACKGROUND allow
```

Prüfen:
```bash
adb shell cmd notification list_listeners          # zeigt de.notifyprio.app/...PriorityListenerService
adb shell dumpsys deviceidle whitelist | grep notifyprio
```

> Hinweis: Die Befehle stehen auch im Tab **Setup** der App (mit deinem tatsächlichen Paketnamen, lang drücken zum Kopieren).

---

## 3) HyperOS 3.0 – Autostart & Akku

Menünamen können je nach Sprache/Build leicht abweichen. Der Tab **Setup** hat Buttons, die direkt dorthin springen (fällt bei Fehlern auf die App-Info zurück).

1. **Autostart**: `Einstellungen ▸ Apps ▸ Berechtigungen ▸ Autostart` → **NotifyPrio** aktivieren.
   (alternativ: `Apps ▸ Apps verwalten ▸ NotifyPrio ▸ Autostart`)
2. **Akku**: `Einstellungen ▸ Apps ▸ Apps verwalten ▸ NotifyPrio ▸ Akku sparen` → **Keine Einschränkungen**.
3. **Android-Akku-Optimierung**: in der App `Setup ▸ Akku-Optimierung ▸ Ausnahme anfordern` → *Zulassen*.
4. **App im Hintergrund fixieren**: Übersicht (Recents) öffnen → NotifyPrio-Karte **lang drücken** → **Schloss-Symbol**.
5. **Benachrichtigungen**: `Apps ▸ Apps verwalten ▸ NotifyPrio ▸ Benachrichtigungen` → erlaubt lassen (Dauerbetrieb-Meldung nicht ausblenden, sonst bremst HyperOS den Dienst eher).
6. **Notfalls:** Entwickleroptionen ▸ „Systemoptimierung/HyperOS-Optimierung“ (falls vorhanden) deaktivieren – nur als letzter Ausweg, kann Systemverhalten ändern.

### Erststart-Reihenfolge
1. App öffnen → Tab **Setup** → *Benachrichtigungszugriff* aktivieren (oder per ADB, siehe oben).
2. *Eigene Benachrichtigungen* erlauben, *Dauerbetrieb* an.
3. HyperOS-Autostart & Akku wie oben.
4. Tab **Apps**: Regeln setzen · Tab **Fokus**: Modus wählen.

---

## 4) Bekannte technische Grenzen (ehrlich)

- **Kurzer Ton möglich**: Ein normaler Listener kann Meldungen erst *nach* dem Eintreffen entfernen. Für lautloses Unterdrücken ohne Blip: *„System-Nicht-stören mitschalten“* aktivieren oder den Kanal über den **„System“-Button** neben dem Kanal direkt in den Android-Einstellungen stummschalten.
- **Kanäle werden gelernt**: Android erlaubt Listenern das direkte Auslesen fremder Kanäle nur mit Companion-/Assistenten-Rolle. Daher erscheinen Kanäle in der Liste, sobald die jeweilige App eine Benachrichtigung gesendet hat.
- **Sensible Inhalte** (z. B. Einmalcodes) können seit Android 15 für Listener geschwärzt sein.
- **Gruppen-Kopfzeile**: HyperOS beschriftet die Gruppe mit dem Namen *dieser* App (z. B. „NotifyPrio (5)“). Willst du „Bündel“ o. Ä.,
  ändere `app_name` in `app/src/main/res/values/strings.xml` (ändert auch den Launcher-Namen).
- **Karten-Optik**: Als Icon links erscheint das Icon der Ursprungs-App (Absender-Avatare und Buttons wie „Antworten“ der Original-Meldung gehen beim Neu-Posten verloren).
- **Original-Aktion**: Sie wird direkt an die neue Karte gehängt und funktioniert auch nach einem Neustart der App, solange die Karte in der Leiste steht.
  In der App-Liste „Bündel“ öffnet sie nach einem Prozess-Neustart nur noch die jeweilige App.
- Foreground-Service-Start aus dem Hintergrund kann Android verbieten; einmal die App öffnen genügt, danach wird er per Listener/Boot-Receiver erneuert.

## 5) Projektstruktur

```
app/src/main/
├─ AndroidManifest.xml
└─ java/de/notifyprio/
   ├─ NotifyPrioApp.kt
   ├─ data/      Models · FilterManager (Regeln, DND-Logik, Entscheidung) · NotificationStore
   ├─ service/   PriorityListenerService · KeepAliveService (FGS specialUse) · BundleNotifier (Gruppe + Kind-Karten) · Receiver
   ├─ system/    DndController · ListenerGuard · SetupHelper (HyperOS-Sprünge)
   └─ ui/        MainActivity · AppRoot · Apps/Focus/Bundle/Setup-Screens · Theme
```
