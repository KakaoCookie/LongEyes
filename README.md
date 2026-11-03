# Long Drive Eyes

Fabric-Mod für **Minecraft Java 26.1.2**: Halte **C** gedrückt, um zu zoomen – und deine Augen
fahren auf langen Stielen aus dem Kopf, wie in *The Long Drive*. Je weiter du mit dem Mausrad
hineinzoomst, desto weiter fahren sie heraus. Andere Spieler sehen das live.

| Komponente | Version |
|---|---|
| Minecraft | 26.1.2 (unobfuskiert, offizielle Mojang-Namen) |
| Fabric Loader | ≥ 0.18.4 (getestet mit 0.20.1) |
| Fabric API | 0.155.3+26.1.2 |
| Fabric Loom | 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`) |
| Gradle | 9.8.2 (Wrapper) |
| Java | 25 |

---

## Installation

### Client
1. Fabric Loader für 26.1.2 installieren (https://fabricmc.net/use/).
2. `fabric-api-0.155.3+26.1.2.jar` (oder neuer für 26.1.2) in `.minecraft/mods/` legen.
3. `long-drive-eyes-1.0.0.jar` in `.minecraft/mods/` legen.

### Server (für Multiplayer-Sichtbarkeit)
1. Fabric-Server 26.1.2 installieren.
2. Fabric API und `long-drive-eyes-1.0.0.jar` in den `mods/`-Ordner des Servers legen.
3. Alle Spieler, die die Augen sehen bzw. zeigen sollen, brauchen die Mod ebenfalls auf dem Client.

Die Mod läuft auf einem dedizierten Server, ohne Client- oder Rendering-Klassen zu laden
(`splitEnvironmentSourceSets`, Client-Code liegt ausschließlich in `src/client`).
Auf Servern ohne die Mod funktioniert der Zoom lokal weiter; es werden dann einfach keine Pakete gesendet.

---

## Steuerung

| Eingabe | Wirkung |
|---|---|
| **C** halten | Zoom an (Start 2×), Augen fahren leicht aus |
| **C + Mausrad hoch** | stärker zoomen (bis 20×), Augen fahren weiter aus |
| **C + Mausrad runter** | herauszoomen (bis 1×), Augen fahren ein |
| **C** loslassen | Zoom aus, Augen ziehen sich vollständig zurück |

* Während des Zoomens wechselt das Mausrad **keine** Hotbar-Slots; danach ist es wieder normal.
* In Menüs/Screens ist der Zoom deaktiviert, Scrollen in Menüs bleibt unverändert.
* Die Taste ist unter *Optionen → Steuerung → Tastenbelegung → Long Drive Eyes* umbelegbar.
* Hinweis: Vanilla belegt **C** zusätzlich mit „Schnellleiste speichern“ (nur im Kreativmodus relevant,
  zusammen mit einer Zifferntaste). Bei Bedarf eine der beiden Tasten umbelegen.

### Animation

| Zoom | Augenausfahrung (normiert) |
|---|---|
| 1× / kein Zoom | 0,00 – vollständig eingefahren |
| 2× | 0,23 – leicht ausgefahren |
| 5× | 0,54 – deutlich ausgefahren |
| 10× | 0,77 – lange Stiele |
| 15× | 0,90 – sehr weit |
| 20× | 1,00 – maximal (Standard 1,5 Blöcke) |

Die Ausfahrung wird stufenlos logarithmisch aus dem Zoomfaktor berechnet
(`ln(zoom) / ln(maxZoom)`) und von einer gedämpften Feder animiert: Beim Ausfahren gibt es einen
kleinen Cartoon-Überschwinger (~5 %), beim Einfahren gleiten die Augen weich zurück. Lange Stiele
hängen leicht durch, wackeln minimal und peitschen bei schnellen Bewegungen nach.

---

## Konfiguration

Datei: `config/longdriveeyes.json` (wird beim ersten Start mit Standardwerten angelegt; ungültige
Werte werden beim Laden in gültige Bereiche geklemmt, defektes JSON fällt auf Standardwerte zurück).

| Schlüssel | Standard | Bereich | Bedeutung |
|---|---|---|---|
| `maxZoom` | 20.0 | 1–100 | Maximaler Zoomfaktor |
| `defaultZoom` | 2.0 | 1–maxZoom | Zoom beim Drücken von C |
| `zoomStep` | 1.25 | 1.01–4 | Multiplikator pro Mausradschritt (1.25 = +25 %; 2× → 20× in ca. 10 Schritten) |
| `smoothZoom` | true | – | Weiche Kameraübergänge (false = FOV springt sofort) |
| `reduceSensitivity` | true | – | Maus-Empfindlichkeit proportional zum Zoom verringern |
| `maxEyeExtension` | 1.5 | 0.1–8 | Maximale Augenstiel-Länge in Blöcken |
| `eyeExtensionSpeed` | 0.18 | 0.02–1 | Federhärte beim Ausfahren (höher = schneller) |
| `eyeRetractionSpeed` | 0.25 | 0.02–1 | Federhärte beim Einfahren (höher = schneller) |
| `eyeSize` | 0.22 | 0.05–1 | Kantenlänge eines Augapfels in Blöcken |
| `eyeStalkThickness` | 0.06 | 0.01–0.5 | Dicke der Augenstiele in Blöcken |
| `eyeWobble` | true | – | Cartoon-Wackeln/Durchhängen langer Stiele |
| `syncMultiplayer` | true | – | Client: eigenen Zustand senden. Server: Zustände weiterleiten |

Geometrie-/Geschwindigkeitswerte gelten für das, was der jeweilige Client rendert. Übertragen wird
nur die normierte Ausfahrung (0–1), daher funktioniert die Synchronisierung auch bei
unterschiedlichen Configs.

---

## Architektur

```
src/main/java/de/longdriveeyes/          (Client + Server, keine Client-Klassen)
  LongDriveEyes.java                     Common-Entrypoint
  EyeMath.java                           Zoom → Ausfahrung, Netzwerk-Quantisierung
  config/EyesConfig.java                 JSON-Config (Gson), Validierung
  network/EyeStateC2SPayload.java        Client → Server: Zielausfahrung (0..255)
  network/EyeStateS2CPayload.java        Server → Client: (Entity-ID, Zielausfahrung)
  network/EyeNetworking.java             Payload-Registrierung
  server/EyeSyncServer.java              Validierung, Rate-Limit, Weiterleitung

src/client/java/de/longdriveeyes/client/ (nur Client)
  LongDriveEyesClient.java               Client-Entrypoint, Layer-Registrierung, Tick
  zoom/ZoomController.java               Tastenbelegung, Zoom-Simulation (log-Raum, 20 Hz)
  anim/EyeAnimation.java                 Gedämpfte Feder pro Spieler
  anim/EyeAnimationManager.java          Animationen nach Entity-ID (leer, wenn niemand zoomt)
  network/EyeSyncClient.java             Senden bei Änderung, Empfang, Reset bei Disconnect/Levelwechsel
  render/EyeRenderData.java              RenderState-Daten (RenderStateDataKey)
  render/EyeStalkLayer.java              RenderLayer am Kopf-ModelPart des Spielermodells
  render/EyeGeometry.java                3D-Boxen: Stiel mit Teleskopringen, abgerundeter Augapfel, Pupille, Glanzpunkt
  mixin/AvatarRendererMixin.java         extractRenderState → schreibt EyeRenderData
  mixin/CameraMixin.java                 Camera.calculateFov → FOV / Zoomfaktor
  mixin/MouseHandlerMixin.java           onScroll (Zoom statt Hotbar), turnPlayer (Empfindlichkeit)
```

### Rendering (RenderState-Pipeline von 26.1.2)
* **Extract-Phase:** `AvatarRendererMixin` hängt sich an `AvatarRenderer.extractRenderState(...)` und
  schreibt die interpolierte Ausfahrung via Fabric `RenderStateDataKey` in den `AvatarRenderState`.
* **Submit-Phase:** `EyeStalkLayer` (registriert über `LivingEntityRenderLayerRegistrationCallback`
  für normale und schlanke Spielermodelle) liest nur den RenderState, transformiert auf den
  Kopf-`ModelPart` (`getHead().translateAndRotate`) und übergibt die Geometrie mit
  `SubmitNodeCollector.submitCustomGeometry(...)` und `RenderTypes.entitySolid(...)`.
* Dadurch drehen sich die Augen mit Kopf-Yaw/-Pitch und jeder Körperpose (Gehen, Rennen, Schleichen,
  Schwimmen, Springen, Elytra …). Skin, Spielermodell, Hitbox und Bewegung bleiben unverändert;
  es werden keine Entities erzeugt.
* Ego-Perspektive: Für die Kamera-Entity in der Ego-Sicht werden keine Augen erzeugt (auch nicht,
  wenn eine andere Mod den Körper in der Ego-Sicht zeigt). Third-Person hinten und vorne: sichtbar.
* Unsichtbare Spieler bekommen keine Augen.

### Netzwerk
* Gesendet wird nur die **Ziel**-Ausfahrung (1 VarInt), und nur, wenn sich der quantisierte Wert ändert
  (C drücken, C loslassen, je Mausradschritt). Eine typische Zoom-Session erzeugt ~10–15 Pakete.
* Jeder empfangende Client simuliert dieselbe Feder → flüssige Animation auch bei Latenz.
* Server: Werte außerhalb 0..255 werden verworfen; max. 40 Pakete/Sekunde/Spieler (Flood-Schutz);
  Weiterleitung höchstens 1× pro Tick und nur an Spieler, die den Sender tracken (`PlayerLookup.tracking`).
* Neue Beobachter (Betreten der Sichtweite, Join, Respawn, Dimensionswechsel) erhalten den aktuellen
  Zustand einmalig über `EntityTrackingEvents.START_TRACKING`.
* Disconnect: Server löscht den Zustand; Client leert alle Animationen und setzt den Zoom zurück.
* Dimensionswechsel: Server vergisst den Zustand, der Client erkennt das neue Level, verwirft
  veraltete Entity-IDs und sendet seinen Zustand erneut, falls er noch zoomt.

---

## Kompatibilität

* **Sodium:** Die Geometrie läuft über die öffentliche `VertexConsumer`-API und einen Vanilla-Entity-
  RenderType – genau die Pfade, die Sodium beschleunigt. Kein Eingriff in Chunk-Rendering.
* **Iris / Shaderpacks:** Iris klinkt sich selbst in `submitCustomGeometry` ein, um den
  Entity-Render-Kontext zu setzen; die Augen werden daher mit dem Entity-Shader gezeichnet und
  werfen im Shadow-Pass Schatten wie normale Spielerteile.
* **Andere Zoom-Mods (Zoomify, Ok Zoomer …):** verwenden oft ebenfalls C und `Camera.calculateFov`.
  Beide funktionieren nebeneinander, aber die Zoomfaktoren multiplizieren sich und das Mausrad wird
  doppelt belegt → Taste einer der Mods umbelegen.
* **Modelle ersetzende Mods (z. B. Figura, EMF):** Die Augen hängen am Vanilla-Kopf-`ModelPart`.
  Wird der Kopf von einer Mod komplett ersetzt/versteckt, sitzen die Augen dort, wo der Vanilla-Kopf wäre.
* Es werden keine Vanilla-Optionen verändert (FOV und Empfindlichkeit werden nur zur Laufzeit modifiziert).

---

## Bauen

```bash
./gradlew build
```

Ergebnis: `build/libs/long-drive-eyes-1.0.0.jar` (+ `-sources.jar`). Benötigt JDK 25 und Zugriff auf
`maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com` und `libraries.minecraft.net`.

Alternativ das Projekt in ein GitHub-Repository pushen: Der enthaltene Workflow
`.github/workflows/build.yml` baut das Jar automatisch und stellt es als Artefakt
`long-drive-eyes-jar` bereit.

### Lokal testen
```bash
./gradlew runServer        # Testserver (eula.txt in run/ auf true setzen, online-mode=false in server.properties)
./gradlew runClient        # Client 1 → Multiplayer → localhost
./gradlew runClient2       # Client 2 („EyeTester2“) → localhost
```
Testablauf: Client 1 stellt sich vor Client 2, hält C und scrollt hoch → Client 2 sieht die Augen
wachsen; Mausrad runter → einfahren; C loslassen → vollständig einfahren.

---

## Automatisierte Tests (GitHub Actions)

Bei jedem Push laufen drei Jobs (`.github/workflows/build.yml`):

| Job | Inhalt |
|---|---|
| `build` | Kompiliert Main-, Client- und Gametest-Sourcesets, baut das Jar (Artefakt `long-drive-eyes-jar`) |
| `dedicated_server_test` | Startet einen echten dedizierten Fabric-Server mit der Mod bis „Done“ und prüft, dass keine Fehler oder Client-Klassen auftreten |
| `client_gametest` | Echter Client + echter dedizierter Server (Fabric Client GameTest API), gesteuert über simulierte Tastatur- und Mauseingaben, 33 Checks + Screenshots |

Geprüft wird unter anderem: alle Mixins greifen; C = Standardtaste; 2× beim Drücken; Mausrad
(5 Schritte → 6,10×, Begrenzung auf 20×, herauszoomen); Hotbar bleibt beim Zoomen unverändert und
funktioniert danach wieder; Inventar öffnen deaktiviert den Zoom; Loslassen stellt das FOV wieder her;
Client → Server-Synchronisation (Serverzustand 59 / 154 / 255 / 0); Server → Client-Synchronisation
auf einen anderen Avatar (ein Mannequin ersetzt den zweiten Spieler) bei 2×/5×/10×/20× inklusive
Pixelvergleich; ungültige Netzwerkwerte werden verworfen; Disconnect setzt alles zurück.
Die Screenshots werden auf den Branch `test-screenshots` gepusht.
