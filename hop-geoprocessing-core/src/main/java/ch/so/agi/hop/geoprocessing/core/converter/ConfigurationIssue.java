package ch.so.agi.hop.geoprocessing.core.converter;

import java.util.Objects;

/** A structural configuration problem, shared by metadata validation and the GUI. */
public record ConfigurationIssue(
    Severity severity, String code, String property, String fieldName, String message) {
  public static final String GC_CONFIG_INVALID = "GC_CONFIG_INVALID";
  public static final String GC_MISSING_INPUT_FIELD = "GC_MISSING_INPUT_FIELD";
  public static final String GC_INPUT_FIELD_TYPE = "GC_INPUT_FIELD_TYPE";
  public static final String GC_DUPLICATE_OUTPUT_FIELD = "GC_DUPLICATE_OUTPUT_FIELD";
  public static final String GC_OUTPUT_FIELD_EXISTS = "GC_OUTPUT_FIELD_EXISTS";
  public static final String GC_UNSUPPORTED_CONFIG_VERSION = "GC_UNSUPPORTED_CONFIG_VERSION";
  public static final String GC_UNRESOLVED_VARIABLE = "GC_UNRESOLVED_VARIABLE";
  public static final String GC_ERROR_HOP_REQUIRED = "GC_ERROR_HOP_REQUIRED";

  public ConfigurationIssue {
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
  }

  public enum Severity {
    ERROR,
    WARNING
  }

  public static ConfigurationIssue error(
      String code, String property, String fieldName, String message) {
    return new ConfigurationIssue(Severity.ERROR, code, property, fieldName, message);
  }

  public static ConfigurationIssue warning(
      String code, String property, String fieldName, String message) {
    return new ConfigurationIssue(Severity.WARNING, code, property, fieldName, message);
  }

  public String describe() {
    StringBuilder result = new StringBuilder();
    result.append(code);
    if (property != null && !property.isBlank()) {
      result.append(" [").append(property).append(']');
    }
    if (fieldName != null && !fieldName.isBlank()) {
      result.append(" field=").append(fieldName);
    }
    result.append(": ").append(message);
    return result.toString();
  }
}
