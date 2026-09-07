# Einfachmodus 1.2 – erste Arena

Aktuelle Vorgabe: nur Schlagen im Kampf und die beiden bestätigten Menüknöpfe.

- Wiederholter einzelner Schlag, frühestens alle 180 ms (bei der Bildrate meist etwa 210 ms).
- Keine Bewegung, Tritte, Distanzlogik, Silhouettenerkennung oder Lernfunktion im aktiven Ablauf.
- Menü: OCR erkennt `KÄMPFT!` rechts unten oder `OK` unten mittig, entsprechend den Screenshots vom 07.09.2026. Zwei stabile Beobachtungen sind nötig. Andere Menüpunkte werden nicht gedrückt.
- Überleben vor dem Start selbst auswählen. Bildschirmaufnahme für den gesamten Bildschirm freigeben, Querformat beibehalten. Automatisches KÄMPFT/OK eingeschaltet lassen.
- Kampfstatus nutzt weiterhin die roten Lebensbalken. Der Schlagknopf bleibt auf der bisherigen relativen Position x=0.866, y=0.710. Die Menü-Screenshots zeigen keinen Schlagknopf; diese Position benötigt weiterhin einen Gerätetest.
- STOP und Pausieren außerhalb des Spiels bleiben aktiv. Bei unbekannten Menüs oder ausbleibender Aufnahme erfolgt kein blindes Weitertippen.
- Die ältere Lernlogik bleibt als inaktive Klassen im Projekt; gespeicherte Lerndaten werden nicht verändert. Gold/h wird nicht gemessen.

GitHub Actions führt `bash tests/run.sh` aus und baut eine Debug-APK. Ein echter Nachtlauf ist noch nicht geprüft.

