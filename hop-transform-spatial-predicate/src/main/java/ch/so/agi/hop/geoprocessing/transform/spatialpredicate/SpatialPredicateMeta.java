package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.FieldNameSupport;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OperationRegistry;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateResultMode;
import ch.so.agi.hop.geoprocessing.core.TransformFamily;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
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
    id = "SPATIAL_PREDICATE_TRANSFORM",
    name = "Spatial Predicate",
    description = "Spatial predicates and inner joins against a secondary layer",
    image = "ch/so/agi/hop/geoprocessing/transform/spatialpredicate/icons/spatial-predicate.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "",
    keywords = {"geospatial", "predicate", "intersects", "contains", "join"})
public class SpatialPredicateMeta
    extends BaseTransformMeta<SpatialPredicate, SpatialPredicateData> {

  @HopMetadataProperty private String operationId;
  @HopMetadataProperty private String primaryGeometryFieldName;
  @HopMetadataProperty private String secondaryGeometryFieldName;
  @HopMetadataProperty private DistanceMode distanceMode;
  @HopMetadataProperty private String distanceValue;
  @HopMetadataProperty private String distanceFieldName;
  @HopMetadataProperty private SpatialPredicateResultMode resultMode;
  @HopMetadataProperty private String booleanFieldName;
  @HopMetadataProperty private String fieldPrefix;

  @Override
  public void setDefault() {
    operationId = "intersects";
    primaryGeometryFieldName = "";
    secondaryGeometryFieldName = "";
    distanceMode = DistanceMode.STATIC;
    distanceValue = "1.0";
    distanceFieldName = "";
    resultMode = SpatialPredicateResultMode.BOOLEAN_COLUMN;
    booleanFieldName = "matches";
    fieldPrefix = "b_";
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    ITransformIOMeta ioMeta = super.getTransformIOMeta(false);
    if (ioMeta == null) {
      TransformIOMeta transformIOMeta = new TransformIOMeta(true, true, true, false, false, false);
      transformIOMeta.addStream(
          new Stream(IStream.StreamType.INFO, null, "Secondary layer", StreamIcon.INFO, null));
      transformIOMeta.addStream(
          new Stream(IStream.StreamType.TARGET, null, "Reject rows", StreamIcon.TARGET, null));
      transformIOMeta.setGeneralTargetDescription(
          "Optional QA/reject target for the opposite filter result");
      setTransformIOMeta(transformIOMeta);
      ioMeta = transformIOMeta;
    }
    return ioMeta;
  }

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    for (IStream infoStream : getTransformIOMeta().getInfoStreams()) {
      infoStream.setTransformMeta(TransformMeta.findTransform(transforms, infoStream.getSubject()));
    }
    for (IStream targetStream : getTransformIOMeta().getTargetStreams()) {
      targetStream.setTransformMeta(TransformMeta.findTransform(transforms, targetStream.getSubject()));
    }
  }

  @Override
  public void convertIOMetaToTransformNames() {
    for (IStream infoStream : getTransformIOMeta().getInfoStreams()) {
      infoStream.setSubject(infoStream.getTransformName());
    }
    for (IStream targetStream : getTransformIOMeta().getTargetStreams()) {
      targetStream.setSubject(targetStream.getTransformName());
    }
  }

  @Override
  public boolean cleanAfterHopToRemove(TransformMeta fromTransform) {
    for (IStream infoStream : getTransformIOMeta().getInfoStreams()) {
      if (fromTransform != null
          && infoStream.getTransformMeta() != null
          && fromTransform.getName().equals(infoStream.getTransformMeta().getName())) {
        infoStream.setTransformMeta(null);
        infoStream.setSubject(null);
        return true;
      }
    }
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
    if (resultMode == SpatialPredicateResultMode.BOOLEAN_COLUMN) {
      rowMeta.addValueMeta(new ValueMetaBoolean(booleanFieldName));
      return;
    }
    if (resultMode == SpatialPredicateResultMode.INNER_JOIN && info != null && info.length > 0 && info[0] != null) {
      appendPrefixedFields(rowMeta, info[0], fieldPrefix);
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
    if (input.length == 0) {
      remarks.add(error("No primary input received from upstream transforms.", transformMeta));
      return;
    }
    if (prev == null || RowMetaSupport.geometryFieldNames(prev).isEmpty()) {
      remarks.add(error("No geometry field available on the primary input.", transformMeta));
      return;
    }
    if (info == null || RowMetaSupport.geometryFieldNames(info).isEmpty()) {
      remarks.add(error("No geometry field available on the secondary info stream.", transformMeta));
      return;
    }
    if (getInfoTransformName().isBlank()) {
      remarks.add(error("Connect a secondary layer using an info hop.", transformMeta));
      return;
    }
    GeometryFieldSelection primarySelection =
        RowMetaSupport.resolveGeometryField(prev, primaryGeometryFieldName);
    if (!primarySelection.warning().isBlank()) {
      remarks.add(warn(primarySelection.warning(), transformMeta));
    }
    if (!primarySelection.hasSelection()) {
      remarks.add(error("Primary geometry field is missing or invalid.", transformMeta));
      return;
    }
    GeometryFieldSelection secondarySelection =
        RowMetaSupport.resolveGeometryField(info, secondaryGeometryFieldName);
    if (!secondarySelection.warning().isBlank()) {
      remarks.add(warn(secondarySelection.warning(), transformMeta));
    }
    if (!secondarySelection.hasSelection()) {
      remarks.add(error("Secondary geometry field is missing or invalid.", transformMeta));
      return;
    }
    OperationDescriptor descriptor = descriptor();
    if (descriptor.requires(ch.so.agi.hop.geoprocessing.core.ParameterId.DISTANCE)) {
      if (distanceMode == DistanceMode.STATIC && (distanceValue == null || distanceValue.isBlank())) {
        remarks.add(error("A static distance value is required.", transformMeta));
        return;
      }
      if (distanceMode == DistanceMode.FIELD && (distanceFieldName == null || distanceFieldName.isBlank())) {
        remarks.add(error("A distance field is required when distance mode is FIELD.", transformMeta));
        return;
      }
      if (distanceMode == DistanceMode.FIELD && prev.indexOfValue(distanceFieldName) < 0) {
        remarks.add(error("Configured distance field was not found on the primary input.", transformMeta));
        return;
      }
    }
    if (resultMode == SpatialPredicateResultMode.BOOLEAN_COLUMN
        && (booleanFieldName == null || booleanFieldName.isBlank())) {
      remarks.add(error("Boolean output field is required.", transformMeta));
      return;
    }
    if (resultMode == SpatialPredicateResultMode.BOOLEAN_COLUMN && prev.indexOfValue(booleanFieldName) >= 0) {
      remarks.add(error("Boolean output field already exists on the primary input.", transformMeta));
      return;
    }
    if (!getRejectTransformName().isBlank()
        && resultMode != SpatialPredicateResultMode.KEEP_MATCHED
        && resultMode != SpatialPredicateResultMode.KEEP_UNMATCHED) {
      remarks.add(
          error(
              "Reject target stream is only supported for KEEP_MATCHED and KEEP_UNMATCHED.",
              transformMeta));
      return;
    }
    if (resultMode == SpatialPredicateResultMode.INNER_JOIN
        && ("disjoint".equals(operationId) || "distance_gte".equals(operationId))) {
      remarks.add(error("INNER_JOIN is not supported for DISJOINT or DISTANCE_GTE.", transformMeta));
      return;
    }
    remarks.add(ok("Spatial predicate configuration looks valid.", transformMeta));
  }

  public List<OperationDescriptor> listOperations() {
    return OperationRegistry.list(TransformFamily.SPATIAL_PREDICATE);
  }

  public OperationDescriptor descriptor() {
    return OperationRegistry.find(TransformFamily.SPATIAL_PREDICATE, operationId)
        .orElseThrow(() -> new IllegalStateException("Unknown spatial predicate: " + operationId));
  }

  public String getInfoTransformName() {
    List<IStream> infoStreams = getTransformIOMeta().getInfoStreams();
    if (infoStreams.isEmpty()) {
      return "";
    }
    IStream infoStream = infoStreams.get(0);
    if (infoStream.getTransformMeta() != null) {
      return infoStream.getTransformMeta().getName();
    }
    return infoStream.getSubject() == null ? "" : infoStream.getSubject();
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

  private void appendPrefixedFields(IRowMeta rowMeta, IRowMeta infoRowMeta, String prefix) {
    Set<String> existingNames = FieldNameSupport.toLowerCaseSet(RowMetaSupport.fieldNames(rowMeta));
    String effectivePrefix = prefix == null || prefix.isBlank() ? "b_" : prefix;
    for (int index = 0; index < infoRowMeta.size(); index++) {
      IValueMeta sourceMeta = infoRowMeta.getValueMeta(index);
      IValueMeta targetMeta = (IValueMeta) sourceMeta.clone();
      targetMeta.setName(FieldNameSupport.uniqueName(effectivePrefix + sourceMeta.getName(), existingNames));
      rowMeta.addValueMeta(targetMeta);
    }
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

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public String getPrimaryGeometryFieldName() {
    return primaryGeometryFieldName;
  }

  public void setPrimaryGeometryFieldName(String primaryGeometryFieldName) {
    this.primaryGeometryFieldName = primaryGeometryFieldName;
  }

  public String getSecondaryGeometryFieldName() {
    return secondaryGeometryFieldName;
  }

  public void setSecondaryGeometryFieldName(String secondaryGeometryFieldName) {
    this.secondaryGeometryFieldName = secondaryGeometryFieldName;
  }

  public DistanceMode getDistanceMode() {
    return distanceMode;
  }

  public void setDistanceMode(DistanceMode distanceMode) {
    this.distanceMode = distanceMode;
  }

  public String getDistanceValue() {
    return distanceValue;
  }

  public void setDistanceValue(String distanceValue) {
    this.distanceValue = distanceValue;
  }

  public String getDistanceFieldName() {
    return distanceFieldName;
  }

  public void setDistanceFieldName(String distanceFieldName) {
    this.distanceFieldName = distanceFieldName;
  }

  public SpatialPredicateResultMode getResultMode() {
    return resultMode;
  }

  public void setResultMode(SpatialPredicateResultMode resultMode) {
    this.resultMode = resultMode;
  }

  public String getBooleanFieldName() {
    return booleanFieldName;
  }

  public void setBooleanFieldName(String booleanFieldName) {
    this.booleanFieldName = booleanFieldName;
  }

  public String getFieldPrefix() {
    return fieldPrefix;
  }

  public void setFieldPrefix(String fieldPrefix) {
    this.fieldPrefix = fieldPrefix;
  }
}
