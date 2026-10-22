package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import ch.so.agi.hop.geoprocessing.core.reproject.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Metadata for native XY reprojection; persisted strings retain Hop variable expressions. */
@Transform(
    id = "GEOMETRY_REPROJECT_TRANSFORM",
    name = "Geometry Reproject",
    description = "Reproject native geometry between horizontal EPSG coordinate reference systems",
    image = "ch/so/agi/hop/geoprocessing/transform/geometryreproject/icons/geometry-reproject.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "https://edigonzales.github.io/hop-geoprocessing-plugin/geoprocessing/main/index.html#geometry-reproject",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"geometry", "reproject", "CRS", "EPSG", "coordinates"})
public class GeometryReprojectMeta
    extends BaseTransformMeta<GeometryReproject, GeometryReprojectData> {
  private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{[^}]+}");

  @HopMetadataProperty(key = "configuration_version")
  private int configurationVersion = 1;
  @HopMetadataProperty(key = "geometry_field")
  private String geometryField = "";
  @HopMetadataProperty(key = "source_mode")
  private ReprojectSourceMode sourceMode = ReprojectSourceMode.GEOMETRY_SRID;
  @HopMetadataProperty(key = "source_epsg")
  private String sourceEpsg = "";
  @HopMetadataProperty(key = "target_epsg")
  private String targetEpsg = "";
  @HopMetadataProperty(key = "output_field")
  private String outputField = "geometry_reprojected";
  @HopMetadataProperty(key = "overwrite_existing_field")
  private boolean overwriteExistingField;
  @HopMetadataProperty(key = "error_mode")
  private ReprojectErrorMode errorMode = ReprojectErrorMode.FAIL;

  @Override
  public void setDefault() {
    configurationVersion = 1;
    geometryField = "";
    sourceMode = ReprojectSourceMode.GEOMETRY_SRID;
    sourceEpsg = "";
    targetEpsg = "";
    outputField = "geometry_reprojected";
    overwriteExistingField = false;
    errorMode = ReprojectErrorMode.FAIL;
  }

  public GeometryReprojectSettings toSettings(IVariables variables)
      throws ReprojectConfigurationException {
    if (configurationVersion != 1) {
      throw new ReprojectConfigurationException("Unsupported configuration version: " + configurationVersion);
    }
    List<String> deferred = new ArrayList<>();
    GeometryReprojectSettings settings = new GeometryReprojectSettings(
        resolveForValidation(geometryField, "geometry_field", variables, deferred), sourceMode,
        sourceMode == ReprojectSourceMode.EXPLICIT_EPSG
            ? resolveForValidation(sourceEpsg, "source_epsg", variables, deferred) : null,
        resolveForValidation(targetEpsg, "target_epsg", variables, deferred),
        resolveForValidation(outputField, "output_field", variables, deferred), overwriteExistingField, errorMode);
    GeometryReprojectSchema.validateSettings(settings);
    if (!deferred.isEmpty()) {
      throw new DeferredVariableException(String.join("; ", deferred));
    }
    return settings;
  }

  private String resolveForValidation(String raw, String property, IVariables variables,
      List<String> deferred) throws ReprojectConfigurationException {
    try {
      return resolve(raw, property, variables);
    } catch (DeferredVariableException e) {
      deferred.add(e.getMessage());
      // Validate all remaining fixed settings before allowing runtime-only expressions to be saved.
      return property.endsWith("epsg") ? "4326" : "__deferred_" + property;
    }
  }

  private String resolve(String raw, String property, IVariables variables)
      throws ReprojectConfigurationException {
    if (raw == null || raw.isBlank()) {
      throw new ReprojectConfigurationException(property + ": a non-empty value is required");
    }
    String resolved = variables == null ? raw : variables.resolve(raw);
    if (resolved == null || UNRESOLVED.matcher(resolved).find()) {
      throw new DeferredVariableException(property + ": unresolved variable in '" + raw + "'");
    }
    if (resolved.isBlank()) {
      throw new ReprojectConfigurationException(property + ": resolved value is empty");
    }
    return resolved;
  }

  @Override
  public void getFields(IRowMeta rowMeta, String origin, IRowMeta[] info,
      TransformMeta nextTransform, IVariables variables, IHopMetadataProvider provider)
      throws HopTransformException {
    try {
      var plan = GeometryReprojectSchema.compile(rowMeta, toSettings(variables), origin);
      rowMeta.setValueMetaList(new ArrayList<>(plan.outputRowMeta().getValueMetaList()));
    } catch (ReprojectConfigurationException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
  }

  @Override
  public void check(List<ICheckResult> remarks, PipelineMeta pipelineMeta,
      TransformMeta transformMeta, IRowMeta prev, String[] input, String[] output,
      IRowMeta info, IVariables variables, IHopMetadataProvider provider) {
    try {
      GeometryReprojectSettings settings = toSettings(variables);
      GeometryReprojectSchema.compile(prev, settings, transformMeta == null ? null : transformMeta.getName());
      if (settings.errorMode() == ReprojectErrorMode.ROUTE_TO_ERROR && !hasUsableErrorHop(transformMeta)) {
        throw new ReprojectConfigurationException("ROUTE_TO_ERROR requires an enabled Hop error connection with a target transform");
      }
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_OK,
          "Geometry Reproject configuration is valid", transformMeta));
    } catch (ReprojectConfigurationException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
    }
  }

  static boolean hasUsableErrorHop(TransformMeta transformMeta) {
    if (transformMeta == null || !transformMeta.isDoingErrorHandling()) { return false; }
    PipelineMeta pipeline = transformMeta.getParentPipelineMeta();
    if (pipeline == null) { return false; }
    var hop = pipeline.findPipelineHop(transformMeta,
        transformMeta.getTransformErrorMeta().getTargetTransform());
    return hop != null && hop.isEnabled();
  }

  @Override
  public boolean supportsErrorHandling() { return true; }

  void copyFrom(GeometryReprojectMeta source) {
    configurationVersion = source.configurationVersion;
    geometryField = source.geometryField;
    sourceMode = source.sourceMode;
    sourceEpsg = source.sourceEpsg;
    targetEpsg = source.targetEpsg;
    outputField = source.outputField;
    overwriteExistingField = source.overwriteExistingField;
    errorMode = source.errorMode;
  }

  public int getConfigurationVersion() { return configurationVersion; }
  public void setConfigurationVersion(int value) { configurationVersion = value; }
  public String getGeometryField() { return geometryField; }
  public void setGeometryField(String value) { geometryField = value; }
  public ReprojectSourceMode getSourceMode() { return sourceMode; }
  public void setSourceMode(ReprojectSourceMode value) { sourceMode = value; }
  public String getSourceEpsg() { return sourceEpsg; }
  public void setSourceEpsg(String value) { sourceEpsg = value; }
  public String getTargetEpsg() { return targetEpsg; }
  public void setTargetEpsg(String value) { targetEpsg = value; }
  public String getOutputField() { return outputField; }
  public void setOutputField(String value) { outputField = value; }
  public boolean isOverwriteExistingField() { return overwriteExistingField; }
  public void setOverwriteExistingField(boolean value) { overwriteExistingField = value; }
  public ReprojectErrorMode getErrorMode() { return errorMode; }
  public void setErrorMode(ReprojectErrorMode value) { errorMode = value; }

  static class DeferredVariableException extends ReprojectConfigurationException {
    DeferredVariableException(String message) { super(message); }
  }
}
