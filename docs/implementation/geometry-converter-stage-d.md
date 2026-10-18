# Implementierungsprotokoll – Geometry Converter, Etappe 3 (Paket D)

**Status:** Transform, Meta, Runtime-Anbindung, Dialog und ZIP-Registrierung umgesetzt;
Installierte-Hop-E2E-/OR14-/OR22-Nachweise bleiben offen.  
**Auftrag:** `hop-geometry-converter-spec-v2`, Revision 2, Kapitel 03/04, Paket D und
die dazugehörige minimale ZIP-Erweiterung.  
**Basis:** Paket C (Core-Schema/Runtime) und Geometry Stage B
(JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a`).

## 1. Neue und geänderte Dateien

### Neues Modul `hop-transform-geometry-converter`

- `GeometryConverterMeta`
- `GeometryConverterData`
- `GeometryConverter`
- `GeometryConverterDialog`
- `GeometryConverterDialogModel` (nicht-SWT, headless testbar)
- Icon und Messages `en_US`/`de_DE`
- Tests:
  - `GeometryConverterMetaTest` (10)
  - `GeometryConverterMetaInjectionTest` (1)
  - `GeometryConverterPluginContractTest` (2)
  - `GeometryConverterDialogTest` (6)
  - `GeometryConverterTransformTest` (4)

### Geändert

- Root-POM: Modul ergänzt.
- `GeoprocessingClassLoaderBootstrap`: `GeometryCodec`-Capability-Check über den
  Geometry-Plugin-Classloader; Meldung verweist auf gemeinsames Geometry-Type-Update.
- Assembly-POM/-XML: sechstes Transform-JAR, Ausschluss aus `lib`.
- `scripts/verify-package.py`: sechstes JAR, Klasse und Icon.
- README, Docs-Index, Reference Matrix, Familienseite.
- Keine Änderung an Versionen, CI-Pins, Event-Gates, `dependencies.xml` oder
  Geometry-Type-ZIP.

## 2. Metadaten und Schema

`GeometryConverterMeta` verwendet die persistierten Schlüssel aus Kapitel 03 §2. Alle
aktiven Feldnamen und `srid_value` werden über `ConverterFieldNameResolver` mit demselben
`IVariables`-Kontext aufgelöst; Rohwerte bleiben im Meta. Inaktive Z/M-/Einzelfeld- und
SRID-Werte werden weder geparst noch als Pflichtfelder validiert.

`configurationVersion` ist ein primitiver `int` mit Default 1. Das ist bewusst so gewählt:
Hop-`BeanInjectionInfo` behandelt `Integer` als komplexen Typ und nimmt ihn nicht in die
Metadata-Injection auf; mit `int` wird `configuration_version` tatsächlich injizierbar.
Fehlende XML-Version bleibt 1, andere Werte werden vom Validator abgelehnt.

`getFields` kompiliert denselben `GeometryConverterSchema`-Plan wie die Runtime und ersetzt
erst danach die Ausgabe-RowMeta. `check` prüft Settings, Upstream-Schema und den aktivierten
Fehlerhop; es liest keine Datenzeilen.

## 3. Transform-Lifecycle

- `init` löst `toSettings` auf und verlangt in **allen** Fehlermodi einen aktivierten
  Fehlerhop mit Ziel. Fehlende Runtime/fehlende Konfiguration sind fatal.
- Erste Datenzeile kompiliert den Schemaplan; danach wird pro Zeile nur noch der Plan
  verwendet.
- `FAIL`, `RETURN_NULL` und `ROUTE_TO_ERROR` sind implementiert.
- `putRow` für erfolgreiche Zeilen liegt ausserhalb des Datenfehler-Catchblocks.
- `RETURN_NULL` verwendet `runtime.nullOutputs`; `ROUTE_TO_ERROR` verwendet `putError` mit
  unveränderter Input-RowMeta und Originalzeile.
- Summary zählt `input`, `normal`, `errors`, `nullOutputs`, `sridOmitted` und Fehler pro
  `GeometryCodecErrorCode`; sie wird höchstens einmal geschrieben.
- Keine Zeilen-/Geometrieretention, kein Threadpool, keine Reflection pro Zeile.

Ein Transform-Test verwendet echte `BlockingRowSet`s, eine gestartete
`LocalPipelineEngine` und ruft tatsächlich `processRow` auf. Getestet sind FAIL,
RETURN_NULL, leerer Stream und fehlender Fehlerhop. Der echte `putError`-Zweig sowie
OR14/OR22 benötigen die installierte Hop-Distribution und sind offen.

## 4. Dialog

Der SWT-Dialog folgt dem bestehenden Muster (`BaseTransformDialog`, `PropsUi`,
`BaseDialog.defaultShellHandling`). Feldkandidaten werden aus der vorherigen Transform-RowMeta
nach Repräsentation gefiltert; Fehler bei der Upstream-Inspektion werden als Statusmeldung
behandelt, nicht als NPE.

Der Dialog arbeitet auf einer Kopie (`GeometryConverterDialogModel`). OK validiert die Kopie
vollständig und übernimmt sie erst danach atomar ins Meta; Cancel lässt das Meta unverändert.
Syntaktisch gültige `${...}`-Pflichtfelder dürfen mit Designzeithinweis gespeichert werden;
eine leer aufgelöste Pflichtvariable ist ein Fehler. Inaktive SRID-Texte werden nicht geparst.

Die Modelltests decken Formatumschaltung, Sichtbarkeit, Defaults, unveränderte Kopie,
SRID-/Variablenfehler und X/Y-Tausch ab. Eine echte SWT-Display-Sichtprüfung wurde auf der
lokalen macOS-Umgebung nicht automatisiert; sie ist als manueller Nachweis offen.

## 5. ZIP und Classloader

Das bestehende Geoprocessing-ZIP enthält jetzt sechs Transform-JARs und weiterhin genau
`hop-geoprocessing-core.jar` im `lib`-Ordner. `hop-geometry-type`/JTS werden nicht dupliziert.
Der Bootstrap prüft nach dem Laden der Geometry-Plugin-Gruppe, dass
`com.atolcd.hop.gis.geometry.codec.GeometryCodec` vorhanden ist; eine alte Runtime führt zu
einer verständlichen Upgrademeldung, nicht zu einem Text-/Reflection-Fallback.

## 6. Testergebnisse

### JDK 21, vollständiger Reactor

```bash
mvn -s /tmp/gp-settings.xml -Dmaven.repo.local=/tmp/gp-m2 -U -B -ntp clean verify
python3 scripts/verify-package.py
```

**155 Tests, 0 Failures, 0 Errors, 0 Skipped** über alle Module.  
Converter-Modul: **23 Tests**.  
ZIP-SHA-256: `130ff450cfb7115e902277815a7a4e8509ee6cb6d4ea38423174e5f5cc6afd87`.

### JDK 25

```bash
mvn -s /tmp/gp-settings.xml -Dmaven.repo.local=/tmp/gp-m2 -U -B -ntp test
```

**155 Tests, 0 Failures, 0 Errors, 0 Skipped.**

### Package-Verifikation

`verify-package.py` bestätigt sechs Transform-JARs, die Converter-Klasse/Icon, die Core-JAR und
das Fehlen von `hop-geometry-type.jar`, `jts-core.jar`, `geolatte-geom.jar` und
`slf4j-api.jar` im Plugin-`lib`.

## 7. Bewusste Abweichungen und Grenzen

- `GeometryConverterDialogModel` ist eine zusätzliche interne Klasse, damit Dialoglogik
  headless getestet werden kann. Sie ist kein öffentlicher Transform-Vertrag.
- `ConfigurationVersion` ist `int` statt `Integer`, damit Hop-Metadata-Injection den Schlüssel
  tatsächlich erkennt; das persistierte Format bleibt unverändert.
- Hop 2.19 wirft beim Laden eines unbekannten Enum-Werts eine
  `IllegalArgumentException` aus dem XML-Deserialisierer, nicht `HopXmlException`. Der Fehler
  ist damit weiterhin ein klarer Ladefehler; der Test dokumentiert diese reale API.
- Der Fehlerhop wird in allen Modi verlangt, wie Kapitel 03 §6 es fordert. Das ist strenger
  als bei manchen bestehenden Hop-Transforms.
- Keine Installed-Hop-E2E-Suite, kein OR14/OR22-Nachweis, keine echte SWT-Sichtprüfung in
  dieser Etappe.
- Keine Veröffentlichung, kein Push, kein Deployment.

## 8. Offene Etappe (Paket E/F)

- zehn Converter-E2E-Pipelines inklusive Select Values/Binary, Kurven, Overwrite,
  Error-Hop, EMPTY/NULL, SRID, Variablen und Serialisierung;
- installierte Hop-Distribution mit beiden ZIPs, Classloaderidentität und echte
  `processRow`-/Fehlerzeilenbilanz (OR14, OR22);
- OR17-Metadata-Injection über eine echte Pipeline, OR19-SWT-Nachweis auf einer
  Display-/Xvfb-Plattform;
- Familien-/Recipe-Dokumentation und Handover-Nachtrag mit E2E-Hashes.

## 9. Übergabe

`handover/stage-d` enthält Transform-JAR, Core-JAR, ZIP, Modul-/Parent-POM,
`SHA256SUMS`, `manifest.json` und `verify.sh`.
