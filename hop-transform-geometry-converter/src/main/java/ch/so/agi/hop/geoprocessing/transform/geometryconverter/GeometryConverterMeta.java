package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import ch.so.agi.hop.geoprocessing.core.converter.ConfigurationIssue;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterConfigurationException;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterFieldNameResolver;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSchema;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSettings;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterValidator;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import java.util.ArrayList;
import java.util.List;
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
import org.apache.hop.pipeline.transform.TransformErrorMeta;

@Transform(
    id = "GEOMETRY_CONVERTER_TRANSFORM",
    name = "Geometry Converter",
    description = "Convert geometry representations and point coordinate fields",
    image = "ch/so/agi/hop/geoprocessing/transform/geometryconverter/icons/geometry-converter.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "https://edigonzales.github.io/hop-geoprocessing-plugin/geoprocessing/main/index.html#geometry-converter",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"geometry", "WKT", "WKB", "EWKT", "EWKB", "coordinates", "point"})
public class GeometryConverterMeta
    extends BaseTransformMeta<GeometryConverter, GeometryConverterData> {

  @HopMetadataProperty(key = "configuration_version")
  private int configurationVersion = 1;

  @HopMetadataProperty(key = "input_format")
  private ConverterInputFormat inputFormat = ConverterInputFormat.WKT_EWKT;

  @HopMetadataProperty(key = "input_field")
  private String inputFieldName = "";

  @HopMetadataProperty(key = "input_x_field")
  private String inputXFieldName = "";

  @HopMetadataProperty(key = "input_y_field")
  private String inputYFieldName = "";

  @HopMetadataProperty(key = "input_z_field")
  private String inputZFieldName = "";

  @HopMetadataProperty(key = "input_m_field")
  private String inputMFieldName = "";

  @HopMetadataProperty(key = "output_format")
  private ConverterOutputFormat outputFormat = ConverterOutputFormat.GEOMETRY;

  @HopMetadataProperty(key = "output_field")
  private String outputFieldName = "geometry";

  @HopMetadataProperty(key = "output_x_field")
  private String outputXFieldName = "x";

  @HopMetadataProperty(key = "output_y_field")
  private String outputYFieldName = "y";

  @HopMetadataProperty(key = "output_z_field")
  private String outputZFieldName = "";

  @HopMetadataProperty(key = "output_m_field")
  private String outputMFieldName = "";

  @HopMetadataProperty(key = "byte_order")
  private GeometryByteOrder byteOrder = GeometryByteOrder.BIG_ENDIAN;

  @HopMetadataProperty(key = "srid_mode")
  private ConverterSridMode sridMode = ConverterSridMode.KEEP;

  @HopMetadataProperty(key = "srid_value")
  private String sridValue = "";

  @HopMetadataProperty(key = "overwrite_existing_fields")
  private boolean overwriteExistingFields;

  @HopMetadataProperty(key = "error_mode")
  private ConverterErrorMode errorMode = ConverterErrorMode.FAIL;

  @Override
  public void setDefault() {
    configurationVersion = 1;
    inputFormat = ConverterInputFormat.WKT_EWKT;
    inputFieldName = "";
    inputXFieldName = "";
    inputYFieldName = "";
    inputZFieldName = "";
    inputMFieldName = "";
    outputFormat = ConverterOutputFormat.GEOMETRY;
    outputFieldName = "geometry";
    outputXFieldName = "x";
    outputYFieldName = "y";
    outputZFieldName = "";
    outputMFieldName = "";
    byteOrder = GeometryByteOrder.BIG_ENDIAN;
    sridMode = ConverterSridMode.KEEP;
    sridValue = "";
    overwriteExistingFields = false;
    errorMode = ConverterErrorMode.FAIL;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    try {
      GeometryConverterSettings settings = toSettings(variables);
      var plan = GeometryConverterSchema.compile(rowMeta, settings, origin);
      rowMeta.setValueMetaList(new ArrayList<>(plan.outputRowMeta().getValueMetaList()));
    } catch (ConverterConfigurationException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    GeometryConverterSettings settings = null;
    List<ConfigurationIssue> issues = new ArrayList<>();
    try {
      settings = toSettings(variables);
    } catch (ConverterConfigurationException e) {
      issues.addAll(e.issues().isEmpty()
          ? List.of(ConfigurationIssue.error(
              ConfigurationIssue.GC_CONFIG_INVALID, "settings", null, e.getMessage()))
          : e.issues());
    }
    if (prev == null || prev.isEmpty()) {
      issues.add(
          ConfigurationIssue.error(
              ConfigurationIssue.GC_MISSING_INPUT_FIELD,
              "input",
              null,
              "No upstream fields are available for schema validation"));
    } else if (settings != null) {
      issues.addAll(GeometryConverterValidator.validateInputSchema(settings, prev));
    }
    if (!hasUsableErrorHop(transformMeta)) {
      issues.add(
          ConfigurationIssue.error(
              ConfigurationIssue.GC_ERROR_HOP_REQUIRED,
              "error_mode",
              null,
              "Geometry Converter requires an enabled Hop error connection with a target transform"));
    } else if (settings != null && settings.errorMode() != ConverterErrorMode.ROUTE_TO_ERROR) {
      issues.add(
          ConfigurationIssue.warning(
              ConfigurationIssue.GC_ERROR_HOP_REQUIRED,
              "error_mode",
              null,
              "An error connection is configured but the current error mode does not use it"));
    }
    for (ConfigurationIssue issue : issues) {
      remarks.add(
          issue.severity() == ConfigurationIssue.Severity.ERROR
              ? new CheckResult(ICheckResult.TYPE_RESULT_ERROR, issue.describe(), transformMeta)
              : new CheckResult(ICheckResult.TYPE_RESULT_WARNING, issue.describe(), transformMeta));
    }
    if (issues.stream().noneMatch(issue -> issue.severity() == ConfigurationIssue.Severity.ERROR)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              "Geometry Converter configuration is valid",
              transformMeta));
    }
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  public GeometryConverterSettings toSettings(IVariables variables)
      throws ConverterConfigurationException {
    String inputField = null;
    String inputX = null;
    String inputY = null;
    String inputZ = null;
    String inputM = null;
    if (inputFormat == ConverterInputFormat.COORDINATES) {
      inputX = ConverterFieldNameResolver.resolveRequired(inputXFieldName, "input_x_field", variables);
      inputY = ConverterFieldNameResolver.resolveRequired(inputYFieldName, "input_y_field", variables);
      inputZ = ConverterFieldNameResolver.resolveOptional(inputZFieldName, "input_z_field", variables);
      inputM = ConverterFieldNameResolver.resolveOptional(inputMFieldName, "input_m_field", variables);
    } else {
      inputField =
          ConverterFieldNameResolver.resolveRequired(inputFieldName, "input_field", variables);
    }

    String outputField = null;
    String outputX = null;
    String outputY = null;
    String outputZ = null;
    String outputM = null;
    if (outputFormat == ConverterOutputFormat.COORDINATES) {
      outputX =
          ConverterFieldNameResolver.resolveRequired(outputXFieldName, "output_x_field", variables);
      outputY =
          ConverterFieldNameResolver.resolveRequired(outputYFieldName, "output_y_field", variables);
      outputZ =
          ConverterFieldNameResolver.resolveOptional(outputZFieldName, "output_z_field", variables);
      outputM =
          ConverterFieldNameResolver.resolveOptional(outputMFieldName, "output_m_field", variables);
    } else {
      outputField =
          ConverterFieldNameResolver.resolveRequired(outputFieldName, "output_field", variables);
    }

    Integer srid = null;
    if (sridMode == ConverterSridMode.SET_IF_MISSING || sridMode == ConverterSridMode.REPLACE) {
      String resolved = ConverterFieldNameResolver.resolveRequired(sridValue, "srid_value", variables);
      srid = parseSrid(resolved);
    }

    GeometryConverterSettings settings =
        new GeometryConverterSettings(
            configurationVersion,
            inputFormat,
            inputField,
            inputX,
            inputY,
            inputZ,
            inputM,
            outputFormat,
            outputField,
            outputX,
            outputY,
            outputZ,
            outputM,
            byteOrder,
            sridMode,
            srid,
            overwriteExistingFields,
            errorMode);
    List<ConfigurationIssue> issues = GeometryConverterValidator.validateSettings(settings);
    if (!issues.isEmpty()) {
      throw new ConverterConfigurationException(issues);
    }
    return settings;
  }

  private Integer parseSrid(String value) throws ConverterConfigurationException {
    String text = value == null ? "" : value.trim();
    if (!text.matches("[0-9]+")) {
      throw configurationError(
          "srid_value", "SRID must be a non-negative decimal integer: " + value);
    }
    try {
      long parsed = Long.parseLong(text);
      if (parsed > Integer.MAX_VALUE) {
        throw configurationError("srid_value", "SRID exceeds Integer.MAX_VALUE: " + value);
      }
      if (parsed <= 0) {
        throw configurationError("srid_value", "SRID must be positive: " + value);
      }
      return (int) parsed;
    } catch (NumberFormatException e) {
      throw new ConverterConfigurationException(
          "SRID is not a valid integer: " + value, e);
    }
  }

  private ConverterConfigurationException configurationError(String property, String message) {
    return new ConverterConfigurationException(
        List.of(ConfigurationIssue.error(
            ConfigurationIssue.GC_CONFIG_INVALID, property, null, message)));
  }

  private boolean hasUsableErrorHop(TransformMeta transformMeta) {
    if (transformMeta == null) {
      return false;
    }
    TransformErrorMeta errorMeta = transformMeta.getTransformErrorMeta();
    return errorMeta != null
        && errorMeta.isEnabled()
        && errorMeta.getTargetTransform() != null;
  }


  /** Copies all persisted properties from another meta instance without sharing the object. */
  void copyFrom(GeometryConverterMeta source) {
    setConfigurationVersion(source.getConfigurationVersion());
    setInputFormat(source.getInputFormat());
    setInputFieldName(source.getInputFieldName());
    setInputXFieldName(source.getInputXFieldName());
    setInputYFieldName(source.getInputYFieldName());
    setInputZFieldName(source.getInputZFieldName());
    setInputMFieldName(source.getInputMFieldName());
    setOutputFormat(source.getOutputFormat());
    setOutputFieldName(source.getOutputFieldName());
    setOutputXFieldName(source.getOutputXFieldName());
    setOutputYFieldName(source.getOutputYFieldName());
    setOutputZFieldName(source.getOutputZFieldName());
    setOutputMFieldName(source.getOutputMFieldName());
    setByteOrder(source.getByteOrder());
    setSridMode(source.getSridMode());
    setSridValue(source.getSridValue());
    setOverwriteExistingFields(source.isOverwriteExistingFields());
    setErrorMode(source.getErrorMode());
  }

  public int getConfigurationVersion() {
    return configurationVersion;
  }

  public void setConfigurationVersion(int configurationVersion) {
    this.configurationVersion = configurationVersion;
  }

  public ConverterInputFormat getInputFormat() {
    return inputFormat;
  }

  public void setInputFormat(ConverterInputFormat inputFormat) {
    this.inputFormat = inputFormat;
  }

  public String getInputFieldName() {
    return inputFieldName;
  }

  public void setInputFieldName(String inputFieldName) {
    this.inputFieldName = inputFieldName;
  }

  public String getInputXFieldName() {
    return inputXFieldName;
  }

  public void setInputXFieldName(String inputXFieldName) {
    this.inputXFieldName = inputXFieldName;
  }

  public String getInputYFieldName() {
    return inputYFieldName;
  }

  public void setInputYFieldName(String inputYFieldName) {
    this.inputYFieldName = inputYFieldName;
  }

  public String getInputZFieldName() {
    return inputZFieldName;
  }

  public void setInputZFieldName(String inputZFieldName) {
    this.inputZFieldName = inputZFieldName;
  }

  public String getInputMFieldName() {
    return inputMFieldName;
  }

  public void setInputMFieldName(String inputMFieldName) {
    this.inputMFieldName = inputMFieldName;
  }

  public ConverterOutputFormat getOutputFormat() {
    return outputFormat;
  }

  public void setOutputFormat(ConverterOutputFormat outputFormat) {
    this.outputFormat = outputFormat;
  }

  public String getOutputFieldName() {
    return outputFieldName;
  }

  public void setOutputFieldName(String outputFieldName) {
    this.outputFieldName = outputFieldName;
  }

  public String getOutputXFieldName() {
    return outputXFieldName;
  }

  public void setOutputXFieldName(String outputXFieldName) {
    this.outputXFieldName = outputXFieldName;
  }

  public String getOutputYFieldName() {
    return outputYFieldName;
  }

  public void setOutputYFieldName(String outputYFieldName) {
    this.outputYFieldName = outputYFieldName;
  }

  public String getOutputZFieldName() {
    return outputZFieldName;
  }

  public void setOutputZFieldName(String outputZFieldName) {
    this.outputZFieldName = outputZFieldName;
  }

  public String getOutputMFieldName() {
    return outputMFieldName;
  }

  public void setOutputMFieldName(String outputMFieldName) {
    this.outputMFieldName = outputMFieldName;
  }

  public GeometryByteOrder getByteOrder() {
    return byteOrder;
  }

  public void setByteOrder(GeometryByteOrder byteOrder) {
    this.byteOrder = byteOrder;
  }

  public ConverterSridMode getSridMode() {
    return sridMode;
  }

  public void setSridMode(ConverterSridMode sridMode) {
    this.sridMode = sridMode;
  }

  public String getSridValue() {
    return sridValue;
  }

  public void setSridValue(String sridValue) {
    this.sridValue = sridValue;
  }

  public boolean isOverwriteExistingFields() {
    return overwriteExistingFields;
  }

  public void setOverwriteExistingFields(boolean overwriteExistingFields) {
    this.overwriteExistingFields = overwriteExistingFields;
  }

  public ConverterErrorMode getErrorMode() {
    return errorMode;
  }

  public void setErrorMode(ConverterErrorMode errorMode) {
    this.errorMode = errorMode;
  }
}
