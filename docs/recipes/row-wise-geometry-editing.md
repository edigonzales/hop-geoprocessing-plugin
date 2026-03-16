# Recipe: Row-wise Geometry Editing

## Goal

Modify or derive a geometry without needing any other row or layer.

Examples:

- buffer points
- fix invalid polygons
- build centroids
- simplify or densify lines

## Pipeline Sketch

```mermaid
flowchart LR
  A["Input rows with geometry"] --> B["Geometry Operation"]
  B --> C["Output rows with derived or replaced geometry"]
```

## Typical Setup

- set the primary geometry field
- set an operation such as `buffer`, `fix_geometry`, `centroid`, or `simplify_topology`
- set `outputMode` to `APPEND` or `REPLACE`

## Constraints and Caveats

- all required geometry inputs must come from the current row
- `explode` is the only common `1:n` exception in this family
- row-wise topology preservation does not preserve shared boundaries across multiple rows
