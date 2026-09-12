package ch.so.agi.hop.geoprocessing.transform.geometryops;

import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldValueHelper;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OverlayMode;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Geometry;

public class GeometryOperation extends BaseTransform<GeometryOperationMeta, GeometryOperationData> {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  private OperationDescriptor descriptor;

  public GeometryOperation(
      TransformMeta transformMeta,
      GeometryOperationMeta meta,
      GeometryOperationData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      initializeFromInputRowMeta();
    }

    Double distance = resolveDistance(row);
    Geometry primaryGeometry =
        "linearize_curves".equals(meta.getOperationId())
            ? readExactGeometry(row)
            : GeometryFieldValueHelper.readGeometry(
                getInputRowMeta(), data.primaryGeometryFieldIndex, row);
    Geometry secondaryGeometry =
        data.secondaryGeometryFieldIndex < 0
            ? null
            : GeometryFieldValueHelper.readGeometry(
                getInputRowMeta(), data.secondaryGeometryFieldIndex, row);

    java.util.List<Geometry> resultGeometries =
        data.executor.execute(
            meta.getOperationId(),
            primaryGeometry,
            secondaryGeometry,
            distance,
            meta.getOverlayMode(),
            data.staticPrecisionScale,
            meta.getBufferSegments(),
            meta.getBufferCapStyle(),
            meta.getBufferJoinStyle(),
            meta.isBufferSingleSided());

    if (resultGeometries.isEmpty()) {
      putGeometryRow(row, null);
      return true;
    }

    for (Geometry resultGeometry : resultGeometries) {
      putGeometryRow(row, resultGeometry);
    }
    return true;
  }

  private Geometry readExactGeometry(Object[] row) throws HopException {
    try {
      return new ch.so.agi.hop.geoprocessing.core.GeometryValueParser()
          .parseGeometry(
              getInputRowMeta().getValueMeta(data.primaryGeometryFieldIndex),
              row[data.primaryGeometryFieldIndex]);
    } catch (Exception e) {
      throw new HopException("Unable to read exact curve geometry", e);
    }
  }

  private void initializeFromInputRowMeta() throws HopTransformException {
    descriptor = meta.descriptor();
    data.outputRowMeta = (IRowMeta) getInputRowMeta().clone();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    GeometryFieldSelection primarySelection =
        GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(
            getInputRowMeta(), meta.getPrimaryGeometryFieldName());
    logSelectionWarning(primarySelection.warning());
    data.primaryGeometryFieldIndex =
        GEOMETRY_FIELD_SELECTION_RESOLVER.requireFieldIndex(
            getInputRowMeta(), meta.getPrimaryGeometryFieldName(), "Primary geometry field");
    data.secondaryGeometryFieldIndex = -1;
    if (descriptor.arity().name().equals("BINARY")) {
      GeometryFieldSelection secondarySelection =
          GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(
              getInputRowMeta(), meta.getSecondaryGeometryFieldName());
      logSelectionWarning(secondarySelection.warning());
      data.secondaryGeometryFieldIndex =
          GEOMETRY_FIELD_SELECTION_RESOLVER.requireFieldIndex(
              getInputRowMeta(), meta.getSecondaryGeometryFieldName(), "Secondary geometry field");
    }
    data.distanceFieldIndex =
        meta.getDistanceMode() == DistanceMode.FIELD
            ? getInputRowMeta().indexOfValue(meta.getDistanceFieldName())
            : -1;
    if (meta.getDistanceMode() == DistanceMode.FIELD && data.distanceFieldIndex < 0) {
      throw new HopTransformException(
          "Distance field was not found on the input row: " + meta.getDistanceFieldName());
    }
    data.staticDistance =
        meta.getDistanceMode() == DistanceMode.STATIC && meta.getDistanceValue() != null
            ? Double.parseDouble(resolve(meta.getDistanceValue()))
            : null;
    data.staticPrecisionScale =
        requiresPrecisionScale() ? Double.parseDouble(resolve(meta.getPrecisionScale())) : null;
    data.outputGeometryFieldIndex =
        meta.getOutputMode() == GeometryOutputMode.REPLACE
            ? data.primaryGeometryFieldIndex
            : data.outputRowMeta.indexOfValue(meta.getOutputFieldName());
    if (data.outputGeometryFieldIndex < 0) {
      throw new HopTransformException(
          "Output geometry field was not found on the output row: " + meta.getOutputFieldName());
    }
  }

  private boolean requiresPrecisionScale() {
    if ("reduce_precision".equals(meta.getOperationId())) {
      return true;
    }
    return descriptor.requires(ch.so.agi.hop.geoprocessing.core.ParameterId.OVERLAY_MODE)
        && meta.getOverlayMode() == OverlayMode.FIXED_PRECISION;
  }

  private void logSelectionWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      logBasic(warning);
    }
  }

  private Double resolveDistance(Object[] row) throws HopException {
    if (!descriptor.requires(ch.so.agi.hop.geoprocessing.core.ParameterId.DISTANCE)) {
      return null;
    }
    if (meta.getDistanceMode() == DistanceMode.FIELD) {
      Double distance = getInputRowMeta().getNumber(row, data.distanceFieldIndex);
      if (distance == null) {
        throw new HopException("Distance field value is null");
      }
      return distance;
    }
    return data.staticDistance;
  }

  private void putGeometryRow(Object[] inputRow, Geometry geometry) throws HopTransformException {
    Object[] outputRow;
    if (meta.getOutputMode() == GeometryOutputMode.REPLACE) {
      outputRow = inputRow.clone();
    } else {
      outputRow = RowDataUtil.resizeArray(inputRow, data.outputRowMeta.size());
    }
    outputRow[data.outputGeometryFieldIndex] = geometry;
    putRow(data.outputRowMeta, outputRow);
  }
}
