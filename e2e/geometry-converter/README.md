# Installed Geometry Converter E2E

These pipelines are executed by `scripts/run-e2e.py` against a disposable Apache Hop
2.19.0 installation. The existing `e2e/geoprocessing.hpl`,
`e2e/linearize-curves.hpl` and `e2e/coverage-linearize.hpl` checks remain unchanged.

Every converter pipeline has an enabled Hop error connection. `FAIL` and `RETURN_NULL`
pipelines use a Dummy error sink; `ROUTE_TO_ERROR` pipelines write the standard error
fields (`errors`, `error_desc`, `error_fields`, `error_codes`) to an error CSV.

| Pipeline | Spec / proof | Output check |
|---|---|---|
| `point-fields.hpl` | T05, T06, OR20 | Coordinate CSV → native geometry → existing `GeometryOperation` centroid consumer → EWKT; configured XYZM layout with NaN for absent optional ordinates; SRID 2056. |
| `point-layouts.hpl` | T05, T06 | Independent XY/XYZ/XYM/XYZM branches; exact EWKT layout and values. |
| `select-values-binary.hpl` | T02, T03 | Converter EWKB → real Select Values `Binary→Geometry` → `Geometry→Binary` → converter EWKT. |
| `encoded-output.hpl` | T04, T07 | Fixed independent ISO WKB BE hex plus normalized WKT/EWKT. |
| `curve-roundtrip.hpl` | OR02, OR20 | `CIRCULARSTRING Z` controls through native geometry and the existing linearize consumer. |
| `overwrite.hpl` | OR16 | Target `geometry` in the middle of the row is replaced in place; `tail` remains. |
| `error-null.hpl` | T10 | Four input rows, valid/bad/valid/NULL; bad and NULL targets are NULL, following row is valid. |
| `error-hop.hpl` | OR14, OR22 | Five input rows; exactly three normal and two error rows; IDs 2/4 and `GC_INVALID_WKT`. |
| `serialization.hpl` | T07, T08 | 5,000 rows through `SortRows` with `sort_size=50`; log shows `Opening 100 tmp-files`; all IDs and EWKT values survive. |
| `variable-fields.hpl` | OR15 | Raw `${SRC_FIELD}`/`${DST_FIELD}` in HPL; `hop-run` parameters resolve both names. |
| `ost-regressions.hpl` | OR01, OR02, OR14, OR22 | EMPTY, curve, valid and invalid rows; normal/error IDs partition the input; values and controls exact. |
| `coordinate-output.hpl` | OR11, OR13 | XY/XYZ/XYM/EMPTY/line/polygon/valid point; seven normal rows, non-Points/EMPTY NULL, no implicit loss. |
| `coordinate-errors.hpl` | OR12 | Full NULL tuple normal; partial NULL and NaN error; exact IDs and `GC_*` codes. |
| `negative-smoke.hpl` | OR21 / GeoJSON API | Used twice in the second Hop installation: first without `GeometryCodec`, then retaining it but without the GeoJSON API; both startups must fail clearly. |
| `streaming-benchmark.hpl` | performance smoke | 100,000 points through the native text conversion path; see `scripts/benchmark-converter.py`. |

The standard installed run also executes the three public `examples/geometry-geojson-*`
pipelines: RFC 7946, Legacy 2008 with LV95, and JSON-FG with M metadata and an exact
CircularString. Each decodes JSON to native Geometry/context, re-encodes it and
checks the decoded EWKT against the local expected CSV. See
[the examples guide](../../examples/README.md).

## Notes

- Hop's numeric text reader accepts `NaN` but not `Infinity`; the `Infinity` case is covered by
  the core Java runtime test, not by the installed CSV pipeline.
- The installed test proves native class compatibility because `GeometryOperation` consumes the
  converter's native geometry. The synthetic legacy ZIP is test-only and is generated from the
  current Geometry ZIP by removing the codec class; it is not a historical release.
- The error connection is mandatory in all converter modes; absence is a configuration failure.
- The benchmark reports wall time and peak child RSS but is not a hard CI performance gate.
