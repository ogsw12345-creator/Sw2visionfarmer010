# SF2 Vision Farmer – Kettensense 1.1

Android-App für Shadow Fight 2 Special Edition (`com.nekki.shadowfight2.paid`). Ziel ist ein unbeaufsichtigter Survival-Ablauf mit hoher Goldausbeute. Diese Version ist eine experimentelle Grundlage und noch kein nachgewiesen zuverlässiger Nachtfarmer.

## Was sich geändert hat

- Kettensense: einen Distanzbereich halten, bei Nähe zurückweichen, bei zu großer Entfernung kurze Schritte statt Dash zum Gegner. Am Rand angreifen statt endlos zurücklaufen.
- Bei fehlender/mehrdeutiger Kämpfererkennung keine tiefen Tritte mehr. Vor Eingaben müssen drei aufeinanderfolgende Erkennungen vorliegen.
- Größerer Suchbereich; Bewegung hilft beim erstmaligen Finden der Silhouetten. Tracking wird zwischen Kämpfen zurückgesetzt. Anfangs wird Shadow links angenommen.
- Gesten nur bei sichtbarem SF2; laufende Gesten werden nicht durch neue Aktionen unterbrochen. Keine verzögerten blinden Menüklicks nach STOP.
- Lokale Texterkennung für deutsche/englische Survival-, Kampf- und Ergebnis-Schaltflächen. Die tatsächlich erkannte Textposition wird angetippt. Ein Kampfbefehl benötigt eine zuvor erkannte Survival-Auswahl.
- Menüaktionen müssen in zwei aufeinanderfolgenden OCR-Ergebnissen übereinstimmen. Veraltete Ergebnisse werden verworfen. Unbekannte Menüs bleiben unangetastet, festhängende Schaltflächen erhalten höchstens zwei Wiederholungen.
- Nach 15 Sekunden ohne neue Bildschirmbilder stoppt die Aufnahme. Ein erneuter Start benötigt wieder Androids Aufnahmefreigabe.

## Lernfunktion und Grenzen

Das Profil **Distanz lernen** vergleicht drei Distanzbereiche. Explizit erkannte Siege/Niederlagen und die Zeit bis zum Ergebnis liefern eine einfache Bewertung. Versuche, Durchschnittswerte und Ergebniszähler bleiben lokal in den App-Daten erhalten.

Das ist ein kleiner lernender Auswahlalgorithmus, kein aus YouTube trainiertes neuronales Modell. Nur eindeutig erkannte Ergebnisse werden berücksichtigt. Runden ohne erkennbaren Ergebnistext liefern keine Lernbewertung. Die Zeitbewertung ist ein Ersatzmaß; **Gold/h wird noch nicht automatisch gemessen oder optimiert**. Die beiden festen Profile verändern die Lerndaten nicht.

Die Distanzgrenzen, Tastenpositionen und Angriffstakte benötigen einen Test mit der ausgerüsteten Kettensense. Bildschirmpositionen basieren auf dem bisherigen Layout 2340 × 1080 und werden proportional skaliert. Andere Seitenverhältnisse, App-Teilaufnahme oder verschobene Tasten können falsche Eingaben verursachen. Bei Seitenwechseln/Überlagerungen und anderen Arenen kann die heuristische Erkennung ausfallen. Es gibt noch keine verifizierte Erkennung für jedes Belohnungsfenster, Energieproblem oder Spielabsturz. Ein unbekanntes Menü benötigt einen Screenshot zur Anpassung.

## Benutzung

1. Debug-APK installieren, Bedienungshilfe aktivieren, Kettensense im Spiel ausrüsten.
2. Profil wählen. **Distanz lernen** probiert die drei Reichweiten aus; **großer Abstand** bleibt bei einer festen Variante.
3. Bot starten und bei Androids Bildschirmfreigabe **gesamten Bildschirm** auswählen. Spiel im Querformat halten.
4. Survival öffnen, falls die Startkarte noch nicht erkannt wird. Die App bedient nur explizit erkannte Menüs.
5. Zuerst mehrere Kämpfe, eine Niederlage und einen vollständigen Neustart beobachten. Dann einen längeren Probelauf durchführen. Die ganze Nacht ist bisher nicht auf einem Gerät geprüft.
6. STOP in der Benachrichtigung oder in der App beendet den Lauf.

Nach einer Aktualisierung kann Android wegen unterschiedlicher Debug-Signaturen eine Neuinstallation verlangen. Deinstallation löscht die lokalen Lerndaten.

## Build und Prüfung

GitHub Actions baut bei Pull Requests sowie auf main/master und manuell eine Debug-APK. Der Build führt zuerst `bash tests/run.sh` aus. Diese Regressionstests prüfen Abstandhalten in beiden Richtungen, Verhalten am Rand/am Boden sowie Menüfreigaben und Mehrdeutigkeiten. Sie ersetzen keinen Spieltest.

Das Artefakt heißt `SF2VisionFarmer-debug-apk`. Die gebündelte ML-Kit-Texterkennung vergrößert die APK; ihr Modell wird mitgeliefert. Verarbeitete Bildschirmkopien werden nach OCR freigegeben. Es werden keine Videos oder Screenshots archiviert.

Die Datei `SF2VisionFarmer_AutoX.js` ist eine ältere, nicht aktualisierte Alternative. Die Änderungen gelten für die Android-App.
