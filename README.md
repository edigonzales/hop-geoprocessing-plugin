# hop-geoprocessing-plugin

Apache Hop geoprocessing plugin suite for geometry editing, spatial predicates, layer overlay,
and layer aggregation based on JTS and `hop-geometry-type`.

## Modules

- `hop-geoprocessing-core`
  - Shared operation registry, geometry parsing helpers, spatial index cache, overlay row builder,
    and execution services.
- `hop-transform-geometry-ops`
  - Single-input transform for row-wise geometry operations.
  - Execution mode: `Streaming`.
- `hop-transform-spatial-predicate`
  - Primary input + info stream transform for spatial predicates and inner spatial joins.
  - Execution mode: `Caches secondary layer`.
- `hop-transform-layer-overlay`
  - Primary input + info stream transform for layer overlay results.
  - Execution mode: `Blocking per layer`.
- `hop-transform-layer-aggregate`
  - Single-input transform for group-wise geometry aggregation.
  - Execution mode: `Blocking per layer/group`.
- `assemblies/assemblies-hop-geoprocessing-suite`
  - Installable Hop plugin ZIP under `plugins/transforms/hop-geoprocessing`.

## Operation Groups

Inspired by QGIS processing categories:

- `Geometry Ops`
  - constructive and editing operations such as `buffer`, `centroid`, `simplify`, `split`.
- `Spatial Predicate`
  - boolean/filter/join operations such as `intersects`, `contains`, `distance <=`.
- `Layer Overlay`
  - overlay operations producing new geometries and A/B attributes such as `intersection`,
    `clip`, `erase`, `identity`.
- `Layer Aggregate`
  - block-wise aggregation such as `dissolve`, `unary_union`, `collect`.

`intersection` and `intersects` are intentionally separate transforms because they have different
execution models and different outputs.

## Build

```bash
mvn clean verify
```

Build prerequisites:

- Java 17
- Maven
- `hop-geometry-type` available in local Maven repository or reachable through configured
  repositories

## Install In Hop

```bash
unzip -o assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-<version>.zip -d "$HOP_HOME"
```

This installs:

```text
$HOP_HOME/plugins/transforms/hop-geoprocessing
```

## Fast Local Sync

```bash
./scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

The script rebuilds the suite assembly, removes the target plugin directory, and unzips the latest
artifact into the given Hop home.
