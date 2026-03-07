package ch.so.agi.hop.geoprocessing.core;

import org.locationtech.jts.operation.buffer.BufferParameters;

public enum BufferCapStyle {
  ROUND(BufferParameters.CAP_ROUND),
  FLAT(BufferParameters.CAP_FLAT),
  SQUARE(BufferParameters.CAP_SQUARE);

  private final int jtsValue;

  BufferCapStyle(int jtsValue) {
    this.jtsValue = jtsValue;
  }

  public int getJtsValue() {
    return jtsValue;
  }
}
