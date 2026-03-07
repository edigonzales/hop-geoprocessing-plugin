package ch.so.agi.hop.geoprocessing.transform.geometryops;

import ch.so.agi.hop.geoprocessing.core.BufferCapStyle;
import ch.so.agi.hop.geoprocessing.core.BufferJoinStyle;
import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OperationRegistry;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.TransformFamily;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
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

@Transform(
    id = "GEOMETRY_OPERATION_TRANSFORM",
    name = "Geometry Operation",
    description = "Row-wise geometry editing and constructive operations",
    image = "ch/so/agi/hop/geoprocessing/transform/geometryops/icons/geometry-operation.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "",
    keywords = {"geospatial", "geometry", "buffer", "intersection", "simplify"})
public class GeometryOperationMeta
    extends BaseTransformMeta<GeometryOperation, GeometryOperationData> {

  @HopMetadataProperty private String operationId;
  @HopMetadataProperty private String primaryGeometryFieldName;
  @HopMetadataProperty private String secondaryGeometryFieldName;
  @HopMetadataProperty private DistanceMode distanceMode;
  @HopMetadataProperty private String distanceValue;
  @HopMetadataProperty private String distanceFieldName;
  @HopMetadataProperty private GeometryOutputMode outputMode;
  @HopMetadataProperty private String outputFieldName;
  @HopMetadataProperty private Integer bufferSegments;
  @HopMetadataProperty private BufferCapStyle bufferCapStyle;
  @HopMetadataProperty private BufferJoinStyle bufferJoinStyle;
  @HopMetadataProperty private boolean bufferSingleSided;

  @Override
  public void setDefault() {
    operationId = "buffer";
    primaryGeometryFieldName = "";
    secondaryGeometryFieldName = "";
    distanceMode = DistanceMode.STATIC;
    distanceValue = "1.0";
    distanceFieldName = "";
    outputMode = GeometryOutputMode.APPEND;
    outputFieldName = "geometry_result";
    bufferSegments = 8;
    bufferCapStyle = BufferCapStyle.ROUND;
    bufferJoinStyle = BufferJoinStyle.ROUND;
    bufferSingleSided = false;
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
    if (outputMode == GeometryOutputMode.REPLACE) {
      GeometryFieldSelection selection =
          RowMetaSupport.resolveGeometryField(rowMeta, primaryGeometryFieldName);
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
    OperationDescriptor descriptor = descriptor();
    if (input.length == 0) {
      remarks.add(error("No input received from upstream transforms.", transformMeta));
      return;
    }
    if (prev == null || RowMetaSupport.geometryFieldNames(prev).isEmpty()) {
      remarks.add(error("No geometry field available on the primary input.", transformMeta));
      return;
    }
    GeometryFieldSelection primarySelection =
        RowMetaSupport.resolveGeometryField(prev, primaryGeometryFieldName);
    if (!primarySelection.warning().isBlank()) {
      remarks.add(warn(primarySelection.warning(), transformMeta));
    }
    if (!primarySelection.hasSelection()) {
      remarks.add(error("Primary geometry field is required.", transformMeta));
      return;
    }
    if (descriptor.arity().name().equals("BINARY")) {
      GeometryFieldSelection secondarySelection =
          RowMetaSupport.resolveGeometryField(prev, secondaryGeometryFieldName);
      if (!secondarySelection.warning().isBlank()) {
        remarks.add(warn(secondarySelection.warning(), transformMeta));
      }
      if (!secondarySelection.hasSelection()) {
        remarks.add(error("Secondary geometry field is required for this operation.", transformMeta));
        return;
      }
    }
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
        remarks.add(error("Configured distance field was not found on the input row.", transformMeta));
        return;
      }
    }
    if (outputMode == GeometryOutputMode.APPEND) {
      if (outputFieldName == null || outputFieldName.isBlank()) {
        remarks.add(error("Output field name is required in APPEND mode.", transformMeta));
        return;
      }
      if (prev.indexOfValue(outputFieldName) >= 0) {
        remarks.add(error("Output field already exists on the input row.", transformMeta));
        return;
      }
    }
    remarks.add(ok("Geometry operation configuration looks valid.", transformMeta));
  }

  public List<OperationDescriptor> listOperations() {
    return OperationRegistry.list(TransformFamily.GEOMETRY_OPERATION);
  }

  public OperationDescriptor descriptor() {
    return OperationRegistry.find(TransformFamily.GEOMETRY_OPERATION, operationId)
        .orElseThrow(() -> new IllegalStateException("Unknown geometry operation: " + operationId));
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

  public GeometryOutputMode getOutputMode() {
    return outputMode;
  }

  public void setOutputMode(GeometryOutputMode outputMode) {
    this.outputMode = outputMode;
  }

  public String getOutputFieldName() {
    return outputFieldName;
  }

  public void setOutputFieldName(String outputFieldName) {
    this.outputFieldName = outputFieldName;
  }

  public Integer getBufferSegments() {
    return bufferSegments;
  }

  public void setBufferSegments(Integer bufferSegments) {
    this.bufferSegments = bufferSegments;
  }

  public BufferCapStyle getBufferCapStyle() {
    return bufferCapStyle;
  }

  public void setBufferCapStyle(BufferCapStyle bufferCapStyle) {
    this.bufferCapStyle = bufferCapStyle;
  }

  public BufferJoinStyle getBufferJoinStyle() {
    return bufferJoinStyle;
  }

  public void setBufferJoinStyle(BufferJoinStyle bufferJoinStyle) {
    this.bufferJoinStyle = bufferJoinStyle;
  }

  public boolean isBufferSingleSided() {
    return bufferSingleSided;
  }

  public void setBufferSingleSided(boolean bufferSingleSided) {
    this.bufferSingleSided = bufferSingleSided;
  }
}
