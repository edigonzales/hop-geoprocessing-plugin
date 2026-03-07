package ch.so.agi.hop.geoprocessing.core;

public enum ExecutionMode {
  STREAMING_ROW("Streaming"),
  INDEXED_SECONDARY("Caches secondary layer"),
  BLOCKING_LAYER("Blocking per layer/group");

  private final String label;

  ExecutionMode(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
