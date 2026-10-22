# Repository instructions

## Contracts and compatibility

Read the [shared CI contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md)
and [plugin repository contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/plugin-repository-contract.md)
before changing workflows, test setup or repository structure. The repository uses
the `multi-module-suite` profile. Workflow references and helper `ci-ref` values
intentionally follow `main`; preserve them. Only change this repository.

The suite inherits `ch.so.agi:hop-plugin-parent:0.1.0-SNAPSHOT`, including DocLint.
Keep transform IDs, metadata fields, Maven coordinates and `sogeo-geometry`
classloader ownership compatible. Geometry Type/JTS belong to the separately
installed Geometry Type plugin and must not enter the Geoprocessing ZIP.

Geometry Reproject also uses the shared GeoTools 35.1 EPSG runtime and Indriya
2.2 as `provided` dependencies. Do not add a Raster Type dependency or duplicate
these libraries in the ZIP. Its installed tests exercise XY-only reprojection,
Z/M preservation and the real shared plugin classloader without Vector/Raster.

## CI and tests

Run commands from the repository root with Bash, Python 3, Maven and JDK 21
(`JAVA_HOME` and `PATH` set to that JDK). Compatibility jobs also use JDK 25.
Use `xvfb-run -a` for Linux SWT tests; the parent sets `-XstartOnFirstThread` on macOS.
Set `HOP_CI_DIR` to an absolute checkout of `hop-plugin-ci` at the revision selected
by the workflow's `main` reference, then generate the same settings as CI:

```bash
CI_TEST_TMP="$(mktemp -d)"
export MAVEN_SETTINGS="$CI_TEST_TMP/maven-settings.xml"
python3 "$HOP_CI_DIR/scripts/write_maven_settings.py" --output "$MAVEN_SETTINGS"
mvn -s "$MAVEN_SETTINGS" -U -B -ntp clean verify
python3 -m unittest discover -s scripts/tests -v
python3 scripts/verify-package.py
python3 "$HOP_CI_DIR/scripts/check-plugin-repository.py" --profile multi-module-suite
```

`.mvn/maven.config` selects Wagon to avoid the observed unauthenticated-upload
`Broken pipe`, and a global-settings mirror for Central only. Generated user
settings still supply repositories and credentials. Do not skip DocLint.
For compatibility use `clean test`; CI uses a fresh `.ci/maven-repository` in each
matrix cell. Geometry JAR/POM and the shared parent resolve from their published
base SNAPSHOT coordinates. Never build Geometry Type source as a CI fallback.

The dependency job downloads the published Geometry ZIP once. In canonical CI,
`scripts/verify-published-geometry.py` compares its Geometry JAR with the JAR in
the actual build repository before the canonical bundle is published. Preserve
this check, the package checks and the installed-Hop publication gate.

## Installed Hop and user examples

Download the published Geometry ZIP with the shared `download_maven_artifact.py`
helper. Use disposable Apache Hop 2.19.0 installations, never a user's live Hop:

```bash
python3 scripts/run-e2e.py --hop-home /absolute/disposable/hop \
  --plugin-zip assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-0.1.0-SNAPSHOT.zip \
  --geometry-zip /absolute/downloaded/geometry.zip \
  --negative-hop-home /absolute/second-disposable/hop
```

This runs the original installed pipelines, converter suite, public examples and
the legacy-runtime negative test. E2E consumes the canonical ZIP without rebuilding.
Only successful main builds publish; PRs never publish. Publication resolves the
published ZIP in a fresh repository and compares it with the canonical bundle.

## Documentation

English AsciiDoc under `docs/` is canonical. Each transform has a page under
`docs/transforms/`, matching its annotation URL, ID and handbook anchor. Keep
`[.gui-mockup]` sketches aligned with dialog labels, order and visibility. Each
family has a runnable example with fixtures and expected results under `examples/`.
Old Markdown entry points are pointers; keep historical implementation reports
and `handover/` artifacts unchanged.

```bash
python3 scripts/build-docs.py
python3 scripts/check-docs-site.py target/docs-site
```

The local Biblios build includes working files; CI uses `--revision "$GITHUB_SHA"`.
Check rendered anchors, search, example downloads and loaded GUI CSS, and inspect
GUI mockups visually. Pages deployment is restricted to main.
