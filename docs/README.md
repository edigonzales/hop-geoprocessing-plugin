# Plugin Documentation

This documentation is organized along two axes:

- `What does the transform do?`
- `How does the transform execute?`

Many transforms look similar on the surface but behave very differently with respect to blocking,
RAM, secondary inputs, and spatial index usage.

## Documentation Map

- [Execution Models](execution-models.md)
  - row-wise vs cached-secondary vs blocking
- [Reference Matrix](reference-matrix.md)
  - compact table of all families and operations
- [Performance & Memory](performance-memory.md)
  - what is buffered, what is indexed, and what can become expensive
- Families
  - [Geometry Operation](families/geometry-operation.md)
  - [Spatial Predicate](families/spatial-predicate.md)
  - [Layer Overlay](families/layer-overlay.md)
  - [Layer Aggregate](families/layer-aggregate.md)
  - [Coverage Operation](families/coverage-operation.md)
- [Recipes](recipes/README.md)

## Family Overview

| Family | Operations | Inputs | Execution | RAM profile | Spatial index |
| --- | ---: | --- | --- | --- | --- |
| Geometry Operation | 34 | 1 layer | Streaming | current row only | no |
| Spatial Predicate | 9 | primary + secondary | Caches secondary layer | full secondary layer in memory | STRtree on secondary |
| Layer Overlay | 4 | primary + secondary | Blocking per layer | full secondary layer in memory | STRtree on secondary |
| Layer Aggregate | 4 | 1 layer, optional grouping | Blocking per layer/group | all rows in layer/group | no |
| Coverage Operation | 5 | 1 layer, optional grouping | Blocking per layer/group | all rows in layer/group | no |

## Visual Map

```mermaid
flowchart LR
  G["Geometry Operation\n34 ops"] --> G1["1 input"]
  G --> G2["Streaming"]
  G --> G3["No cache / no spatial index"]

  S["Spatial Predicate\n9 ops"] --> S1["Primary + secondary"]
  S --> S2["Secondary cached in RAM"]
  S --> S3["STRtree on secondary"]

  O["Layer Overlay\n4 ops"] --> O1["Primary + secondary"]
  O --> O2["Secondary cached in RAM"]
  O --> O3["STRtree + optional union geometry"]

  A["Layer Aggregate\n4 ops"] --> A1["1 input / optional grouping"]
  A --> A2["Blocking per layer/group"]
  A --> A3["No spatial index"]

  C["Coverage Operation\n5 ops"] --> C1["1 input / optional grouping"]
  C --> C2["Blocking per layer/group"]
  C --> C3["Row-preserving results"]
```
