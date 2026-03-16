# Third-Party Notices

## Vendored JTS Coverage Cleaner

The files under
`hop-geoprocessing-core/src/main/java/ch/so/agi/hop/geoprocessing/vendor/jts/coverage/`
include adapted source derived from the JTS project:

- Project: [JTS Topology Suite](https://github.com/locationtech/jts)
- Upstream sources:
  - [CoverageCleaner.java](https://raw.githubusercontent.com/locationtech/jts/master/modules/core/src/main/java/org/locationtech/jts/coverage/CoverageCleaner.java)
  - [CleanCoverage.java](https://raw.githubusercontent.com/locationtech/jts/master/modules/core/src/main/java/org/locationtech/jts/coverage/CleanCoverage.java)
- Copyright: Martin Davis
- License: Eclipse Public License 2.0 and Eclipse Distribution License v1.0

The vendored copies retain the upstream license headers and were adapted to run against the
published JTS `1.20.0` API used by this repository.
