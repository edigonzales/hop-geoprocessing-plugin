package ch.so.agi.hop.geoprocessing.core;

import org.locationtech.jts.operation.buffer.BufferParameters;

public enum BufferJoinStyle {
  ROUND(BufferParameters.JOIN_ROUND),
  BEVEL(BufferParameters.JOIN_BEVEL),
  MITRE(BufferParameters.JOIN_MITRE);

  private final int jtsValue;

  BufferJoinStyle(int jtsValue) {
    this.jtsValue = jtsValue;
  }

  public int getJtsValue() {
    return jtsValue;
  }
}
