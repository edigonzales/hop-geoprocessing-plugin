package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldValueHelper;
import ch.so.agi.hop.geoprocessing.core.OverlayFragment;
import ch.so.agi.hop.geoprocessing.core.OverlayResultRowBuilder;
import ch.so.agi.hop.geoprocessing.core.SpatialIndexBuilder;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.IRowSet;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class LayerOverlay extends BaseTransform<LayerOverlayMeta, LayerOverlayData> {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  public LayerOverlay(
      TransformMeta transformMeta,
      LayerOverlayMeta meta,
      LayerOverlayData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      initialize();
    }

    Object[] primaryRow = getRowFrom(data.primaryRowSet);
    if (primaryRow == null) {
      setOutputDone();
      return false;
    }
    incrementLinesRead();

    FeatureRow primaryFeature =
        FeatureRow.fromRow(data.primaryRowMeta, data.primaryGeometryFieldIndex, primaryRow, getLinesRead());
    List<OverlayFragment> fragments =
        data.executor.execute(meta.getOperationId(), primaryFeature, data.secondaryLayerCache);

    for (OverlayFragment fragment : fragments) {
      putRow(
          data.outputRowMeta,
          data.outputRowBuilder.buildRow(primaryRow, fragment.secondaryFeature(), fragment.geometry()));
    }
    return true;
  }

  private void initialize() throws HopException {
    data.initialized = true;
    String infoTransformName = meta.getInfoTransformName();
    if (infoTransformName.isBlank()) {
      throw new HopTransformException("Secondary layer info stream is not connected");
    }

    data.secondaryRowSet = findInputRowSet(infoTransformName);
    data.primaryRowSet = resolvePrimaryRowSet(infoTransformName);
    if (data.secondaryRowSet == null) {
      throw new HopTransformException("Secondary layer info stream row set was not found: " + infoTransformName);
    }
    if (data.primaryRowSet == null) {
      throw new HopTransformException("Primary input row set was not found");
    }
    data.primaryRowMeta = data.primaryRowSet.getRowMeta();
    data.secondaryRowMeta = data.secondaryRowSet.getRowMeta();
    GeometryFieldSelection primarySelection =
        GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(data.primaryRowMeta, meta.getPrimaryGeometryFieldName());
    GeometryFieldSelection secondarySelection =
        GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(data.secondaryRowMeta, meta.getSecondaryGeometryFieldName());
    logSelectionWarning(primarySelection.warning());
    logSelectionWarning(secondarySelection.warning());
    data.primaryGeometryFieldIndex =
        GEOMETRY_FIELD_SELECTION_RESOLVER.requireFieldIndex(
            data.primaryRowMeta, meta.getPrimaryGeometryFieldName(), "Primary geometry field");
    data.secondaryGeometryFieldIndex =
        GEOMETRY_FIELD_SELECTION_RESOLVER.requireFieldIndex(
            data.secondaryRowMeta, meta.getSecondaryGeometryFieldName(), "Secondary geometry field");

    List<FeatureRow> secondaryFeatures = new ArrayList<>();
    long featureId = 0L;
    Object[] secondaryRow;
    while ((secondaryRow = getRowFrom(data.secondaryRowSet)) != null) {
      secondaryFeatures.add(
          FeatureRow.fromRow(data.secondaryRowMeta, data.secondaryGeometryFieldIndex, secondaryRow, featureId++));
    }
    data.secondaryLayerCache = new SpatialIndexBuilder().build(secondaryFeatures, data.secondaryRowMeta, true);

    OverlayResultRowBuilder outputRowBuilder =
        new OverlayResultRowBuilder(data.primaryRowMeta, data.secondaryRowMeta, meta.getFieldPrefix(), meta.getOutputFieldName());
    data.outputRowBuilder = outputRowBuilder;
    data.outputRowMeta = outputRowBuilder.createOutputRowMeta();
  }

  private void logSelectionWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      logBasic(warning);
    }
  }

  private IRowSet resolvePrimaryRowSet(String infoTransformName) throws HopTransformException {
    for (IRowSet rowSet : getInputRowSets()) {
      if (!rowSet.getOriginTransformName().equalsIgnoreCase(infoTransformName)) {
        return rowSet;
      }
    }
    throw new HopTransformException("No primary input row set found");
  }
}
