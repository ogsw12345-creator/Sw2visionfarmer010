# SF2 Vision Farmer

Lokaler Android-Bot für **Shadow Fight 2 Special Edition**. Er verändert keine Save-Dateien und benötigt kein Root, keinen API-Key und kein Internet. Die App nimmt den Bildschirm über Android MediaProjection auf, erkennt Kampfsituationen lokal und sendet Touch-Gesten über einen AccessibilityService.

## Auf deinen Clips kalibriert
- Landscape 2340 × 1080
- Joystick links unten
- Schlag rechts
- Tritt rechts unten
- erkennt rote Lebensbalken als Kampfstatus
- verfolgt die zwei dunklen Kämpfer-Silhouetten
- unterscheidet große/mittlere/enge Distanz
- Dash, Vorwärtsschlag, kurze Combos, Sweep/Low Kick und Neutral/Auto-Block
- erkennt grob Kämpfer am Boden
- optionaler automatischer Runden-/Survival-Fortschritt

## Installation der fertigen APK
Die GitHub-Action `.github/workflows/build-apk.yml` baut automatisch eine installierbare Debug-APK.

1. Projekt in ein GitHub-Repository hochladen.
2. Tab **Actions** → **Build Android APK** → **Run workflow**.
3. Nach Abschluss das Artifact `SF2VisionFarmer-debug-apk` herunterladen.
4. `app-debug.apk` auf dem Samsung installieren.

Die Debug-APK ist von Android/Gradle signiert und kann direkt installiert werden. Da sie nicht aus dem Play Store stammt, muss Android einmalig die Installation aus der jeweiligen Quelle erlauben.

## Verwendung
1. App öffnen.
2. `Bedienungshilfe aktivieren` → SF2 Vision Farmer erlauben.
3. Profil wählen. Für den ersten Test: **Ausgewogen**.
4. `Bot starten + SF2 öffnen`.
5. Bildschirmaufnahme bestätigen.
6. Bot läuft im Hintergrund und steuert SF2.
7. Stoppen über die permanente Benachrichtigung.

## Profile
- **Ausgewogen:** Standardkalibrierung.
- **Sicher:** etwas mehr Abstand und Block-/Konterverhalten.
- **Aggressiv:** schließt Distanz schneller und greift häufiger an.

## Datenschutz / Netzwerk
Die App enthält keine `INTERNET`-Berechtigung. Frames werden nur lokal im RAM verarbeitet und nicht gespeichert oder übertragen.

## Hinweis
Die Vision-Engine ist heuristisch und auf die hochgeladenen Aufnahmen kalibriert. SF2 ist animationsgebunden; der Bot kann Kämpfe verlieren. Der erste reale Test auf dem Zielgerät ist deshalb weiterhin wichtig, um Schwellenwerte/Timing bei Bedarf zu optimieren.
