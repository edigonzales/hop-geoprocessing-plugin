# Geometry Converter – Abnahmematrix

Bezugsstand: Stage E, Apache Hop 2.19.0, Geometry Type Stage B.

## Funktionale Verträge

| ID | Nachweis | Status |
|---|---|---|
| F01 | `ValueMetaGeometry`-Legacy-Tests, `LegacyGeometryContractTest` | grün |
| F02/F03 | Select-Values-E2E `select-values-binary.hpl`, ValueMeta-Conversion-Tests | grün |
| F04–F08 | Codec-, Layout-, SRID- und Runtime-Tests (Core/Geometry) | grün |
| F09/F10 | Converter-Runtime, Fehlerpfade, Datenfehler-Codes | grün |
| F11/F12 | Dialogmodell-Tests, atomare Übernahme, inaktive Werte | grün (Modell) |
| F13 | `variable-fields.hpl`, `ConverterFieldNameResolverTest`, OR15 | grün |

## T-Tests

| ID | Nachweis | Status |
|---|---|---|
| T01 | Legacy-Conversion- und Geometry-Codec-Tests | grün |
| T02/T03 | `select-values-binary.hpl` in installiertem Hop | grün |
| T04 | `encoded-output.hpl`, Hex-Codec-Tests | grün |
| T05/T06 | `point-fields.hpl`, `point-layouts.hpl` | grün |
| T07 | `serialization.hpl` mit echtem SortRows-Spill | grün |
| T08 | SRID-Fälle in E2E/Core-Tests | grün |
| T09 | `GeometryConverterMetaTest`, `getFields`-Test | grün |
| T10 | `error-null.hpl`, `error-hop.hpl` | grün |
| T11 | native Consumer-Pipeline, ZIP-/Negativtest | grün |
| T12 | `scripts/benchmark-converter.py` | Smoke dokumentiert |

## OR-Tests

| ID | Nachweis | Status |
|---|---|---|
| OR01–OR10 | Geometry-Codec-, Kurven-, Layout-, SRID- und Non-Mutation-Tests | grün |
| OR11 | `coordinate-output.hpl` (7 Zeilen, keine Verluste) | grün |
| OR12 | `coordinate-errors.hpl` (NULL/partiell/NaN); Infinity im Core-Java-Test | grün, Infinity nicht CSV-installed |
| OR13 | `coordinate-output.hpl` Point-EMPTY; Core-Tests | grün |
| OR14 | `error-hop.hpl`, 3 normal / 2 Fehler, Standardfehlerfelder | grün |
| OR15 | `variable-fields.hpl`, Meta-/Resolver-Tests | grün |
| OR16 | `overwrite.hpl`, Schema-/Unit-Tests für X/Y-Tausch | grün |
| OR17 | `GeometryConverterMetaInjectionTest` (echte Hop-Injection), Defaults, XML | grün |
| OR18 | `GeometryConverterDialogTest` (Modell, gleiche Formate, Sichtbarkeit) | grün (Modell) |
| OR19 | `GeometryConverterDialogTest` (atomar, inaktive SRID, deferred variables) | grün (Modell); SWT-Sichtprüfung offen |
| OR20 | native `GeometryOperation`-Consumer in `point-fields`/`curve-roundtrip`; native Core-Tests | grün |
| OR21 | ZIP-Inhaltsprüfung, `verify-package.py`, synthetischer Legacy-Negativtest | grün |
| OR22 | exakte IDs/Werte/Zeilenbilanzen in `error-hop`, `ost-regressions`, `serialization` | grün |

## Legacy-Grenzen

- `ValueMetaGeometry`/Select Values: `POINT EMPTY` → NULL (bestehender Vertrag).
- Neuer `GeometryCodec` und Converter: `POINT EMPTY` bleibt eine leere Geometrie.
- Koordinatenausgabe: Nur Points; Nicht-Points/EMPTY folgen dem Fehlermodus.
- WKT/ISO-WKB/COORDINATES geben keine SRID aus; EWKT/EWKB tun es.

## Offene Nachweise

- OR19 sichtbarer SWT-Dialog (HiDPI, grössere Schrift) auf einer Display-/Xvfb-Plattform.
- OR12 `Infinity` als installierter CSV-Fall (Hop-CSV-Parser akzeptiert ihn nicht).
- Calculator-Regression: 30 Tests und installierte E2E grün (lokale `ImageN`-Classpath-
  Ergänzung dokumentiert).
