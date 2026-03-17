# Coverage Operation

`Coverage Operation` is the coverage-wide toolbox for polygon coverages. It validates, cleans, and
topology-preservingly simplifies while keeping row cardinality stable.

## At a Glance

| Field | Value |
| --- | --- |
| Purpose | Validate, clean, and simplify polygon coverages |
| Required Inputs | 1 layer, optional `groupFieldNames` |
| Execution Mode | blocking |
| Needs Whole Layer? | yes, at least the full active coverage group |
| Needs Second Layer? | no |
| What Stays in RAM? | all rows of the active coverage group |
| Spatial Index | no |
| Output Behavior | `1:1`, row-preserving |

## Diagram

```mermaid
flowchart LR
  A["Input rows of one coverage/group"] --> B["Buffer complete coverage"]
  B --> C{"Operation"}
  C --> V["Validate coverage"]
  C --> S["Simplify coverage"]
  C --> CL["Clean coverage"]
  V --> O["Emit same row order\n+ status/error geometry"]
  S --> O2["Emit same row order\n+ simplified geometry"]
  CL --> O2
```

## Operations in This Family

- `coverage_validate`
- `coverage_simplify`
- `coverage_simplify_inner`
- `coverage_simplify_outer`
- `coverage_clean`

Full list: [Reference Matrix](../reference-matrix.md#coverage-operation)

## Important Parameters

- `primaryGeometryField`
  - polygon geometry field used to build the coverage
- `groupFieldNames`
  - optional grouping fields; each group is processed as a separate coverage
- operation-specific tolerance parameters
  - simplification, validation, and cleaning use different distance and gap-related settings
  - `coverage_validate` can optionally disallow all true coverage holes, independent of `gapWidth`
- output-field parameters
  - validation appends status and error fields; simplify and clean return row-preserving geometry output
- optional reject target hop
  - `coverage_validate` can also duplicate invalid rows to a second QA/reject output stream

## Validate Output Streams

When the selected operation is `coverage_validate`, the transform keeps the main output
row-preserving and appends validation result fields. By default these fields are
`coverage_is_valid` and `coverage_error`, but both names are configurable.

| Stream | Rows included | Fields | Empty when |
| --- | --- | --- | --- |
| Main output | All input rows, in original row order | All input fields plus the validation boolean field and the error geometry field | Only when the transform receives no input rows |
| Reject target | Only rows whose validation boolean is `false` | The same row structure as the main output | No invalid rows were found, no reject target is connected, or the selected operation is not `coverage_validate` |

Validation result semantics:

- With the default field names, valid rows are emitted on the main output with
  `coverage_is_valid = true` and `coverage_error = null`.
- With the default field names, invalid rows are emitted on the main output with
  `coverage_is_valid = false` and `coverage_error = <error geometry>`.
- `Gap width` detects only narrow gaps up to the configured maximum width.
- `Disallow coverage holes` is stricter: every interior hole in the full coverage union is invalid,
  regardless of size. Disjoint coverage islands are still allowed.
- If a reject target hop is configured, invalid rows are duplicated to that target. They are not
  removed from the main output.
- If no coverage errors are found, the reject target remains empty.

## Common Pitfalls

- Coverage operations require polygon geometries and full coverage context.
- They are blocking even though the result remains `1:1` and row-preserving.
- `coverage_validate` appends validation-status and error-geometry fields.
- `Gap width` is not a strict no-hole rule; use `Disallow coverage holes` if the coverage must not
  contain any true interior holes.
- The reject target stream only applies to `coverage_validate`; simplify and clean stay single-stream.
- `coverage_simplify*` preserves topology across the full coverage, which is different from
  row-wise `simplify_topology`.

## Related Recipes

- [Coverage Validate, Clean, Simplify, and Union](../recipes/coverage-workflow.md)
- [Group Aggregation / Dissolve](../recipes/group-aggregation.md)
