package ch.so.agi.hop.geoprocessing.core.converter;

import org.apache.hop.core.exception.HopException;

/** Fatal integration/runtime-capability error; never handled as a per-row data error. */
public class ConverterIntegrationException extends HopException {
  private static final long serialVersionUID = 1L;

  public ConverterIntegrationException(String message) {
    super(message);
  }

  public ConverterIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
