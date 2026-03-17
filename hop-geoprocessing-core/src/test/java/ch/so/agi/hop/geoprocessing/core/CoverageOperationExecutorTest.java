package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.coverage.CoverageUnion;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

class CoverageOperationExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final WKTReader wktReader = new WKTReader(geometryFactory);
  private final CoverageOperationExecutor executor = new CoverageOperationExecutor();

  @Test
  void validateTreatsGapWidthAsOptional() throws Exception {
    List<Geometry> coverage =
        List.of(
            wktReader.read("POLYGON ((0 0, 1 0, 1 2, 0 2, 0 0))"),
            wktReader.read("POLYGON ((1.05 0, 2.05 0, 2.05 2, 1.05 2, 1.05 0))"));

    CoverageValidationResult[] withoutGapCheck = executor.validate(coverage, 0.0d);
    CoverageValidationResult[] withGapCheck = executor.validate(coverage, 0.1d);

    assertThat(withoutGapCheck).allMatch(CoverageValidationResult::valid);
    assertThat(withGapCheck).anyMatch(result -> !result.valid());
    assertThat(withGapCheck).anyMatch(result -> result.errorGeometry() != null);
  }

  @Test
  void validateCanDisallowCoverageHolesAcrossMultiplePolygons() throws Exception {
    CoverageValidationResult[] allowedHoles = executor.validate(coverageWithCentralHole(), 0.0d, false);
    CoverageValidationResult[] disallowedHoles = executor.validate(coverageWithCentralHole(), 0.0d, true);

    assertThat(allowedHoles).allMatch(CoverageValidationResult::valid);
    assertThat(disallowedHoles).extracting(CoverageValidationResult::valid).containsExactly(false, false, false, false);
    assertThat(disallowedHoles).allMatch(result -> result.errorGeometry() != null);
  }

  @Test
  void validateCanDisallowDonutPolygons() throws Exception {
    CoverageValidationResult[] results = executor.validate(donutCoverage(), 0.0d, true);

    assertThat(results).hasSize(1);
    assertThat(results[0].valid()).isFalse();
    assertThat(results[0].errorGeometry()).isNotNull();
  }

  @Test
  void validateAllowsDisjointCoverageIslandsWhenCoverageHolesAreDisallowed() throws Exception {
    CoverageValidationResult[] results = executor.validate(disjointCoverageIslands(), 0.0d, true);

    assertThat(results).allMatch(CoverageValidationResult::valid);
  }

  @Test
  void validateKeepsBaseCoverageErrorsWhenHoleConstraintIsEnabled() throws Exception {
    List<Geometry> coverage =
        List.of(
            wktReader.read("POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))"),
            wktReader.read("POLYGON ((1 0, 3 0, 3 2, 1 2, 1 0))"));

    CoverageValidationResult[] withoutHoleConstraint = executor.validate(coverage, 0.0d, false);
    CoverageValidationResult[] withHoleConstraint = executor.validate(coverage, 0.0d, true);

    assertThat(withoutHoleConstraint).extracting(CoverageValidationResult::valid).containsExactly(false, false);
    assertThat(withHoleConstraint).extracting(CoverageValidationResult::valid).containsExactly(false, false);
    assertThat(withHoleConstraint).allMatch(result -> result.errorGeometry() != null);
  }

  @Test
  void simplifyInnerPreservesCoverageUnionAndValidity() throws Exception {
    List<Geometry> coverage = jaggedCoverage();

    Geometry[] simplified = executor.simplify("coverage_simplify_inner", coverage, 0.75d);

    assertThat(simplified).hasSize(2);
    assertThat(CoverageValidator.hasInvalidResult(CoverageValidator.validate(simplified))).isFalse();
    assertThat(
            CoverageUnion.union(simplified)
                .equalsTopo(CoverageUnion.union(coverage.toArray(Geometry[]::new))))
        .isTrue();
  }

  @Test
  void simplifyOuterKeepsCoverageValidAndCardinality() throws Exception {
    Geometry[] simplified = executor.simplify("coverage_simplify_outer", jaggedCoverage(), 0.75d);

    assertThat(simplified).hasSize(2);
    assertThat(CoverageValidator.hasInvalidResult(CoverageValidator.validate(simplified))).isFalse();
  }

  @Test
  void cleanSnapsNearMatchingCoverageIntoValidCoverage() throws Exception {
    List<Geometry> coverage =
        List.of(
            wktReader.read("POLYGON ((1 9, 9 9, 9 4.99, 1 5, 1 9))"),
            wktReader.read("POLYGON ((1 1, 1 5, 9 5, 9 1, 1 1))"));

    Geometry[] cleaned =
        executor.clean(coverage, 0.1d, CoverageMergeStrategy.LONGEST_BORDER, 0.0d);

    assertThat(cleaned).hasSize(2);
    assertThat(CoverageValidator.hasInvalidResult(CoverageValidator.validate(cleaned))).isFalse();
  }

  @Test
  void unionRejectsMixedSrids() throws Exception {
    Geometry left = wktReader.read("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    Geometry right = wktReader.read("POLYGON ((1 0, 2 0, 2 1, 1 1, 1 0))");
    left.setSRID(2056);
    right.setSRID(4326);

    assertThatThrownBy(() -> executor.union(List.of(left, right)))
        .isInstanceOf(HopException.class)
        .hasMessageContaining("matching SRIDs");
  }

  private List<Geometry> jaggedCoverage() throws Exception {
    return List.of(
        wktReader.read("POLYGON ((0 0, 4.8 0, 4.8 1, 5.2 2, 4.8 3, 4.8 4, 0 4, 0 0))"),
        wktReader.read("POLYGON ((4.8 0, 10 0, 10 4, 4.8 4, 4.8 3, 5.2 2, 4.8 1, 4.8 0))"));
  }

  private List<Geometry> coverageWithCentralHole() throws Exception {
    return List.of(
        wktReader.read("POLYGON ((0 0, 1 0, 1 1, 1 2, 1 3, 0 3, 0 0))"),
        wktReader.read("POLYGON ((1 0, 2 0, 2 1, 1 1, 1 0))"),
        wktReader.read("POLYGON ((2 0, 3 0, 3 3, 2 3, 2 2, 2 1, 2 0))"),
        wktReader.read("POLYGON ((1 2, 2 2, 2 3, 1 3, 1 2))"));
  }

  private List<Geometry> donutCoverage() throws Exception {
    return List.of(
        wktReader.read(
            "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 1 3, 3 3, 3 1, 1 1))"));
  }

  private List<Geometry> disjointCoverageIslands() throws Exception {
    return List.of(
        wktReader.read("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))"),
        wktReader.read("POLYGON ((10 0, 11 0, 11 1, 10 1, 10 0))"));
  }
}
