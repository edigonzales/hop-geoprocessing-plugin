package ch.so.agi.hop.geoprocessing.core.reproject;

import org.apache.hop.core.exception.HopException;

/** Broken native Geometry/runtime integration; never a recoverable row error. */
public class ReprojectIntegrationException extends HopException {
  private static final long serialVersionUID = 1L;

  public ReprojectIntegrationException(String message) {
    super(message);
  }

  public ReprojectIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
