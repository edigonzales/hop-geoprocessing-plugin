# Implementierungsprotokoll – Geometry Converter, Etappe 4 (Paket E/F)

**Status:** Installierte Converter-E2E, Negativtest, PostGIS-/Calculator-Regression und
Performance-Smoke ausgeführt.  
**Basis:** Stage-E-ZIP `7c696337bcdc96ab14192e96e1c88c96b9e21bb8a4b7842489537a7a66e2da30`,
Geometry Stage B JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a`,
ZIP `00638751fcae5637cb9fc3db5db6ec8bd02601347394eb67ada58110332721a3`.

## 1. Installierte E2E

Isolierte Apache-Hop-2.19.0-Installation; die produktive lokale Hop-Installation wurde
nicht verändert. Der Lauf verwendet die Stage-E-ZIP und die Stage-B-Geometry-ZIP.

```bash
python3 scripts/run-e2e.py \
  --hop-home /tmp/hop-e2e \
  --plugin-zip handover/stage-e/hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip \
  --geometry-zip /Users/stefan/sources/hop-geometry-type-plugin/handover/stage-b/hop-geometry-type-plugin-0.2.0-SNAPSHOT.zip \
  --negative-hop-home /tmp/hop-e2e-legacy
```

Ergebnis:

- bestehende `geoprocessing.hpl`, `linearize-curves.hpl`, `coverage-linearize.hpl`: grün;
- 13 positive Converter-Pipelines: grün;
- negative Installation mit synthetischer Geometry-Runtime ohne `GeometryCodec`: erwarteter
  Startfehler, keine normale Ausgabe;
- **Installed Hop Geometry Converter E2E OK**;
- **Installed Hop Geoprocessing E2E OK**.

### Nachgewiesene Daten

| Nachweis | Ergebnis |
|---|---|
| `point-fields.hpl` | 4/4 Zeilen; SRID 2056; native Geometrie durch vorhandenen `GeometryOperation`-Consumer; XYZM-Layout mit NaN für konfigurierte fehlende Z/M |
| `point-layouts.hpl` | XY/XYZ/XYM/XYZM exakt, inkl. SRID und Ordinaten |
| `select-values-binary.hpl` | echte Select-Values-Konvertierung `Binary→Geometry→Binary`; EWKT exakt |
| `encoded-output.hpl` | unabhängige ISO-WKB-BE-Hex-Fixture, WKT, EWKT |
| `curve-roundtrip.hpl` | `CIRCULARSTRING Z` mit exakten Controls; vorhandener Linearize-Consumer |
| `overwrite.hpl` | Ziel `geometry` in der Mitte ersetzt; `tail` unverändert |
| `error-null.hpl` | 4 Zeilen, Fehler-/NULL-Zielwerte NULL, Folgezeile korrekt |
| `error-hop.hpl` | 3 normale / 2 Fehlerzeilen; IDs 2/4; `GC_INVALID_WKT` im Standardfehlerfeld |
| `serialization.hpl` | 5.000 Zeilen; `SortRows` meldet `Opening 100 tmp-files`; alle IDs/EWKT erhalten |
| `variable-fields.hpl` | rohe `${SRC_FIELD}`/`${DST_FIELD}`; korrekte Position/Typen |
| `ost-regressions.hpl` | gültig/ungültig/EMPTY/Kurve/Linie; Normal- und Fehler-IDs disjunkt; Werte exakt |
| `coordinate-output.hpl` | 7 Zeilen; XY/XYZ/XYM/EMPTY/Line/Polygon/Point; keine Zeilenverluste |
| `coordinate-errors.hpl` | vollständiges NULL-Tupel normal; partielles NULL/NaN als Fehler; IDs 3/4 |

`Infinity` wird von Hops CSV-Zahlenparser nicht akzeptiert; der Fall ist im
`GeometryConverterRuntimeTest` der Core-Suite abgedeckt und wird hier nicht als
installierter CSV-Nachweis behauptet.

### Negativtest

`run-e2e.py` erzeugt im temporären Verzeichnis eine **synthetische** Geometry-ZIP, deren
Plugin-JAR nur `GeometryCodec` entfernt hat. In einem zweiten frischen Hop-Home werden
Geoprocessing-ZIP und diese Legacy-ZIP installiert. `negative-smoke.hpl` endet mit einem
verständlichen `GeometryCodec`-/Upgrade-Fehler, ohne normale Ausgabe. Das ist kein
historisches Release, sondern ein gezielter OR21-Nachweis.

## 2. Regressionen

### Geometry Type/PostGIS

Kanonischer Lauf mit `postgis/postgis:17-3.5`:

```bash
export POSTGIS_TEST_ENABLED=true ...
mvn -s "$MAVEN_SETTINGS" -U -B -ntp clean verify
python3 -m unittest discover -s scripts/tests -v
bash scripts/check-distribution.sh
```

Ergebnis: **142 Tests**, 0 Failures/Errors/Skips, Distribution OK. Neu ist
`GeometryCodecPostgisCounterprobeTest`: feste `POINT ZM`-EWKB-Fixture über
`ST_GeomFromEWKB`, Prüfung von Typ, SRID 2056, vier Dimensionen und Rücklesen durch den
Codec. Es wurden keine Geometry-Produktionsklassen geändert.

### Calculator

Calculator-ZIP mit exaktem Stage-B-JAR gebaut und die vorhandene installierte E2E
ausgeführt:

```bash
python3 scripts/run-e2e.py --hop-home /tmp/hop-e2e-calc \
  --plugin-zip assemblies/.../hop-geometry-calculator-plugin-0.1.0-SNAPSHOT.zip \
  --geometry-zip .../hop-geometry-type-plugin-0.2.0-SNAPSHOT.zip
```

Ergebnis: Basispipeline und alle drei Dokumentationsbeispiele grün
(`area`, `centroid`, `validation`). Der Calculator-Unit-/Verify-Lauf ist ebenfalls grün,
wenn die lokal fehlende Hop-UI-Laufzeitklasse `org.eclipse.imagen.ImageN` explizit auf
den Surefire-Test-Classpath gelegt wird:

```bash
mvn ... -Dmaven.test.additionalClasspath=.../imagen-core-0.9.2.jar clean verify
# 30 Tests, 0 Failures/Errors
```

Das ist eine lokale Test-Setup-Ergänzung, keine Änderung am Calculator.

## 3. Performance-Smoke

`scripts/benchmark-converter.py` mit 100.000 kleinen Punkten, `-Xmx128m`:

```json
{
  "rows": 100000,
  "wall_seconds": 2.63,
  "max_child_rss_mb": 302.5,
  "heap": "-Xmx128m",
  "java": "Temurin 21.0.10",
  "os": "macOS-27.0.1-arm64",
  "pipeline": "e2e/geometry-converter/streaming-benchmark.hpl",
  "plugin_zip_sha256": "7c696337bcdc96ab14192e96e1c88c96b9e21bb8a4b7842489537a7a66e2da30",
  "geometry_zip_sha256": "00638751fcae5637cb9fc3db5db6ec8bd02601347394eb67ada58110332721a3"
}
```

Das ist ein Smoke-Profil, keine harte Zeitvorgabe. Die Zeilenzahl wird exakt geprüft
(`input=100000, normal=100000, errors=0`); der Lauf ist streaming und hält keine
Geometriesammlung. Der RSS-Wert enthält die Hop-/JVM-Grundlast und ist nicht mit dem
Heap-Limit gleichzusetzen.

## 4. CI

- `.github/workflows/ci.yml` `installed-e2e` entpackt den bereits geprüften Hop-Download
  ein zweites Mal und übergibt `--negative-hop-home`.
- `scripts/run-e2e.py` behält die bestehende CLI; der negative Schalter ist optional.
- `ci-ref`, Hop-SHA-512, Verify-/Publish-Gates und `verify.yml` bleiben unverändert.
- `python3 scripts/verify-package.py` bleibt bei sechs Transform-JARs.

## 5. Abnahme-Mapping

Siehe [geometry-converter-acceptance.md](geometry-converter-acceptance.md).

## 6. Offene manuelle Nachweise

- Echte SWT-Sichtprüfung bei HiDPI/grösserer Schrift auf einer Display-/Xvfb-Plattform.
  Die atomare Dialog-/Modelllogik ist automatisiert getestet (6 Tests), der sichtbare
  Dialog wurde in dieser Etappe nicht automatisiert geöffnet.
- `Infinity` als installierter CSV-Fall (Hop-Parser-Grenze); Core-Java-Test vorhanden.
- Keine offenen Calculator-Nachweise; der Unit-/Verify-Lauf benötigt lokal die oben
  dokumentierte `ImageN`-Classpath-Ergänzung.

## 7. Übergabe

`handover/stage-e` enthält die finalen Hashwerte, E2E-/Benchmark-Ergebnisse,
`SHA256SUMS`, `manifest.json` und `verify.sh`. Stage-D-Artefakte bleiben historisch
unverändert.
