package ch.so.agi.hop.geoprocessing.core.reproject;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;

/** Pure validation and output schema compilation; does not initialize GeoTools. */
public final class GeometryReprojectSchema {
  private static final Pattern EPSG = Pattern.compile("(?:EPSG:)?([0-9]+)", Pattern.CASE_INSENSITIVE);

  private GeometryReprojectSchema() {}

  public static void validateSettings(GeometryReprojectSettings settings)
      throws ReprojectConfigurationException {
    if (settings == null) {
      throw new ReprojectConfigurationException("Geometry Reproject settings are required");
    }
    requireName(settings.geometryField(), "Geometry field");
    requireName(settings.outputField(), "Output field");
    if (settings.sourceMode() == null || settings.errorMode() == null) {
      throw new ReprojectConfigurationException("Source CRS mode and error mode are required");
    }
    parseEpsg(settings.targetEpsg(), "Target EPSG");
    if (settings.sourceMode() == ReprojectSourceMode.EXPLICIT_EPSG) {
      parseEpsg(settings.sourceEpsg(), "Source EPSG");
    }
  }

  public static CompiledReprojectionPlan compile(
      IRowMeta input, GeometryReprojectSettings settings, String origin)
      throws ReprojectConfigurationException {
    validateSettings(settings);
    if (input == null) {
      throw new ReprojectConfigurationException("Input row metadata is required");
    }
    int geometryIndex = input.indexOfValue(settings.geometryField());
    if (geometryIndex < 0) {
      throw new ReprojectConfigurationException("Geometry field was not found: " + settings.geometryField());
    }
    IValueMeta sourceMeta = input.getValueMeta(geometryIndex);
    if (sourceMeta.getType() != ValueMetaGeometry.TYPE_GEOMETRY
        || sourceMeta.getStorageType() != IValueMeta.STORAGE_TYPE_NORMAL) {
      throw new ReprojectConfigurationException("Geometry field must use native Geometry storage: " + settings.geometryField());
    }
    IRowMeta output = input.clone();
    int outputIndex = output.indexOfValue(settings.outputField());
    if (outputIndex >= 0 && !settings.overwriteExistingField()) {
      throw new ReprojectConfigurationException("Output field already exists; enable overwrite: " + settings.outputField());
    }
    IValueMeta target = new ValueMetaGeometry(settings.outputField());
    target.setOrigin(origin == null ? "" : origin);
    target.setStorageType(IValueMeta.STORAGE_TYPE_NORMAL);
    if (outputIndex < 0) {
      outputIndex = output.size();
      output.addValueMeta(target);
    } else {
      output.setValueMeta(outputIndex, target);
    }
    return new CompiledReprojectionPlan(
        input, output, settings, geometryIndex, outputIndex,
        settings.sourceMode() == ReprojectSourceMode.EXPLICIT_EPSG
            ? parseEpsg(settings.sourceEpsg(), "Source EPSG") : null,
        parseEpsg(settings.targetEpsg(), "Target EPSG"));
  }

  public static int parseEpsg(String expression, String property)
      throws ReprojectConfigurationException {
    Matcher matcher = EPSG.matcher(expression == null ? "" : expression.trim());
    if (matcher.matches()) {
      try {
        int value = Integer.parseInt(matcher.group(1));
        if (value > 0) {
          return value;
        }
      } catch (NumberFormatException ignored) {
        // The public syntax is limited to a positive Java SRID.
      }
    }
    throw new ReprojectConfigurationException(property + " must be a positive EPSG number or EPSG:<number>: " + expression);
  }

  private static void requireName(String name, String property)
      throws ReprojectConfigurationException {
    if (name == null || name.isBlank()) {
      throw new ReprojectConfigurationException(property + " is required");
    }
  }
}
