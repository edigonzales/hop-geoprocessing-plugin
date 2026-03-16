package ch.so.agi.hop.geoprocessing.core;

public enum CoverageMergeStrategy {
  LONGEST_BORDER(0),
  MAX_AREA(1),
  MIN_AREA(2),
  MIN_INDEX(3);

  private final int cleanerCode;

  CoverageMergeStrategy(int cleanerCode) {
    this.cleanerCode = cleanerCode;
  }

  public int getCleanerCode() {
    return cleanerCode;
  }
}
