package ch.so.agi.hop.geoprocessing.core;

public enum OperationGroup {
  CONSTRUCTIVE("Constructive"),
  EDIT("Edit"),
  MEASURE("Measure"),
  CONVERSION("Conversion"),
  COLLECTION("Collection"),
  PREDICATE("Predicate"),
  OVERLAY("Overlay"),
  AGGREGATE("Aggregate");

  private final String label;

  OperationGroup(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
