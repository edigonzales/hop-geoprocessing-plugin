# Geometry Converter

The **Geometry Converter** transform changes the representation of an existing geometry
or builds a Point from X/Y(/Z/M) fields. It is row-wise and does not reproject, buffer,
simplify or otherwise modify coordinates. All geometry parsing and writing goes through
the shared Geometry Type codec (`com.atolcd.hop.gis.geometry.codec.GeometryCodec`), not
through a private parser in the Geoprocessing plugin.

## Representations

| Direction | Values |
|---|---|
| Input | `GEOMETRY`, `WKT_EWKT`, `WKB_BYTES`, `WKB_HEX`, `COORDINATES` |
| Output | `GEOMETRY`, `WKT`, `EWKT`, `WKB_BYTES`, `EWKB_BYTES`, `WKB_HEX`, `EWKB_HEX`, `COORDINATES` |

`GEOMETRY` is the native Hop value type from the Geometry Type plugin. Binary input is raw
WKB/EWKB without any Hop stream framing. Hex input is plain hexadecimal (case-insensitive,
optional `0x`), not WKT.

`WKT` writes no SRID prefix. `EWKT` writes `SRID=<n>;` when a positive effective SRID is
known. ISO WKB and WKT omit SRID; EWKB stores it in the root header. `COORDINATES` output
never carries SRID information. `sridOmitted` in the converter summary records this case.

## Coordinate input and output

Coordinate input uses **one Point per row**:

- X and Y are required numeric fields.
- Z and M are optional numeric fields; leaving them blank means the ordinate is absent.
- X/Y/Z/M may be integer, number or big-number value metas.
- An all-NULL tuple produces a NULL row output.
- A partially supplied tuple, NaN or infinity is a data error.

Coordinate output is also Point-only. The selected X/Y fields are always written; Z/M are
written only when configured. A NULL or non-Point geometry produces NULL target ordinates;
a non-Point geometry is a data error. Extra input ordinates are allowed: a Point with Z can
still be written to X/Y only.

## SRID handling

| Mode | Behaviour |
|---|---|
| `KEEP` | Preserve the effective SRID of the input geometry. |
| `SET_IF_MISSING` | Assign the configured positive SRID when the input has no known SRID. |
| `REPLACE` | Replace the root and child SRIDs with the configured positive SRID. |
| `CLEAR` | Remove the SRID. |

SRID handling is **not** a coordinate transformation. No EPSG database lookup or
reprojection is performed. The configured value must be a positive decimal integer and
may use Hop variables. Inactive SRID values are not parsed.

## NULL and EMPTY

The converter keeps `POINT EMPTY`, `LINESTRING EMPTY` and `POLYGON EMPTY` as empty
geometries in native and supported binary output. A NULL input produces NULL output.
The older `ValueMetaGeometry` conversion/Select Values path is separate and still maps
`EMPTY` to NULL; that legacy contract is intentionally unchanged.

## Byte order and WKB dialect

- `BIG_ENDIAN` and `LITTLE_ENDIAN` are honoured recursively for every geometry component.
- `WKB_*` writes ISO WKB (no SRID in the header).
- `EWKB_*` writes EWKB and sets the SRID flag when a positive SRID is known; an explicitly
  known SRID 0 is still representable in EWKB.
- Curves, Z/M and nested collections use the same shared codec options.

## Fields and overwrite

- A single output field is the default (`geometry`).
- Coordinate output uses `x`, `y` and optional `z`, `m` names.
- Existing target fields are replaced only with **Overwrite existing fields** enabled.
  Otherwise a name collision is a configuration error.
- Swapping X and Y target names is allowed with overwrite; it is not a special case.
- Inactive field mappings remain in the transform metadata so that switching format in the
  dialog does not erase them. They are not read or validated at runtime.

Field names and the active SRID value support Hop variables. Raw values are persisted; they
are resolved once when the transform schema is compiled. Unresolved or empty resolved
required variables are configuration errors.

## Error handling

Every Geometry Converter needs an enabled Hop error connection with a target transform,
regardless of the selected error mode. Without it, the pipeline reports a configuration
error before the first row.

| Mode | Behaviour |
|---|---|
| `FAIL` | Stop at the first data error. This is not a completed processing run. |
| `RETURN_NULL` | Emit the input row with all configured target values NULL and continue. |
| `ROUTE_TO_ERROR` | Send the unmodified input row to the Hop error connection and continue. |

Configuration, plan and classloader failures are fatal in all modes. Data errors carry a
stable `GC_*` code; the summary reports input/normal/error/null-output/SRID-omitted counts
and a failure count per code.

## Shared installation

The transform is part of the existing Geoprocessing ZIP, not a separately installable
plugin. Install the updated Geometry Type ZIP and the Geoprocessing ZIP together. The
Geoprocessing ZIP does not contain a second Geometry Type or JTS runtime. If the installed
Geometry Type is too old and lacks `GeometryCodec`, startup fails with a clear upgrade
message instead of falling back to a text or reflection bridge.


## Installed E2E

The installed suite under `e2e/geometry-converter/` covers coordinate layouts, Select Values
binary conversion, encoded output, curves, overwrite, RETURN_NULL, a real error hop,
SortRows spilling, variable field names, EMPTY/error regressions and coordinate-output
row balance. A second isolated Hop installation verifies that an outdated Geometry Type
runtime without `GeometryCodec` is rejected with an upgrade error.

A 100,000-point streaming smoke profile is available through
`scripts/benchmark-converter.py`; it is not a hard performance gate.

See [the installed E2E README](../../e2e/geometry-converter/README.md) and
[Stage E](../implementation/geometry-converter-stage-e.md).
