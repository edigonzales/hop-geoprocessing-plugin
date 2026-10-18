# Übergabe Etappe 3 / Paket D – Geometry Converter Transform

Dieses Verzeichnis enthält die tatsächlich gebauten und getesteten Artefakte aus
`hop-geoprocessing-plugin`: den neuen Transform, das gemeinsame Core-JAR und die
sechste Transform-JAR im bestehenden Geoprocessing-ZIP.

## Artefakte

| Datei | SHA-256 |
|---|---|
| `hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip` | `130ff450cfb7115e902277815a7a4e8509ee6cb6d4ea38423174e5f5cc6afd87` |
| `maven-repo/.../hop-transform-geometry-converter-0.1.0-SNAPSHOT.jar` | `ed743ac623d484dc54ea8afc54b2e20416ffad4db32340904ece785a9723f2f8` |
| `maven-repo/.../hop-transform-geometry-converter-0.1.0-SNAPSHOT.pom` | `8337d4b1886760af36f7b82a2206cf0a79e30cafcf582b11f23cf1f7d2e77db0` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.jar` | `1e720820175e534b2dfcc6cfab35b165ef3b43c34f8d42caad2043299d984fc6` |
| `maven-repo/.../hop-geoprocessing-core-0.1.0-SNAPSHOT.pom` | `4150c2bd836dd497ae1fb14629f96eb161643cdbfa05db9bff0f2be2227bf402` |
| `maven-repo/.../hop-geoprocessing-plugin-parent-0.1.0-SNAPSHOT.pom` | `f5533fde4bcd09e167e11759e92465ee518cca6d148759626b8ee331a2425082` |

Vollständige Liste: `SHA256SUMS`. Vor Verwendung `./verify.sh` ausführen.

## Voraussetzungen

Die ZIP wurde gegen exakt dieses Geometry-Type-Artefakt gebaut und getestet:

- JAR `d6b44299e0dc307211a94b40aed99d821b037d610014c3841e7d33f1fa73853a`
- ZIP `00638751fcae5637cb9fc3db5db6ec8bd02601347394eb67ada58110332721a3`
- Quelle: `/Users/stefan/sources/hop-geometry-type-plugin/handover/stage-b`

Der Transformationsteil erwartet die gemeinsame Geometry-Type-Laufzeit mit
`GeometryCodec`. Eine ältere Runtime wird über den Bootstrap mit einer Upgrademeldung
abgelehnt.

## Installation

1. Geometry-Type-ZIP als `plugins/misc/hop-geometry-type` installieren/aktualisieren.
2. Dieses Geoprocessing-ZIP über das bestehende Verzeichnis
   `plugins/transforms/hop-geoprocessing` entpacken/aktualisieren.
3. Hop neu starten; der Bootstrap prüft die gemeinsame Classloader-Gruppe und den Codec.
4. Die installierte Hop-Version ist Apache Hop 2.19.0 mit Java 21.

Das ZIP enthält **sechs** Transform-JARs:

- Geometry Operation
- Coverage Operation
- Spatial Predicate
- Layer Overlay
- Layer Aggregate
- Geometry Converter

und genau `lib/hop-geoprocessing-core.jar`. Es enthält keine zweite Geometry-Type-,
JTS-, Geolatte- oder SLF4J-JAR.

## Enthaltene Transform-API

Paket `ch.so.agi.hop.geoprocessing.transform.geometryconverter`:

- `GeometryConverterMeta` (Transform-ID `GEOMETRY_CONVERTER_TRANSFORM`, Classloader-Gruppe
  `sogeo-geometry`)
- `GeometryConverterData`
- `GeometryConverter` (FAIL / RETURN_NULL / ROUTE_TO_ERROR)
- `GeometryConverterDialog` und headless testbares `GeometryConverterDialogModel`

Die fachliche Konvertierung nutzt ausschliesslich
`ch.so.agi.hop.geoprocessing.core.converter` aus dem Core-JAR.

## Getestet

- Vollständiger Reactor JDK 21: **155 Tests**, 0 Failures, 0 Errors, 0 Skipped.
- Converter-Modul: **23 Tests**.
- JDK 25: **155 Tests**, 0 Failures, 0 Errors, 0 Skipped.
- `scripts/verify-package.py`: sechs Transform-JARs, Converter-Klasse/Icon, Core-JAR,
  keine geteilte Geometry-/JTS-Laufzeit im ZIP.
- ZIP-SHA-256: `130ff450cfb7115e902277815a7a4e8509ee6cb6d4ea38423174e5f5cc6afd87`.

## Noch offen (Etappe 4 / Paket E/F)

- Installed-Hop-E2E mit zehn Converter-Pipelines (Select Values/Binary, Kurven, Z/M,
  NULL/EMPTY, SRID, Overwrite, Fehlerhop, Variablen, Serialisierung);
- echte Fehlerzeilenbilanz und OR14/OR22;
- OR17-Metadata-Injection über eine Pipeline, OR19-SWT-Sichtprüfung;
- Converter-spezifische E2E-Erweiterung von `scripts/run-e2e.py`;
- Veröffentlichung/Push/Deployment (nicht beauftragt).

Details: [`../../docs/implementation/geometry-converter-stage-d.md`](../../docs/implementation/geometry-converter-stage-d.md).
