package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;

/** Pure configuration/schema validation shared by metadata, GUI and runtime. */
public final class GeometryConverterValidator {
  private GeometryConverterValidator() {}

  public static List<ConfigurationIssue> validateSettings(GeometryConverterSettings settings) {
    List<ConfigurationIssue> issues = new ArrayList<>();
    if (settings == null) {
      issues.add(
          error(ConfigurationIssue.GC_CONFIG_INVALID, "settings", null, "Settings are missing"));
      return issues;
    }
    if (settings.configurationVersion() != 1) {
      issues.add(
          error(
              ConfigurationIssue.GC_UNSUPPORTED_CONFIG_VERSION,
              "configuration_version",
              null,
              "Unsupported Geometry Converter configuration version: "
                  + settings.configurationVersion()));
    }
    if (settings.inputFormat() == null) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID,
              "input_format",
              null,
              "Input format is required"));
    }
    if (settings.outputFormat() == null) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID,
              "output_format",
              null,
              "Output format is required"));
    }
    if (settings.byteOrder() == null) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID, "byte_order", null, "Byte order is required"));
    }
    if (settings.sridMode() == null) {
      issues.add(
          error(ConfigurationIssue.GC_CONFIG_INVALID, "srid_mode", null, "SRID mode is required"));
    }
    if (settings.errorMode() == null) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID, "error_mode", null, "Error mode is required"));
    }
    if (settings.inputFormat() != null) {
      validateInputSelection(settings, issues);
    }
    if (settings.outputFormat() != null) {
      validateOutputSelection(settings, issues);
    }
    if (settings.usesGeoJson()) {
      if (settings.inputFormat() == ConverterInputFormat.GEOJSON
          && settings.geoJsonInputProfile() == null) {
        issues.add(
            error(
                ConfigurationIssue.GC_CONFIG_INVALID,
                "geojson_input_profile",
                null,
                "GeoJSON input profile is required"));
      }
      if (settings.outputFormat() == ConverterOutputFormat.GEOJSON
          && (settings.geoJsonOutputProfile() == null
              || settings.geoJsonOutputProfile()
                  == com.atolcd.hop.gis.geometry.codec.GeoJsonProfile.AUTO)) {
        issues.add(
            error(
                ConfigurationIssue.GC_CONFIG_INVALID,
                "geojson_output_profile",
                null,
                "Select an explicit GeoJSON output profile"));
      }
    }
    validateSridSettings(settings, issues);
    validateDuplicateOutputNames(settings, issues);
    return List.copyOf(issues);
  }

  public static List<ConfigurationIssue> validateInputSchema(
      GeometryConverterSettings settings, IRowMeta input) {
    List<ConfigurationIssue> issues = new ArrayList<>();
    if (settings == null) {
      issues.add(
          error(ConfigurationIssue.GC_CONFIG_INVALID, "settings", null, "Settings are missing"));
      return List.copyOf(issues);
    }
    if (input == null) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID,
              "input",
              null,
              "Input row metadata is required"));
      return List.copyOf(issues);
    }
    if (settings.inputFormat() == ConverterInputFormat.COORDINATES) {
      validateInputField(
          settings.inputXFieldName(), "input_x_field", true, input, issues, "numeric");
      validateInputField(
          settings.inputYFieldName(), "input_y_field", true, input, issues, "numeric");
      validateInputField(
          settings.inputZFieldName(), "input_z_field", false, input, issues, "numeric");
      validateInputField(
          settings.inputMFieldName(), "input_m_field", false, input, issues, "numeric");
    } else {
      validateInputField(
          settings.inputFieldName(),
          "input_field",
          true,
          input,
          issues,
          inputTypeName(settings.inputFormat()));
    }
    if (settings.outputFormat() != ConverterOutputFormat.COORDINATES) {
      validateOutputCollision(settings, input, settings.outputFieldName(), "output_field", issues);
    } else {
      validateOutputCollision(
          settings, input, settings.outputXFieldName(), "output_x_field", issues);
      validateOutputCollision(
          settings, input, settings.outputYFieldName(), "output_y_field", issues);
      validateOutputCollision(
          settings, input, settings.outputZFieldName(), "output_z_field", issues);
      validateOutputCollision(
          settings, input, settings.outputMFieldName(), "output_m_field", issues);
    }
    if (settings.usesGeoJson()) {
      validateInputField(
          settings.geoJsonInputContextField(),
          "geojson_input_context_field",
          false,
          input,
          issues,
          "string");
      validateOutputCollision(
          settings,
          input,
          settings.geoJsonOutputContextField(),
          "geojson_output_context_field",
          issues);
    }
    return List.copyOf(issues);
  }

  public static void requireValid(GeometryConverterSettings settings, IRowMeta input)
      throws ConverterConfigurationException {
    List<ConfigurationIssue> issues = new ArrayList<>(validateSettings(settings));
    issues.addAll(validateInputSchema(settings, input));
    if (!issues.isEmpty()) {
      throw new ConverterConfigurationException(issues);
    }
  }

  private static void validateInputSelection(
      GeometryConverterSettings settings, List<ConfigurationIssue> issues) {
    if (settings.inputFormat() == ConverterInputFormat.COORDINATES) {
      requireName(settings.inputXFieldName(), "input_x_field", issues);
      requireName(settings.inputYFieldName(), "input_y_field", issues);
      optionalName(settings.inputZFieldName(), "input_z_field", issues);
      optionalName(settings.inputMFieldName(), "input_m_field", issues);
    } else {
      requireName(settings.inputFieldName(), "input_field", issues);
    }
  }

  private static void validateOutputSelection(
      GeometryConverterSettings settings, List<ConfigurationIssue> issues) {
    if (settings.outputFormat() == ConverterOutputFormat.COORDINATES) {
      requireName(settings.outputXFieldName(), "output_x_field", issues);
      requireName(settings.outputYFieldName(), "output_y_field", issues);
      optionalName(settings.outputZFieldName(), "output_z_field", issues);
      optionalName(settings.outputMFieldName(), "output_m_field", issues);
    } else {
      requireName(settings.outputFieldName(), "output_field", issues);
    }
  }

  private static void validateSridSettings(
      GeometryConverterSettings settings, List<ConfigurationIssue> issues) {
    if (settings.sridMode() == null) {
      return;
    }
    switch (settings.sridMode()) {
      case KEEP, CLEAR -> {
        if (settings.srid() != null) {
          issues.add(
              error(
                  ConfigurationIssue.GC_CONFIG_INVALID,
                  "srid_value",
                  null,
                  "An SRID value is inactive for mode " + settings.sridMode()));
        }
      }
      case SET_IF_MISSING, REPLACE -> {
        if (settings.srid() == null || settings.srid() <= 0) {
          issues.add(
              error(
                  ConfigurationIssue.GC_CONFIG_INVALID,
                  "srid_value",
                  null,
                  "A positive SRID is required for mode " + settings.sridMode()));
        }
      }
    }
    if (settings.outputFormat() == ConverterOutputFormat.COORDINATES
        && settings.sridMode() != ConverterSridMode.KEEP) {
      issues.add(
          error(
              ConfigurationIssue.GC_CONFIG_INVALID,
              "srid_mode",
              null,
              "Coordinate output only supports SRID mode KEEP"));
    }
  }

  private static void validateDuplicateOutputNames(
      GeometryConverterSettings settings, List<ConfigurationIssue> issues) {
    if (settings.outputFormat() == null) {
      return;
    }
    Set<String> names = new HashSet<>();
    List<String> active =
        settings.outputFormat() == ConverterOutputFormat.COORDINATES
            ? List.of(
                nullToEmpty(settings.outputXFieldName()),
                nullToEmpty(settings.outputYFieldName()),
                nullToEmpty(settings.outputZFieldName()),
                nullToEmpty(settings.outputMFieldName()))
            : List.of(nullToEmpty(settings.outputFieldName()));
    active = new ArrayList<>(active);
    if (settings.usesGeoJson()) {
      active.add(nullToEmpty(settings.geoJsonOutputContextField()));
    }
    for (String name : active) {
      if (name.isBlank()) {
        continue;
      }
      String key = name.toLowerCase(Locale.ROOT);
      if (!names.add(key)) {
        issues.add(
            error(
                ConfigurationIssue.GC_DUPLICATE_OUTPUT_FIELD,
                "output_field",
                name,
                "The target field name is assigned more than once: " + name));
      }
    }
  }

  private static void validateInputField(
      String fieldName,
      String property,
      boolean required,
      IRowMeta input,
      List<ConfigurationIssue> issues,
      String expectedType) {
    if (fieldName == null || fieldName.isBlank()) {
      if (required) {
        issues.add(
            error(
                ConfigurationIssue.GC_CONFIG_INVALID, property, null, "A field name is required"));
      }
      return;
    }
    int index = input.indexOfValue(fieldName);
    if (index < 0) {
      issues.add(
          error(
              ConfigurationIssue.GC_MISSING_INPUT_FIELD,
              property,
              fieldName,
              "Configured input field was not found: " + fieldName));
      return;
    }
    IValueMeta valueMeta = input.getValueMeta(index);
    if (!matchesType(valueMeta, expectedType)) {
      issues.add(
          error(
              ConfigurationIssue.GC_INPUT_FIELD_TYPE,
              property,
              fieldName,
              "Input field '"
                  + fieldName
                  + "' has type "
                  + valueMeta.getTypeDesc()
                  + ", expected "
                  + expectedType));
    }
  }

  private static void validateOutputCollision(
      GeometryConverterSettings settings,
      IRowMeta input,
      String fieldName,
      String property,
      List<ConfigurationIssue> issues) {
    if (fieldName == null || fieldName.isBlank()) {
      return;
    }
    if (input.indexOfValue(fieldName) >= 0 && !settings.overwriteExistingFields()) {
      issues.add(
          error(
              ConfigurationIssue.GC_OUTPUT_FIELD_EXISTS,
              property,
              fieldName,
              "Target field already exists and overwriteExistingFields is false: " + fieldName));
    }
  }

  private static boolean matchesType(IValueMeta valueMeta, String expectedType) {
    if (valueMeta == null) {
      return false;
    }
    return switch (expectedType) {
      case "geometry" -> valueMeta.getType() == ValueMetaGeometry.TYPE_GEOMETRY;
      case "string" -> valueMeta.getType() == IValueMeta.TYPE_STRING;
      case "binary" -> valueMeta.getType() == IValueMeta.TYPE_BINARY;
      case "numeric" ->
          valueMeta.getType() == IValueMeta.TYPE_NUMBER
              || valueMeta.getType() == IValueMeta.TYPE_INTEGER
              || valueMeta.getType() == IValueMeta.TYPE_BIGNUMBER;
      default -> false;
    };
  }

  private static String inputTypeName(ConverterInputFormat format) {
    return switch (format) {
      case GEOMETRY -> "geometry";
      case WKT_EWKT, WKB_HEX, GEOJSON -> "string";
      case WKB_BYTES -> "binary";
      case COORDINATES -> "numeric";
    };
  }

  private static void requireName(String name, String property, List<ConfigurationIssue> issues) {
    if (name == null || name.isBlank()) {
      issues.add(
          error(ConfigurationIssue.GC_CONFIG_INVALID, property, null, "A field name is required"));
    }
  }

  private static void optionalName(String name, String property, List<ConfigurationIssue> issues) {
    // Optional blank is valid; nothing to check until the schema verifies a configured value.
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static ConfigurationIssue error(
      String code, String property, String fieldName, String message) {
    return ConfigurationIssue.error(code, property, fieldName, message);
  }
}
