package ch.so.agi.hop.geoprocessing.core.converter;

import java.util.regex.Pattern;
import org.apache.hop.core.variables.IVariables;

/** Resolves Hop variables in configured field names exactly once, using Hop's own resolver. */
public final class ConverterFieldNameResolver {
  private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{[^}]+}");

  private ConverterFieldNameResolver() {}

  public static String resolveRequired(String raw, String property, IVariables variables)
      throws ConverterConfigurationException {
    if (raw == null || raw.isBlank()) {
      throw new ConverterConfigurationException(
          java.util.List.of(
              ConfigurationIssue.error(
                  ConfigurationIssue.GC_CONFIG_INVALID,
                  property,
                  null,
                  "A non-empty value is required")));
    }
    return resolve(raw, property, variables, true);
  }

  public static String resolveOptional(String raw, String property, IVariables variables)
      throws ConverterConfigurationException {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return resolve(raw, property, variables, false);
  }

  private static String resolve(
      String raw, String property, IVariables variables, boolean required)
      throws ConverterConfigurationException {
    if (variables == null) {
      throw issue(
          ConfigurationIssue.GC_UNRESOLVED_VARIABLE,
          property,
          "No variable context is available for '" + raw + "'");
    }
    String resolved;
    try {
      resolved = variables.resolve(raw);
    } catch (RuntimeException e) {
      throw new ConverterConfigurationException(
          "Unable to resolve variable in property '" + property + "'", e);
    }
    if (resolved == null || UNRESOLVED.matcher(resolved).find()) {
      throw issue(
          ConfigurationIssue.GC_UNRESOLVED_VARIABLE,
          property,
          "Unresolved variable in '" + raw + "'");
    }
    if (resolved.isBlank()) {
      throw issue(
          ConfigurationIssue.GC_CONFIG_INVALID,
          property,
          required
              ? "Resolved value is empty for required property"
              : "Configured optional value resolved to an empty string");
    }
    return resolved;
  }

  private static ConverterConfigurationException issue(
      String code, String property, String message) {
    return new ConverterConfigurationException(
        java.util.List.of(ConfigurationIssue.error(code, property, null, message)));
  }
}
