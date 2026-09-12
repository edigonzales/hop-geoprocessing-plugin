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
  - 35 operations
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
  - 6 operations
  - single input, optional grouping
  - execution: `Blocking per layer/group`

## Build

```bash
mvn -U -B -ntp clean verify
```

Build prerequisites:

- Java 21 (Java 25 is part of the CI compatibility matrix)
- Maven
- `ch.so.agi:hop-geometry-type:0.2.0-SNAPSHOT` available through the configured Maven snapshot
  repository or installed locally from the current Geometry Type build

The project targets Apache Hop `2.19.0` and keeps its own version at `0.1.0-SNAPSHOT`. Maven
resolves the Geometry Type dependency by its normal base version; no timestamped snapshot is
recorded in this repository.

## Install In Hop

```bash
unzip -o assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-<version>.zip -d "$HOP_HOME"
```

This installs:

```text
$HOP_HOME/plugins/transforms/hop-geoprocessing
```

The installable Maven ZIP is published as:

```text
ch.so.agi:hop-geoprocessing-plugin:0.1.0-SNAPSHOT
```

The ZIP contains the five geoprocessing transform JARs and `hop-geoprocessing-core.jar`. The
Geometry Type plugin is installed separately at `plugins/misc/hop-geometry-type`; its shared
JTS/Geometry runtime is deliberately not duplicated in this ZIP.

## CI and publication

The local workflow tests six combinations:

```text
Ubuntu, macOS, Windows × Java 21, Java 25
```

Ubuntu/Java 21 is the canonical run. It executes `clean verify`, validates the ZIP and creates the
publishable bundle. The other five runs execute `clean test` for compatibility only. Linux tests
use `xvfb-run` when available and macOS tests use `-XstartOnFirstThread`.

The canonical bundle is then installed into a fresh Apache Hop 2.19.0 installation together with
the current Geometry Type snapshot. The Installed-Hop E2E reads deterministic WKT fixtures,
executes the Geometry Operation transform and checks the resulting centroids. Pull requests never
publish artifacts. A successful `main` run publishes the exact verified ZIP to
`https://jars.interlis.guru/snapshots/` without rebuilding it; GitHub plugin Releases are not used.

The Maven publish job requires the protected secrets `INTERLIS_MAVEN_USERNAME` and
`INTERLIS_MAVEN_TOKEN`. Package validation can be run locally with:

```bash
python3 scripts/verify-package.py
python3 scripts/run-e2e.py --help
```

## Fast Local Sync

```bash
./scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

The script rebuilds the suite assembly, removes the target plugin directory, and unzips the latest
artifact into the given Hop home.

## Explicit curve linearization

`Geometry Operation / linearize_curves` converts exact circular curves to linear JTS geometries.
The distance parameter is `maxError`: maximum XY chord deviation (the sagitta/Pfeilhöhe), in
coordinate units. It must be positive and finite. Z/M are interpolated on each side of the
original intermediate control point. Opposite representations use identical XY sample positions.
This row operation does not coordinate differently partitioned boundaries in other rows.

`Coverage Operation / coverage_linearize` first gathers the whole layer or configured group.
It splits shared straight/circular edges at all supplied boundary nodes before constructing
common chords. This handles opposite ring directions and different subdivisions of the same
exact circle. Output attributes and SRIDs are retained. Optional target XY resolution and X/Y
origins must be supplied together and should match the eventual FileGDB writer. Grid displacement
is deducted from the error budget; excessive coarseness or collapsed edges fail.

The operation validates output polygons, coverage overlaps, and changes of holes/adjacency
introduced by the target grid. It does not snap, repair or automatically refine invalid input.
Nearly coincident circles whose definitions are numerically ambiguous are rejected rather than
silently merged. Use analytically consistent source boundaries. The entire group must succeed
before output; this operation buffers the layer and has pairwise validation work, so it is intended
for bounded groups rather than unbounded streams. These checks do not constitute a general exact
arithmetic topology engine for arbitrary intersecting curved input.
