package ch.so.agi.hop.geoprocessing.core;

public enum CoverageValidationErrorType {
  COVERAGE_INVALID,
  FORBIDDEN_HOLE,
  MULTIPLE;

  public String getCode() {
    return name();
  }
}
