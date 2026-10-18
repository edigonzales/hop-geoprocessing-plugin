package ch.so.agi.hop.geoprocessing.core.converter;

import java.util.List;
import org.apache.hop.core.exception.HopTransformException;

/** Fatal configuration error; never handled as a per-row data error. */
public class ConverterConfigurationException extends HopTransformException {
  private static final long serialVersionUID = 1L;

  private final List<ConfigurationIssue> issues;

  public ConverterConfigurationException(String message) {
    super(message);
    this.issues = List.of();
  }

  public ConverterConfigurationException(String message, Throwable cause) {
    super(message, cause);
    this.issues = List.of();
  }

  public ConverterConfigurationException(List<ConfigurationIssue> issues) {
    super(buildMessage(issues));
    this.issues = List.copyOf(issues);
  }

  public List<ConfigurationIssue> issues() {
    return issues;
  }

  private static String buildMessage(List<ConfigurationIssue> issues) {
    if (issues == null || issues.isEmpty()) {
      return "Invalid Geometry Converter configuration";
    }
    return issues.stream()
        .map(ConfigurationIssue::describe)
        .reduce((left, right) -> left + "; " + right)
        .orElse("Invalid Geometry Converter configuration");
  }
}
