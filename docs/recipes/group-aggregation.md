# Recipe: Group Aggregation / Dissolve

## Goal

Build one geometry result per whole layer or per logical group.

Examples:

- dissolve parcels by municipality
- collect all geometries per object class
- coverage-union polygons per district

## Pipeline Sketch

```mermaid
flowchart LR
  A["Input rows"] --> G["Group fields\n(optional)"]
  G --> L["Layer Aggregate"]
  L --> R["One row per group"]
```

## Operation Variants in This Example

- `dissolve`
  - group-wise merge of geometries
- `unary_union`
  - robust group-wise union
- `coverage_union`
  - efficient union for valid polygon coverages
- `collect`
  - keep all member geometries as a collected result

## Constraints and Caveats

- the largest group controls memory demand
- without grouping, the full layer becomes one working set
- `coverage_union` requires polygon coverages with valid shared boundaries
