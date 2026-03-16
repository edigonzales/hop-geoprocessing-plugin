package ch.so.agi.hop.geoprocessing.core;

import ch.so.agi.hop.geoprocessing.vendor.jts.coverage.CoverageCleaner;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.coverage.CoverageSimplifier;
import org.locationtech.jts.coverage.CoverageUnion;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.TopologyException;

public class CoverageOperationExecutor {

  public CoverageValidationResult[] validate(List<Geometry> geometries, Double gapWidth) throws HopException {
    Geometry[] coverage = CoverageSupport.toCoverageArray(geometries, false);
    double effectiveGapWidth = gapWidth == null ? 0.0d : gapWidth;
    Geometry[] errors =
        effectiveGapWidth > 0.0d
            ? CoverageValidator.validate(coverage, effectiveGapWidth)
            : CoverageValidator.validate(coverage);
    CoverageValidationResult[] results = new CoverageValidationResult[coverage.length];
    for (int index = 0; index < coverage.length; index++) {
      Geometry errorGeometry = CoverageSupport.preserveSrid(GeometryFieldValueHelper.sridOf(coverage[index]), errors[index]);
      results[index] = new CoverageValidationResult(errorGeometry == null, errorGeometry);
    }
    return results;
  }

  public Geometry[] simplify(String operationId, List<Geometry> geometries, Double tolerance)
      throws HopException {
    Geometry[] coverage = CoverageSupport.toCoverageArray(geometries, false);
    ensureValidCoverage(coverage);
    double effectiveTolerance = tolerance == null ? 0.0d : tolerance;
    Geometry[] simplified =
        switch (operationId) {
          case "coverage_simplify" -> CoverageSimplifier.simplify(coverage, effectiveTolerance);
          case "coverage_simplify_inner" ->
              CoverageSimplifier.simplifyInner(coverage, effectiveTolerance);
          case "coverage_simplify_outer" ->
              CoverageSimplifier.simplifyOuter(coverage, effectiveTolerance);
          default -> throw new HopException("Unsupported coverage operation: " + operationId);
        };
    return preserveSrids(coverage, simplified);
  }

  public Geometry[] clean(
      List<Geometry> geometries,
      Double snappingDistance,
      CoverageMergeStrategy mergeStrategy,
      Double gapWidth)
      throws HopException {
    Geometry[] coverage = CoverageSupport.toCoverageArray(geometries, false);
    for (Geometry geometry : coverage) {
      if (!geometry.isValid()) {
        throw new HopException(
            "Coverage Clean requires individually valid polygon geometries before cleaning.");
      }
    }
    Geometry[] cleaned =
        CoverageCleaner.clean(
            coverage,
            snappingDistance == null ? -1.0d : snappingDistance,
            mergeStrategy == null ? CoverageMergeStrategy.LONGEST_BORDER.getCleanerCode() : mergeStrategy.getCleanerCode(),
            gapWidth == null ? 0.0d : gapWidth);
    return preserveSrids(coverage, cleaned);
  }

  public Geometry union(List<Geometry> geometries) throws HopException {
    Geometry[] coverage = CoverageSupport.toCoverageArray(geometries, true);
    if (coverage.length == 0) {
      return null;
    }
    try {
      Integer srid = Arrays.stream(coverage).map(GeometryFieldValueHelper::sridOf).filter(value -> value != null).findFirst().orElse(null);
      return CoverageSupport.preserveSrid(srid, CoverageUnion.union(coverage));
    } catch (TopologyException e) {
      throw new HopException(
          "Coverage Union requires a valid polygonal coverage. Run Coverage Validate first.", e);
    }
  }

  public void ensureValidCoverage(List<Geometry> geometries) throws HopException {
    ensureValidCoverage(CoverageSupport.toCoverageArray(geometries, false));
  }

  private void ensureValidCoverage(Geometry[] coverage) throws HopException {
    Geometry[] errors = CoverageValidator.validate(coverage);
    if (CoverageValidator.hasInvalidResult(errors)) {
      throw new HopException(
          "Coverage operation requires a valid polygonal coverage. Run Coverage Validate first.");
    }
  }

  private Geometry[] preserveSrids(Geometry[] source, Geometry[] result) {
    Geometry[] normalized = new Geometry[result.length];
    for (int index = 0; index < result.length; index++) {
      normalized[index] = GeometryFieldValueHelper.preserveSrid(source[index], result[index]);
    }
    return normalized;
  }
}
