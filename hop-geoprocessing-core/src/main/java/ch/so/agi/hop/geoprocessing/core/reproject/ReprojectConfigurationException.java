package ch.so.agi.hop.geoprocessing.core.reproject;

import org.apache.hop.core.exception.HopTransformException;

/** Invalid fixed configuration; never a recoverable row error. */
public class ReprojectConfigurationException extends HopTransformException {
  private static final long serialVersionUID = 1L;

  public ReprojectConfigurationException(String message) {
    super(message);
  }

  public ReprojectConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
