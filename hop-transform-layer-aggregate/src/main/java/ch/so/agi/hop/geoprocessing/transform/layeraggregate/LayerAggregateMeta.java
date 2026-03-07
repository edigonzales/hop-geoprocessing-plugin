package ch.so.agi.hop.geoprocessing.transform.layeraggregate;

import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OperationRegistry;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
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
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "LAYER_AGGREGATE_TRANSFORM",
    name = "Layer Aggregate",
    description = "Group-wise geometry aggregation operations",
    image = "ch/so/agi/hop/geoprocessing/transform/layeraggregate/icons/layer-aggregate.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "",
    keywords = {"geospatial", "aggregate", "dissolve", "collect", "union"})
public class LayerAggregateMeta extends BaseTransformMeta<LayerAggregate, LayerAggregateData> {

  @HopMetadataProperty private String operationId;
  @HopMetadataProperty private String geometryFieldName;
  @HopMetadataProperty private String groupFieldNames;
  @HopMetadataProperty private String outputFieldName;

  @Override
  public void setDefault() {
    operationId = "dissolve";
    geometryFieldName = "";
    groupFieldNames = "";
    outputFieldName = "aggregated_geometry";
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    Set<String> groupFields = new HashSet<>(TextListSupport.splitCsvOrSemicolon(groupFieldNames));
    for (int index = rowMeta.size() - 1; index >= 0; index--) {
      if (!groupFields.contains(rowMeta.getValueMeta(index).getName())) {
        rowMeta.removeValueMeta(index);
      }
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
    if (outputFieldName == null || outputFieldName.isBlank()) {
      remarks.add(error("Output geometry field is required.", transformMeta));
      return;
    }
    if (TextListSupport.splitCsvOrSemicolon(groupFieldNames).contains(outputFieldName)) {
      remarks.add(error("Output geometry field must not be one of the group fields.", transformMeta));
      return;
    }
    remarks.add(ok("Layer aggregate configuration looks valid.", transformMeta));
  }

  public List<OperationDescriptor> listOperations() {
    return OperationRegistry.list(TransformFamily.LAYER_AGGREGATE);
  }

  public OperationDescriptor descriptor() {
    return OperationRegistry.find(TransformFamily.LAYER_AGGREGATE, operationId)
        .orElseThrow(() -> new IllegalStateException("Unknown layer aggregate: " + operationId));
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

  public String getOutputFieldName() {
    return outputFieldName;
  }

  public void setOutputFieldName(String outputFieldName) {
    this.outputFieldName = outputFieldName;
  }
}
