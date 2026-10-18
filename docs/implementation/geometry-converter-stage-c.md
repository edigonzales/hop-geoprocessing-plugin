# Implementierungsprotokoll – Geometry Converter, Etappe 2 (Paket C)

**Status:** abgeschlossen am 2026-10-17  
**Auftrag:** Spezifikation `hop-geometry-converter-spec-v2`, Revision 2, Kapitel 05 Paket C / Gate C.  
**Scope:** nicht-GUI-Konfigurations-, Schema- und Runtime-Klassen im bestehenden
`hop-geoprocessing-core`. Kein Transform, kein SWT-Dialog, kein neues Modul und keine
Assembly-Änderung.

## 1. Ausgangslage

| Punkt | Wert |
|---|---|
| Repository | `edigonzales/hop-geoprocessing-plugin` |
| Branch | `main` |
| HEAD vor Änderungen | `364d138899010f7ed899489984ce06c4c20cf326` |
| Arbeitsbaum vor Änderungen | sauber |
| Java | Temurin 21.0.10+7 (CI-Kompatibilität 25) |
| Hop | 2.19.0 |
| JTS | 1.20.0 |
| Projektversion | `0.1.0-SNAPSHOT` |
| Geometry-Artefakt | Stage-B-Handover, JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a` |

Der lokale Maven-Cache enthielt einen älteren gleichnamigen Geometry-Snapshot. Für alle
Läufe wurde deshalb ein separates lokales Maven-Repository (`/tmp/gp-m2`) mit dem exakten
Stage-B-JAR/POM/Parent-POM verwendet.

## 2. Vorprüfung und gefundener Consumer-Regressionsfall

Baseline mit dem bisherigen Snapshot: **64 Core-Tests, alle grün**.

Beim ersten Lauf mit dem exakten Stage-B-Artefakt schlug
`TrueCurveSupportTest.parserPreservesAllSupportedTrueCurveTypes` fehl:
`GC_UNSUPPORTED_LAYOUT: Inconsistent coordinate layouts: XY and XYZ`. Ursache war eine
bestehende Consumer-Konstruktion: `CompoundCurve`/`CurvePolygon`-Komponenten aus
JTS-`Coordinate[]` haben eine `CoordinateArraySequence` mit deklarierter Dimension 3,
aber ausschließlich NaN-Z-Werten; der neue strikte Codec behandelte sie als XYZ, während
die Kurvenkontrollpunkte XY waren.

Die Regression wurde im Geometry-Type-Repository behoben, nicht durch Aufweichen des
Consumer-Tests:

- `GeometryLayoutSupport.lineStringLayout(...)` behandelt **legacy** line/polygon
  `CoordinateArraySequence`-Instanzen mit ausschließlich NaN-Z als XY.
- Explizite Codec-Sequenzen (Packed) behalten deklarierte XYZ/XYM/XYZM, auch wenn alle
  Werte NaN sind.
- `CurveWktWriter`/`CurveWkbWriter` verwenden für Linien/Polygone dasselbe effektive
  Layout für Header und Nutzlast.
- Neuer Geometry-Regressionstest `LegacyCurveLayoutCompatibilityTest`.
- Neues Stage-B-Artefakt: JAR `d6b44299…`, ZIP `00638751…`; Geometry-Suite 141 Tests.

Danach liefen die 64 bestehenden Geoprocessing-Core-Tests mit dem exakten Artefakt grün.

## 3. Umgesetzte Klassen (Kapitel 02/03)

Paket `ch.so.agi.hop.geoprocessing.core.converter`:

- `ConverterInputFormat`, `ConverterOutputFormat`, `ConverterSridMode`,
  `ConverterErrorMode`
- `GeometryConverterSettings` (unveränderlicher Record, bereits aufgelöste Namen)
- `ConfigurationIssue` mit Severity und stabilen Codes
- `ConverterConfigurationException` (`HopTransformException`)
- `ConverterIntegrationException` (`HopException`)
- `ConverterFieldNameResolver` (Hop-`IVariables.resolve`, keine eigene Expansion)
- `GeometryConverterValidator` (Settings + Input-Schema)
- `OutputRole`, `OutputBinding`
- `CompiledConversionPlan` (defensive RowMeta-Kopien, Indizes, Bindings, Options)
- `GeometryConverterSchema.compile(...)`
- `GeometryConverterRuntime` inkl. `readInput`, `applySridPolicy`,
  `calculateTargetValues`, `writeOutputCopy`, `nullOutputs`
- `ConversionResult` (Zeile, alle Zielwerte NULL, SRID weggelassen)

## 4. Verhalten

- Formate: `GEOMETRY`, `WKT_EWKT`, `WKB_BYTES`, `WKB_HEX`, `COORDINATES`;
  Ausgabe: `GEOMETRY`, `WKT`, `EWKT`, `WKB_BYTES`, `EWKB_BYTES`, `WKB_HEX`,
  `EWKB_HEX`, `COORDINATES`.
- Native Geometrie wird ohne String-/Reflection-Brücke gelesen; fremde Runtime-Klassen
  führen zu `ConverterIntegrationException`.
- Koordinateneingabe nutzt X/Y/Z/M-`getNumber`, `PointCoordinateSupport.create` und
  das deklarierte Layout; vollständiges NULL-Tupel → NULL, partielles NULL/NaN/Infinity
  → benannte Codec-Fehler.
- SRID: `KEEP`, `SET_IF_MISSING`, `REPLACE`, `CLEAR`; Änderungen nur auf exakten Kopien.
- Ausgabe koordiniert: Point-only; EMPTY/NULL → NULL-Ordinaten; Nicht-Point →
  `GC_NOT_A_POINT`.
- Zeilen werden über `Arrays.copyOf` kopiert; Quellzeile, Quell-RowMeta und
  Quellgeometrie bleiben unverändert.
- Ausgabemetadaten: `ValueMetaGeometry`, `ValueMetaString`, `ValueMetaBinary` oder
  `ValueMetaNumber`, Storage NORMAL, Origin gesetzt; bestehende Ziele nur bei
  Überschreibfreigabe ersetzen.
- Keine Verwendung von `GeometryFieldValueHelper`, `GeometryOperationExecutor`,
  `OperationRegistry` oder JTS-Textbrücken.
- Fehlerstrategien `FAIL`/`RETURN_NULL`/`ROUTE_TO_ERROR` werden in Paket D im
  Transform-Lifecycle umgesetzt; Paket C liefert die fachlichen Datenfehler.

## 5. Tests und Ergebnisse

### Core (JDK 21, exaktes Geometry-Artefakt)

```bash
mvn -s /tmp/gp-settings.xml -Dmaven.repo.local=/tmp/gp-m2 -U -B -ntp \
  -pl hop-geoprocessing-core -am test
```

**102 Tests, 0 Failures, 0 Errors, 0 Skipped.**

Neue Testklassen:

- `ConverterFieldNameResolverTest`
- `GeometryConverterValidatorTest`
- `GeometryConverterSchemaTest`
- `GeometryConverterRuntimeTest`
- `OstRegressionRuntimeTest` (OR11–OR16, OR20)

Behandelte Fälle: T05–T09, alle Eingabe-/Ausgabeformate, BE/LE, ISO/EWKB,
SRID-Strategien, Koordinatenlayouts, NULL/EMPTY/partielle Tupel, Overwrite/Index,
X/Y-Tausch, native Kopie ohne Stringbrücke, keine Quellmutation.

### Vollständiger Reactor (JDK 21)

```bash
mvn -s /tmp/gp-settings.xml -Dmaven.repo.local=/tmp/gp-m2 -U -B -ntp clean verify
python3 scripts/verify-package.py
```

**132 Tests, 0 Failures, 0 Errors, 0 Skipped** über alle Module; Distribution
weiterhin mit fünf Transform-JARs, `Geometry`/JTS nicht dupliziert. ZIP-SHA-256:
`81f460e2a8bd19506d62cd3ba52d7535e26459b6b3ae92a5f939ab46064c8055`.

### JDK 25

```bash
mvn -s /tmp/gp-settings.xml -Dmaven.repo.local=/tmp/gp-m2 -U -B -ntp clean test
```

**132 Tests, 0 Failures, 0 Errors, 0 Skipped.**

## 6. Abweichungen / Entscheidungen

- `GeometryConverterSettings` enthält bereits aufgelöste Feldnamen; die Rohwerte
  bleiben Aufgabe des Metas (Paket D).
- `GeometryConverterValidator` prüft Error-Hop-Präsenz nicht; das ist eine
  Transform-Lifecycle-Aufgabe (D), ebenso `FAIL`/`RETURN_NULL`/`ROUTE_TO_ERROR`.
- Legacy-JTS-Linien mit ausschließlich NaN-Z werden im Geometry-Type-Codec als XY
  behandelt, sofern sie eine `CoordinateArraySequence` sind. Explizite Codec-Sequenzen
  bleiben layouttreu. Diese gezielte Kompatibilitätsregel schützt bestehende Consumer.
- Keine neue Maven-Abhängigkeit, keine CI-Pin-Änderung, kein Assembly-Umbau.

## 7. Offene Punkte (Paket D/E)

- `GeometryConverterMeta`, Transform, SWT-Dialog.
- Fehlerhop-/`putError`-Runtime und Zeilenbilanz.
- Neues Modul im bestehenden Geoprocessing-ZIP und `verify-package.py`-Erweiterung.
- Installed-Hop-E2E mit dem Converter.
- Dialogtests/OR19; OR22 installed data proof.

## 8. Übergabe

`handover/stage-c` enthält:

- `hop-geoprocessing-core-0.1.0-SNAPSHOT.jar`
- Modul-POM und Parent-POM im Maven-Layout
- `SHA256SUMS`, `manifest.json`, `verify.sh`

Voraussetzung bleibt das Geometry-Stage-B-Artefakt
`handover/stage-b` (JAR `d6b44299…`). Die nächste Etappe (D) soll dieses Core-JAR
verwenden und die Converter-Klassen in das neue Transform-Modul einbinden.
