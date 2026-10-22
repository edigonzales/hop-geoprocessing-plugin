# hop-geoprocessing-plugin

Apache Hop transforms for geometry editing, predicates, overlays, aggregation,
representation conversion, coordinate reprojection and polygon coverages, using the shared Geometry Type runtime.

## Features

- Geometry Operation: 35 streaming operations, including explicit curve linearization.
- Spatial Predicate: nine predicates against a cached secondary layer, with filter and join modes.
- Layer Overlay: intersection, clip, erase and identity against a secondary layer.
- Layer Aggregate: dissolve, unary union, coverage union and collect per layer/group.
- Geometry Converter: native geometry, WKT/EWKT, binary/hex WKB/EWKB and Point coordinates.
- Geometry Reproject: streaming XY reprojection between 2D EPSG CRS, preserving Z/M.
- Coverage Operation: six operations for validation, cleaning, simplification and shared curve linearization.

## Requirements

Apache Hop 2.19.0 and Java 21 (Java 25 is also tested). Install the published
Geometry Type 0.2.0-SNAPSHOT plugin separately. Building requires Maven and Python 3;
SWT tests need a display (`xvfb-run -a` on headless Linux).

## Install

Download the published ZIPs from [the Maven repository](https://jars.interlis.guru/)
and extract both into the same Hop home:

```bash
unzip hop-geometry-type-plugin-0.2.0-SNAPSHOT.zip -d "$HOP_HOME"
unzip hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip -d "$HOP_HOME"
```

Geometry Type installs under `plugins/misc/hop-geometry-type`; this suite installs
under `plugins/transforms/hop-geoprocessing`. Restart Hop after installation.
See the [handbook](https://edigonzales.github.io/hop-geoprocessing-plugin/geoprocessing/main/index.html)
for version compatibility and configuration.

## Documentation

The English, searchable [handbook](https://edigonzales.github.io/hop-geoprocessing-plugin/geoprocessing/main/index.html)
is built from [docs/index.adoc](docs/index.adoc) and [docs/master.adoc](docs/master.adoc).
Runnable [examples](examples/README.md) include local fixtures and expected results.
Build the current working documentation with `python3 scripts/build-docs.py`;
output is in `target/docs-site`. Use `--serve` for a local preview.

## Build and development

Follow [AGENTS.md](AGENTS.md) for the shared CI contracts, generated Maven settings,
Java versions and installed-Hop checks. The shared Maven parent supplies dependency
and plugin management, SWT profiles and mandatory DocLint checks.

```bash
python3 "$HOP_CI_DIR/scripts/write_maven_settings.py" --output "$MAVEN_SETTINGS"
mvn -s "$MAVEN_SETTINGS" -U -B -ntp clean verify
python3 -m unittest discover -s scripts/tests -v
python3 scripts/verify-package.py
```

Maven resolves Geometry and its real POM from the published repositories. Repository
configuration selects Wagon and a mirror for Central only. For a development Hop
installation, `scripts/dev-sync-hop-plugin.sh "$HOP_HOME"` rebuilds and replaces the
suite directory; install Geometry Type separately first. Implementation reports in
[docs/implementation](docs/implementation) and [handover](handover) are historical records.
The optional streaming smoke benchmark is `scripts/benchmark-converter.py`.

## Modules and artifacts

The suite parent is `ch.so.agi:hop-geoprocessing-plugin-parent:0.1.0-SNAPSHOT`,
inheriting `ch.so.agi:hop-plugin-parent:0.1.0-SNAPSHOT`. It contains
`hop-geoprocessing-core`, seven `hop-transform-*` modules and the assembly modules.
The installable artifact is `ch.so.agi:hop-geoprocessing-plugin:zip:0.1.0-SNAPSHOT`.
Its ZIP contains the seven transform JARs and the core JAR; Geometry Type and JTS are
provided by their separately installed plugin.

## CI and publication

The workflows follow `hop-plugin-ci@main` with `ci-ref: main`. Ubuntu/Java 21 builds
the canonical ZIP with `clean verify`, package checks, the repository contract and
DocLint. Linux/macOS/Windows × Java 21/25 form the compatibility matrix.
Each matrix build uses a fresh Maven repository. A dependency job downloads the
published Geometry ZIP once; the canonical job compares its JAR bytes with the
published JAR resolved by Maven. Installed E2E consumes that same ZIP and canonical
suite bundle, including converter and reprojection scenarios, all seven user examples and the legacy
GeometryCodec negative test.

Successful `main` runs publish the exact verified bundle to the snapshot repository
without rebuilding, then download it through Maven for comparison. Publishing uses
`INTERLIS_MAVEN_USERNAME` and `INTERLIS_MAVEN_TOKEN`. PRs only verify. A separate
Biblios workflow checks the exact commit and deploys Pages only from `main`.

## License

MIT. See [LICENSE](LICENSE).
