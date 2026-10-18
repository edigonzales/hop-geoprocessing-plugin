# Runnable Geoprocessing examples

Install Apache Hop 2.19.0, Geometry Type 0.2.0-SNAPSHOT and Geoprocessing
0.1.0-SNAPSHOT as described in the handbook. Open any `.hpl` directly in Hop GUI
and select a **local** pipeline run configuration. No database or remote service
is needed. Keep `fixtures/` alongside the pipelines. Each transform uses one copy.

| Pipeline | Operation | Expected result |
| --- | --- | --- |
| [geometry-operation.hpl](geometry-operation.hpl) | centroid | Two centroids: POINT (1 1), POINT (3 1) |
| [spatial-predicate.hpl](spatial-predicate.hpl) | within / BOOLEAN_COLUMN | First square matches the west mask; second does not |
| [layer-overlay.hpl](layer-overlay.hpl) | intersection | The west square plus the shared boundary of the east square |
| [layer-aggregate.hpl](layer-aggregate.hpl) | collect | One MultiPolygon containing both squares |
| [geometry-converter.hpl](geometry-converter.hpl) | WKT to EWKT | Both polygons with SRID=2056 assigned, coordinates unchanged |
| [coverage-operation.hpl](coverage-operation.hpl) | coverage_validate | Both adjacent squares are valid |

Parameters shared by all pipelines:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `INPUT_DIR` | `${Internal.Pipeline.Filename.Directory}/fixtures` | Directory containing `geometry.csv` and `secondary.csv` |
| `OUTPUT_DIR` | `${Internal.Pipeline.Filename.Directory}/output` | Writable directory; each pipeline writes its own named CSV |

The geometry fixture contains two adjacent 2 × 2 squares in an arbitrary planar
coordinate system. The secondary fixture contains the identical west square.
Select Values prepares native Geometry fields for the two-layer transforms.
The predicate's **Prepare secondary geometry** hop is the info input, while
**Prepare primary geometry** supplies the primary input. The touching east square yields a LineString intersection along its shared boundary;
intersection output can therefore have a lower dimension than the input polygons.
The converter's **Conversion errors** connection is enabled even in FAIL mode;
no errors are expected for these fixtures. It illustrates SRID assignment,
not reprojection.

[expected/](expected) contains the expected CSV rows, including headers. Boolean
values use Hop's `Y`/`N` notation. Run every pipeline and compare its CSV output:

```bash
python3 scripts/run-examples.py --hop-home /absolute/path/to/installed/hop
```

This helper uses an isolated temporary Hop configuration and output directory.
For a single command-line run with a configured `local` run configuration:

```bash
"$HOP_HOME/hop-run.sh" -r local -f "$PWD/examples/geometry-operation.hpl" \
  -p "INPUT_DIR=$PWD/examples/fixtures" -p "OUTPUT_DIR=/tmp/geoprocessing-examples"
```

CI runs these same files against the verified ZIPs as part of installed E2E and
compares parsed CSV rows with the checked-in expectations. All source fixtures,
pipelines and expected files are also downloadable from the
[handbook](https://edigonzales.github.io/hop-geoprocessing-plugin/geoprocessing/main/index.html).
