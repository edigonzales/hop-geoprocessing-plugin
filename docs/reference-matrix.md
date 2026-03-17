# Reference Matrix

This is the fastest way to scan the plugin from an operator perspective.

## Family Summary

| Transform | Operations | Inputs | Execution | RAM | Spatial Index | Output style |
| --- | ---: | --- | --- | --- | --- | --- |
| Geometry Operation | 34 | 1 layer | Streaming | current row | no | mostly 1:1, sometimes 1:n |
| Spatial Predicate | 9 | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | boolean, filter, or join; optional reject stream for filters |
| Layer Overlay | 4 | primary + secondary | Blocking per layer | full secondary layer | STRtree on secondary | 0:n overlay rows |
| Layer Aggregate | 4 | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1 row per group |
| Coverage Operation | 5 | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | row-preserving 1:1; optional reject stream for validate |

## Geometry Operation

| Transform | Operation | Category | Inputs | Execution | RAM | Spatial Index | Output-Cardinality | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Geometry Operation | `buffer` | Constructive | 1 layer / same row | Streaming | current row | no | 1:1 | creates a basic buffer |
| Geometry Operation | `buffer_extended` | Constructive | 1 layer / same row | Streaming | current row | no | 1:1 | creates a buffer with cap and join controls |
| Geometry Operation | `centroid` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a centroid point |
| Geometry Operation | `interior_point` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates an interior point |
| Geometry Operation | `boundary` | Conversion | 1 layer / same row | Streaming | current row | no | 1:1 | extracts the boundary |
| Geometry Operation | `convex_hull` | Constructive | 1 layer / same row | Streaming | current row | no | 1:1 | creates a convex hull |
| Geometry Operation | `concave_hull` | Constructive | 1 layer / same row | Streaming | current row | no | 1:1 | creates a concave hull |
| Geometry Operation | `simplify` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | simplifies with Douglas-Peucker |
| Geometry Operation | `simplify_vw` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | simplifies with Visvalingam-Whyatt |
| Geometry Operation | `simplify_topology` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | simplifies while preserving single-geometry topology |
| Geometry Operation | `densify` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | adds intermediate vertices |
| Geometry Operation | `reverse` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | reverses line or ring direction |
| Geometry Operation | `explode` | Collection | 1 layer / same row | Streaming | current row | no | 1:n | splits multi-geometries into parts |
| Geometry Operation | `line_merge` | Collection | 1 layer / same row | Streaming | current row | no | 1:1 | merges connected lines |
| Geometry Operation | `polygonize` | Constructive | 1 layer / same row | Streaming | current row | no | 1:1 | polygonizes linework |
| Geometry Operation | `remove_small_holes` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | removes holes below a threshold |
| Geometry Operation | `remove_all_holes` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | removes all polygon holes |
| Geometry Operation | `fix_geometry` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | repairs invalid geometry |
| Geometry Operation | `reduce_precision` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | rounds coordinates to a precision grid |
| Geometry Operation | `to_2d` | Conversion | 1 layer / same row | Streaming | current row | no | 1:1 | removes Z and M ordinates |
| Geometry Operation | `to_multi` | Conversion | 1 layer / same row | Streaming | current row | no | 1:1 | converts single geometry to multi geometry |
| Geometry Operation | `extract_coordinates` | Conversion | 1 layer / same row | Streaming | current row | no | 1:1 | emits coordinates as points |
| Geometry Operation | `envelope` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a bounding box geometry |
| Geometry Operation | `minimum_bounding_rectangle` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a minimum bounding rectangle |
| Geometry Operation | `minimum_diameter` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a minimum diameter line |
| Geometry Operation | `mbc` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a minimum bounding circle |
| Geometry Operation | `linear_referencing` | Measure | 1 layer / same row | Streaming | current row | no | 1:1 | creates a point at a linear distance |
| Geometry Operation | `intersection` | Overlay | 1 layer / same row | Streaming | current row | no | 1:1 | intersects two geometry fields |
| Geometry Operation | `difference` | Overlay | 1 layer / same row | Streaming | current row | no | 1:1 | subtracts one geometry field from another |
| Geometry Operation | `sym_difference` | Overlay | 1 layer / same row | Streaming | current row | no | 1:1 | computes the symmetric difference |
| Geometry Operation | `union` | Overlay | 1 layer / same row | Streaming | current row | no | 1:1 | unions two geometry fields |
| Geometry Operation | `snap` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | snaps to a second geometry field |
| Geometry Operation | `snap_to_self` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | snaps a geometry to itself |
| Geometry Operation | `split` | Edit | 1 layer / same row | Streaming | current row | no | 1:1 | splits geometry with another geometry |

## Spatial Predicate

| Transform | Operation | Category | Inputs | Execution | RAM | Spatial Index | Output-Cardinality | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Spatial Predicate | `intersects` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether geometries intersect |
| Spatial Predicate | `contains` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether the primary contains the secondary |
| Spatial Predicate | `within` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether the primary is within the secondary |
| Spatial Predicate | `touches` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether geometries touch |
| Spatial Predicate | `crosses` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether geometries cross |
| Spatial Predicate | `overlaps` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether geometries overlap |
| Spatial Predicate | `disjoint` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..1 or filter | tests whether geometries are disjoint |
| Spatial Predicate | `distance_lte` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..n by result mode | tests whether distance is below a threshold |
| Spatial Predicate | `distance_gte` | Predicate | primary + secondary | Caches secondary layer | full secondary layer | STRtree on secondary | 0..1 or filter | tests whether distance is above a threshold |

## Layer Overlay

| Transform | Operation | Category | Inputs | Execution | RAM | Spatial Index | Output-Cardinality | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Layer Overlay | `intersection` | Overlay | primary + secondary | Blocking per layer | full secondary layer | STRtree on secondary | 0:n | creates overlap fragments with attributes from both inputs |
| Layer Overlay | `clip` | Overlay | primary + secondary | Blocking per layer | full secondary layer | STRtree on secondary | 0:n | keeps only the primary portion inside the secondary |
| Layer Overlay | `erase` | Overlay | primary + secondary | Blocking per layer | full secondary layer | STRtree on secondary | 0:n | removes primary portions covered by the secondary |
| Layer Overlay | `identity` | Overlay | primary + secondary | Blocking per layer | full secondary layer | STRtree on secondary | 0:n | keeps primary coverage and adds secondary attributes where present |

## Layer Aggregate

| Transform | Operation | Category | Inputs | Execution | RAM | Spatial Index | Output-Cardinality | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Layer Aggregate | `dissolve` | Aggregate | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1 row per group | merges geometries per group |
| Layer Aggregate | `unary_union` | Aggregate | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1 row per group | performs a robust union per group |
| Layer Aggregate | `coverage_union` | Aggregate | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1 row per group | unions a valid polygon coverage efficiently |
| Layer Aggregate | `collect` | Aggregate | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1 row per group | collects group member geometries |

## Coverage Operation

| Transform | Operation | Category | Inputs | Execution | RAM | Spatial Index | Output-Cardinality | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Coverage Operation | `coverage_validate` | Coverage | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1:1 row-preserving | finds coverage errors |
| Coverage Operation | `coverage_simplify` | Coverage | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1:1 row-preserving | simplifies while preserving coverage topology |
| Coverage Operation | `coverage_simplify_inner` | Coverage | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1:1 row-preserving | simplifies inner edges only |
| Coverage Operation | `coverage_simplify_outer` | Coverage | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1:1 row-preserving | simplifies outer edges only |
| Coverage Operation | `coverage_clean` | Coverage | 1 layer, optional grouping | Blocking per layer/group | active layer/group | no | 1:1 row-preserving | cleans and snaps a coverage |
