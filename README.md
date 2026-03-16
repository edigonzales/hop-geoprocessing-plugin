# hop-geoprocessing-plugin

Apache Hop geoprocessing plugin suite for geometry editing, spatial predicates, layer overlay,
coverage processing, and layer aggregation based on JTS and `hop-geometry-type`.

## Documentation

Detailed documentation lives in [docs/README.md](docs/README.md).

- [Overview](docs/README.md)
- [Execution Models](docs/execution-models.md)
- [Reference Matrix](docs/reference-matrix.md)
- [Performance & Memory](docs/performance-memory.md)
- Family docs:
  - [Geometry Operation](docs/families/geometry-operation.md)
  - [Spatial Predicate](docs/families/spatial-predicate.md)
  - [Layer Overlay](docs/families/layer-overlay.md)
  - [Layer Aggregate](docs/families/layer-aggregate.md)
  - [Coverage Operation](docs/families/coverage-operation.md)
- [Recipes](docs/recipes/README.md)

## Transform Families

- `Geometry Operation`
  - 34 operations
  - single input
  - execution: `Streaming`
- `Spatial Predicate`
  - 9 operations
  - primary input + secondary info stream
  - execution: `Caches secondary layer`
- `Layer Overlay`
  - 4 operations
  - primary input + secondary info stream
  - execution: `Blocking per layer`
- `Layer Aggregate`
  - 4 operations
  - single input, optional grouping
  - execution: `Blocking per layer/group`
- `Coverage Operation`
  - 5 operations
  - single input, optional grouping
  - execution: `Blocking per layer/group`

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
