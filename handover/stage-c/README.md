# Übergabe Etappe C – Converter-Core (Schema, Validierung, Runtime)

Dieses Verzeichnis enthält die tatsächlich gebauten und getesteten Core-Artefakte
aus `hop-geoprocessing-plugin` für Paket C (Gate C). Es enthält **keinen** neuen
Transform und keinen SWT-Dialog; diese folgen in Paket D.

## Artefakte

| Datei | SHA-256 |
|---|---|
| `hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip` | `81f460e2a8bd19506d62cd3ba52d7535e26459b6b3ae92a5f939ab46064c8055` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.jar` | `79e14a2cd74d883689a73d116481d9d0d2287244294e552a147dbca0b1822592` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.pom` | `4150c2bd836dd497ae1fb14629f96eb161643cdbfa05db9bff0f2be2227bf402` |
| `maven-repo/.../hop-geoprocessing-plugin-parent-0.1.0-SNAPSHOT.pom` | `0315be85b034e75be92ce1714cc0726e5b796a05121af6b17c6a1e52c9711ac7` |

Vollständige Liste: `SHA256SUMS`. Vor Verwendung `./verify.sh` ausführen.

## Voraussetzung: Geometry Type Stage B

Die Core-JAR wurde gegen exakt dieses Geometry-Artefakt gebaut und getestet:

- JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a`
- ZIP `00638751fcae5637cb9fc3db5db6ec8bd02601347394eb67ada58110332721a3`
- Quelle: `/Users/stefan/sources/hop-geometry-type-plugin/handover/stage-b`

Der ältere, gleichnamige Remote-Snapshot ist nicht der getestete Stand.

## Verwendung im nachfolgenden Transform-Arbeitspaket (D)

Das Verzeichnis `maven-repo` ist ein lokales Maven-Repository:

```xml
<repository>
  <id>stage-c-handover</id>
  <url>file:///ABSOLUTER/PFAD/handover/stage-c/maven-repo</url>
</repository>
```

```xml
<dependency>
  <groupId>ch.so.agi</groupId>
  <artifactId>hop-geoprocessing-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Im bestehenden Reactor kann das Modul direkt als Projektabhängigkeit verwendet werden.
Für reproduzierbare Läufe das exakte JAR aus diesem Verzeichnis bzw. dem
Stage-B-Geometry-Handover verwenden.

## Enthaltene Core-API

Paket `ch.so.agi.hop.geoprocessing.core.converter`:

- `GeometryConverterSettings`, `ConverterInputFormat`, `ConverterOutputFormat`
- `ConverterSridMode`, `ConverterErrorMode`
- `ConfigurationIssue`, `ConverterConfigurationException`,
  `ConverterIntegrationException`, `ConverterFieldNameResolver`
- `GeometryConverterValidator`, `GeometryConverterSchema`
- `CompiledConversionPlan`, `OutputBinding`, `OutputRole`, `ConversionResult`
- `GeometryConverterRuntime` (`readInput`, `applySridPolicy`,
  `calculateTargetValues`, `writeOutputCopy`, `nullOutputs`)

## Getestet

- 102 Core-Tests; 132 Tests im vollständigen Reactor (JDK 21), alle grün.
- JDK-25-Kompatibilität: 132 Tests, alle grün.
- Bestehendes Geoprocessing-ZIP weiterhin mit fünf Transform-JARs; `verify-package.py` OK.
- Distribution ändert sich in Paket C nicht.

## Noch offen (Paket D/E)

- `GeometryConverterMeta`, Transform-Lifecycle, SWT-Dialog, Fehlerhop;
- neues Modul im ZIP, `verify-package.py`-Erweiterung;
- Installed-Hop-E2E und OR19/OR22;
- Veröffentlichung/Push/Deployment (nicht beauftragt).

Details: [`../../docs/implementation/geometry-converter-stage-c.md`](../../docs/implementation/geometry-converter-stage-c.md).
