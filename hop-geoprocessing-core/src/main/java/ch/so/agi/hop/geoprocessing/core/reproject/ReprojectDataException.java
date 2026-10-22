package ch.so.agi.hop.geoprocessing.core.reproject;

import org.apache.hop.core.exception.HopException;

/** A row-specific geometry, SRID or coordinate error with a stable error-hop code. */
public class ReprojectDataException extends HopException {
  private static final long serialVersionUID = 1L;
  private final String code;
  private final String fieldName;

  public ReprojectDataException(String code, String message) {
    this(code, null, message, null);
  }

  public ReprojectDataException(String code, String fieldName, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.fieldName = fieldName;
  }

  public String code() {
    return code;
  }

  public String fieldName() {
    return fieldName;
  }
}
