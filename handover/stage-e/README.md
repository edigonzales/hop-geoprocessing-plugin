# Übergabe Etappe 4 / Paket E/F – installierte Converter-Abnahme

Dieses Verzeichnis enthält den finalen Geoprocessing-Stand nach der installierten
Converter-E2E, dem Negativtest, der PostGIS-/Calculator-Regression und dem
Performance-Smoke.

## Artefakte

| Datei | SHA-256 |
|---|---|
| `hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip` | `7c696337bcdc96ab14192e96e1c88c96b9e21bb8a4b7842489537a7a66e2da30` |
| `maven-repo/.../hop-transform-geometry-converter-0.1.0-SNAPSHOT.jar` | `9c1d3a07b7669ae45fea25ad04989eb4d6f578330c9e343a53529de8efc5d656` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.jar` | `84dadd8d2950d3e693a35d03d9798fcc7378246619abf0fc41e0bbcbb2f3784b` |
| `maven-repo/.../hop-transform-geometry-converter-0.1.0-SNAPSHOT.pom` | `8337d4b1886760af36f7b82a2206cf0a79e30cafcf582b11f23cf1f7d2e77db0` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.pom` | `4150c2bd836dd497ae1fb14629f96eb161643cdbfa05db9bff0f2be2227bf402` |
| `maven-repo/.../hop-geoprocessing-plugin-parent-0.1.0-SNAPSHOT.pom` | `f5533fde4bcd09e167e11759e92465ee518cca6d148759626b8ee331a2425082` |

Vollständige Liste: `SHA256SUMS`. Vor Verwendung `./verify.sh` ausführen.

## Voraussetzungen

- Apache Hop 2.19.0, Java 21 (JDK 25 getestet).
- Geometry Type Stage B: JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a`,
  ZIP `00638751fcae5637cb9fc3db5db6ec8bd02601347394eb67ada58110332721a3`.
- Das ZIP enthält sechs Transform-JARs und `lib/hop-geoprocessing-core.jar`; keine zweite
  Geometry-/JTS-Laufzeit.

## Getestet

- **Geoprocessing-Reactor**: 155 Tests, 0 Failures/Errors/Skips (JDK 21); JDK 25 ebenfalls 155.
- **Installierte E2E**: drei bestehende Pipelines + 13 Converter-Pipelines grün.
- **Fehlerhop**: 3 normal / 2 Fehler, IDs 2/4, `GC_INVALID_WKT`.
- **RETURN_NULL**: 4 Zeilen, 2 NULL-Zielzeilen.
- **Serialisierung**: 5.000 Zeilen durch `SortRows` mit `sort_size=50`; Log
  `Opening 100 tmp-files`; alle IDs/EWKT erhalten.
- **Koordinatenausgabe**: 7 Zeilen, keine Zeilenverluste; Nicht-Points/EMPTY NULL.
- **Negativtest**: synthetische Geometry-Runtime ohne `GeometryCodec` wird mit
  Upgrademeldung abgelehnt; keine normale Ausgabe.
- **Geometry/PostGIS**: 142 Tests, Distribution OK.
- **Calculator**: installierte E2E inkl. drei Dokumentationsbeispiele grün; Unit-/Verify 30 Tests grün (lokale `ImageN`-Classpath-Ergänzung).
- **Benchmark**: 100.000 Punkte, 2.63 s Wandzeit, 302.5 MB Peak-RSS, `-Xmx128m`; kein
  harter Performance-Grenzwert.

Details: `e2e-results.json`, `benchmark.json`, `stage-e.md`, `acceptance.md`.

## Offene manuelle Nachweise

- Sichtbarer SWT-Dialog (HiDPI/grössere Schrift) auf einer Display-/Xvfb-Plattform.
- `Infinity` als installierter CSV-Fall (Hop-CSV-Parser akzeptiert den Wert nicht; der
  Core-Java-Test deckt ihn ab).

## Installation

1. Geometry Type Stage B als `plugins/misc/hop-geometry-type` installieren.
2. Dieses ZIP über `plugins/transforms/hop-geoprocessing` entpacken.
3. Hop neu starten; der Bootstrap prüft Classloader-Gruppe und `GeometryCodec`.

Kein Push, kein Deployment, keine produktive Hop-Installation.
