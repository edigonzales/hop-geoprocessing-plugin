package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OperationRegistry;
import ch.so.agi.hop.geoprocessing.core.OverlayResultRowBuilder;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.OverlayMode;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.TransformFamily;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
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
    id = "LAYER_OVERLAY_TRANSFORM",
    name = "Layer Overlay",
    description = "Layer overlay operations against a cached secondary layer",
    image = "ch/so/agi/hop/geoprocessing/transform/layeroverlay/icons/layer-overlay.svg",
    categoryDescription = "Geospatial",
    documentationUrl = "",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"geospatial", "overlay", "intersection", "clip", "identity"})
public class LayerOverlayMeta extends BaseTransformMeta<LayerOverlay, LayerOverlayData> {

  @HopMetadataProperty private String operationId;
  @HopMetadataProperty private String primaryGeometryFieldName;
  @HopMetadataProperty private String secondaryGeometryFieldName;
  @HopMetadataProperty private String outputFieldName;
  @HopMetadataProperty private String fieldPrefix;
  @HopMetadataProperty private OverlayMode overlayMode;
  @HopMetadataProperty private String precisionScale;

  @Override
  public void setDefault() {
    operationId = "intersection";
    primaryGeometryFieldName = "";
    secondaryGeometryFieldName = "";
    outputFieldName = "overlay_geometry";
    fieldPrefix = "b_";
    overlayMode = OverlayMode.STANDARD;
    precisionScale = "";
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    ITransformIOMeta ioMeta = super.getTransformIOMeta(false);
    if (ioMeta == null) {
      TransformIOMeta transformIOMeta = new TransformIOMeta(true, true, true, false, false, false);
      transformIOMeta.addStream(
          new Stream(IStream.StreamType.INFO, null, "Secondary layer", StreamIcon.INFO, null));
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
  }

  @Override
  public void convertIOMetaToTransformNames() {
    for (IStream infoStream : getTransformIOMeta().getInfoStreams()) {
      infoStream.setSubject(infoStream.getTransformName());
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
    if (info != null && info.length > 0 && info[0] != null) {
      OverlayResultRowBuilder builder =
          new OverlayResultRowBuilder(rowMeta, info[0], fieldPrefix, outputFieldName);
      rowMeta.setValueMetaList(builder.createOutputRowMeta().getValueMetaList());
      return;
    }
    rowMeta.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry(outputFieldName));
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
    if (outputFieldName == null || outputFieldName.isBlank()) {
      remarks.add(error("Output geometry field is required.", transformMeta));
      return;
    }
    if (prev.indexOfValue(outputFieldName) >= 0) {
      remarks.add(error("Output geometry field already exists on the primary input.", transformMeta));
      return;
    }
    if (getOverlayMode() == OverlayMode.FIXED_PRECISION && !hasPositiveNumber(precisionScale, variables)) {
      remarks.add(error("A positive precision scale is required in FIXED_PRECISION mode.", transformMeta));
      return;
    }
    remarks.add(ok("Layer overlay configuration looks valid.", transformMeta));
  }

  public List<OperationDescriptor> listOperations() {
    return OperationRegistry.list(TransformFamily.LAYER_OVERLAY);
  }

  public OperationDescriptor descriptor() {
    return OperationRegistry.find(TransformFamily.LAYER_OVERLAY, operationId)
        .orElseThrow(() -> new IllegalStateException("Unknown overlay operation: " + operationId));
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

  private ICheckResult error(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_ERROR, message, transformMeta);
  }

  private ICheckResult warn(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_WARNING, message, transformMeta);
  }

  private ICheckResult ok(String message, TransformMeta transformMeta) {
    return new CheckResult(ICheckResult.TYPE_RESULT_OK, message, transformMeta);
  }

  private boolean hasPositiveNumber(String value, IVariables variables) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      return Double.parseDouble(variables.resolve(value)) > 0.0d;
    } catch (Exception e) {
      return false;
    }
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

  public String getOutputFieldName() {
    return outputFieldName;
  }

  public void setOutputFieldName(String outputFieldName) {
    this.outputFieldName = outputFieldName;
  }

  public String getFieldPrefix() {
    return fieldPrefix;
  }

  public void setFieldPrefix(String fieldPrefix) {
    this.fieldPrefix = fieldPrefix;
  }

  public OverlayMode getOverlayMode() {
    return overlayMode == null ? OverlayMode.STANDARD : overlayMode;
  }

  public void setOverlayMode(OverlayMode overlayMode) {
    this.overlayMode = overlayMode;
  }

  public String getPrecisionScale() {
    return precisionScale;
  }

  public void setPrecisionScale(String precisionScale) {
    this.precisionScale = precisionScale;
  }
}
