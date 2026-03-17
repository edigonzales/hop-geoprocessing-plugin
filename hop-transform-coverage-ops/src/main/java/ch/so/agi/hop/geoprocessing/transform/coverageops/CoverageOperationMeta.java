package ch.so.agi.hop.geoprocessing.transform.coverageops;

import ch.so.agi.hop.geoprocessing.core.CoverageMergeStrategy;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OperationRegistry;
import ch.so.agi.hop.geoprocessing.core.ParameterId;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.TextListSupport;
import ch.so.agi.hop.geoprocessing.core.TransformFamily;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransformIOMeta;
import org.apache.hop.pipeline.transform.TransformIOMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.stream.IStream;
import org.apache.hop.pipeline.transform.stream.Stream;
import org.apache.hop.pipeline.transform.stream.StreamIcon;

@Transform(
    id = "COVERAGE_OPERATION_TRANSFORM",
    name = "Coverage Operation",
    description = "Blocking coverage validation, simplification, cleaning, and related operations",
    image = "ch/so/agi/hop/geoprocessing/transform/coverageops/icons/coverage-operation.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "",
    keywords = {"geospatial", "coverage", "validate", "simplify", "clean"})
public class CoverageOperationMeta
    extends BaseTransformMeta<CoverageOperation, CoverageOperationData> {

  @HopMetadataProperty private String operationId;
  @HopMetadataProperty private String geometryFieldName;
  @HopMetadataProperty private String groupFieldNames;
  @HopMetadataProperty private String gapWidth;
  @HopMetadataProperty private boolean disallowCoverageHoles;
  @HopMetadataProperty private String distanceValue;
  @HopMetadataProperty private String snappingDistance;
  @HopMetadataProperty private CoverageMergeStrategy mergeStrategy;
  @HopMetadataProperty private GeometryOutputMode outputMode;
  @HopMetadataProperty private String outputFieldName;
  @HopMetadataProperty private String booleanFieldName;
  @HopMetadataProperty private String errorTypeFieldName;

  @Override
  public void setDefault() {
    operationId = "coverage_validate";
    geometryFieldName = "";
    groupFieldNames = "";
    gapWidth = "0.0";
    disallowCoverageHoles = false;
    distanceValue = "1.0";
    snappingDistance = "";
    mergeStrategy = CoverageMergeStrategy.LONGEST_BORDER;
    outputMode = GeometryOutputMode.APPEND;
    outputFieldName = "coverage_error";
    booleanFieldName = "coverage_is_valid";
    errorTypeFieldName = "coverage_error_type";
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    ITransformIOMeta ioMeta = super.getTransformIOMeta(false);
    if (ioMeta == null) {
      TransformIOMeta transformIOMeta = new TransformIOMeta(true, true, true, false, false, false);
      transformIOMeta.addStream(
          new Stream(IStream.StreamType.TARGET, null, "Reject rows", StreamIcon.TARGET, null));
      transformIOMeta.setGeneralTargetDescription("Optional QA/reject target for invalid coverage rows");
      setTransformIOMeta(transformIOMeta);
      ioMeta = transformIOMeta;
    }
    return ioMeta;
  }

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    for (IStream targetStream : getTransformIOMeta().getTargetStreams()) {
      targetStream.setTransformMeta(TransformMeta.findTransform(transforms, targetStream.getSubject()));
    }
  }

  @Override
  public void convertIOMetaToTransformNames() {
    for (IStream targetStream : getTransformIOMeta().getTargetStreams()) {
      targetStream.setSubject(targetStream.getTransformName());
    }
  }

  @Override
  public boolean cleanAfterHopToRemove(TransformMeta fromTransform) {
    for (IStream targetStream : getTransformIOMeta().getTargetStreams()) {
      if (fromTransform != null
          && targetStream.getTransformMeta() != null
          && fromTransform.getName().equals(targetStream.getTransformMeta().getName())) {
        targetStream.setTransformMeta(null);
        targetStream.setSubject(null);
        return true;
      }
    }
    return false;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (isValidateOperation()) {
      rowMeta.addValueMeta(new ValueMetaBoolean(getBooleanFieldName()));
      rowMeta.addValueMeta(new ValueMetaGeometry(getOutputFieldName()));
      rowMeta.addValueMeta(new ValueMetaString(getErrorTypeFieldName()));
      return;
    }
    if (getOutputMode() == GeometryOutputMode.REPLACE) {
      GeometryFieldSelection selection = RowMetaSupport.resolveGeometryField(rowMeta, geometryFieldName);
      int primaryIndex = rowMeta.indexOfValue(selection.selectedField());
      if (primaryIndex >= 0) {
        rowMeta.setValueMeta(primaryIndex, new ValueMetaGeometry(rowMeta.getValueMeta(primaryIndex).getName()));
      }
      return;
    }
    rowMeta.addValueMeta(new ValueMetaGeometry(outputFieldName));
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
    if (input.length == 0) {
      remarks.add(error("No input received from upstream transforms.", transformMeta));
      return;
    }
    if (prev == null || RowMetaSupport.geometryFieldNames(prev).isEmpty()) {
      remarks.add(error("No geometry field available on the input row.", transformMeta));
      return;
    }
    GeometryFieldSelection geometrySelection =
        RowMetaSupport.resolveGeometryField(prev, geometryFieldName);
    if (!geometrySelection.warning().isBlank()) {
      remarks.add(warn(geometrySelection.warning(), transformMeta));
    }
    if (!geometrySelection.hasSelection()) {
      remarks.add(error("Input geometry field is missing or invalid.", transformMeta));
      return;
    }
    for (String groupFieldName : TextListSupport.splitCsvOrSemicolon(groupFieldNames)) {
      if (prev.indexOfValue(groupFieldName) < 0) {
        remarks.add(error("Unknown group field: " + groupFieldName, transformMeta));
        return;
      }
    }
    OperationDescriptor descriptor = descriptor();
    if (descriptor.requires(ParameterId.DISTANCE) && !hasNonNegativeNumber(distanceValue, variables)) {
      remarks.add(error("A non-negative simplification tolerance is required.", transformMeta));
      return;
    }
    if (descriptor.requires(ParameterId.GAP_WIDTH)
        && !hasNonNegativeNumber(defaultText(gapWidth, "0.0"), variables)) {
      remarks.add(error("Gap width must be a non-negative number.", transformMeta));
      return;
    }
    if (descriptor.requires(ParameterId.SNAPPING_DISTANCE)
        && !defaultText(snappingDistance).isBlank()
        && !hasNonNegativeNumber(snappingDistance, variables)) {
      remarks.add(error("Snapping distance must be a non-negative number.", transformMeta));
      return;
    }
    if (descriptor.requires(ParameterId.MERGE_STRATEGY) && getMergeStrategy() == null) {
      remarks.add(error("A merge strategy is required for Coverage Clean.", transformMeta));
      return;
    }
    Set<String> groupFields = new HashSet<>(TextListSupport.splitCsvOrSemicolon(groupFieldNames));
    if (isValidateOperation()) {
      String booleanOutputField = getBooleanFieldName();
      String errorGeometryField = getOutputFieldName();
      String errorTypeField = getErrorTypeFieldName();
      if (booleanOutputField.isBlank()) {
        remarks.add(error("Boolean output field is required.", transformMeta));
        return;
      }
      if (errorGeometryField.isBlank()) {
        remarks.add(error("Error geometry field is required.", transformMeta));
        return;
      }
      if (errorTypeField.isBlank()) {
        remarks.add(error("Error type field is required.", transformMeta));
        return;
      }
      if (prev.indexOfValue(booleanOutputField) >= 0) {
        remarks.add(error("Boolean output field already exists on the input row.", transformMeta));
        return;
      }
      if (prev.indexOfValue(errorGeometryField) >= 0) {
        remarks.add(error("Error geometry field already exists on the input row.", transformMeta));
        return;
      }
      if (prev.indexOfValue(errorTypeField) >= 0) {
        remarks.add(error("Error type field already exists on the input row.", transformMeta));
        return;
      }
      if (booleanOutputField.equalsIgnoreCase(errorGeometryField)
          || booleanOutputField.equalsIgnoreCase(errorTypeField)
          || errorGeometryField.equalsIgnoreCase(errorTypeField)) {
        remarks.add(
            error(
                "Boolean output field, error geometry field, and error type field must be different.",
                transformMeta));
        return;
      }
      if (groupFields.contains(booleanOutputField)
          || groupFields.contains(errorGeometryField)
          || groupFields.contains(errorTypeField)) {
        remarks.add(error("Coverage output fields must not reuse group field names.", transformMeta));
        return;
      }
      remarks.add(ok("Coverage operation configuration looks valid.", transformMeta));
      return;
    }
    if (!getRejectTransformName().isBlank()) {
      remarks.add(error("Reject target stream is only supported for Validate Coverage.", transformMeta));
      return;
    }
    if (getOutputMode() == GeometryOutputMode.APPEND) {
      if (outputFieldName == null || outputFieldName.isBlank()) {
        remarks.add(error("Output geometry field is required in APPEND mode.", transformMeta));
        return;
      }
      if (prev.indexOfValue(outputFieldName) >= 0) {
        remarks.add(error("Output geometry field already exists on the input row.", transformMeta));
        return;
      }
      if (groupFields.contains(outputFieldName)) {
        remarks.add(error("Output geometry field must not be one of the group fields.", transformMeta));
        return;
      }
    }
    remarks.add(ok("Coverage operation configuration looks valid.", transformMeta));
  }

  public List<OperationDescriptor> listOperations() {
    return OperationRegistry.list(TransformFamily.COVERAGE_OPERATION);
  }

  public OperationDescriptor descriptor() {
    return OperationRegistry.find(TransformFamily.COVERAGE_OPERATION, operationId)
        .orElseThrow(() -> new IllegalStateException("Unknown coverage operation: " + operationId));
  }

  public boolean isValidateOperation() {
    return "coverage_validate".equals(operationId);
  }

  public String getRejectTransformName() {
    List<IStream> targetStreams = getTransformIOMeta().getTargetStreams();
    if (targetStreams.isEmpty()) {
      return "";
    }
    IStream targetStream = targetStreams.get(0);
    if (targetStream.getTransformMeta() != null) {
      return targetStream.getTransformMeta().getName();
    }
    return targetStream.getSubject() == null ? "" : targetStream.getSubject();
  }

  private ICheckResult error(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_ERROR, message, transformMeta);
  }

  private ICheckResult warn(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_WARNING, message, transformMeta);
  }

  private ICheckResult ok(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_OK, message, transformMeta);
  }

  private boolean hasNonNegativeNumber(String value, IVariables variables) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      return Double.parseDouble(variables.resolve(value)) >= 0.0d;
    } catch (Exception e) {
      return false;
    }
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public String getGeometryFieldName() {
    return geometryFieldName;
  }

  public void setGeometryFieldName(String geometryFieldName) {
    this.geometryFieldName = geometryFieldName;
  }

  public String getGroupFieldNames() {
    return groupFieldNames;
  }

  public void setGroupFieldNames(String groupFieldNames) {
    this.groupFieldNames = groupFieldNames;
  }

  public String getGapWidth() {
    return gapWidth;
  }

  public void setGapWidth(String gapWidth) {
    this.gapWidth = gapWidth;
  }

  public boolean isDisallowCoverageHoles() {
    return disallowCoverageHoles;
  }

  public void setDisallowCoverageHoles(boolean disallowCoverageHoles) {
    this.disallowCoverageHoles = disallowCoverageHoles;
  }

  public String getDistanceValue() {
    return distanceValue;
  }

  public void setDistanceValue(String distanceValue) {
    this.distanceValue = distanceValue;
  }

  public String getSnappingDistance() {
    return snappingDistance;
  }

  public void setSnappingDistance(String snappingDistance) {
    this.snappingDistance = snappingDistance;
  }

  public CoverageMergeStrategy getMergeStrategy() {
    return mergeStrategy == null ? CoverageMergeStrategy.LONGEST_BORDER : mergeStrategy;
  }

  public void setMergeStrategy(CoverageMergeStrategy mergeStrategy) {
    this.mergeStrategy = mergeStrategy;
  }

  public GeometryOutputMode getOutputMode() {
    return outputMode == null ? GeometryOutputMode.APPEND : outputMode;
  }

  public void setOutputMode(GeometryOutputMode outputMode) {
    this.outputMode = outputMode;
  }

  public String getOutputFieldName() {
    return defaultText(outputFieldName, "coverage_error");
  }

  public void setOutputFieldName(String outputFieldName) {
    this.outputFieldName = outputFieldName;
  }

  public String getBooleanFieldName() {
    return defaultText(booleanFieldName, "coverage_is_valid");
  }

  public void setBooleanFieldName(String booleanFieldName) {
    this.booleanFieldName = booleanFieldName;
  }

  public String getErrorTypeFieldName() {
    return defaultText(errorTypeFieldName, "coverage_error_type");
  }

  public void setErrorTypeFieldName(String errorTypeFieldName) {
    this.errorTypeFieldName = errorTypeFieldName;
  }
}
