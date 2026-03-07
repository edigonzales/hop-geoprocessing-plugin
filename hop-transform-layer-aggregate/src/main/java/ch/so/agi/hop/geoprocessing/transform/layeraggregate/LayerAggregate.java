package ch.so.agi.hop.geoprocessing.transform.layeraggregate;

import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldValueHelper;
import ch.so.agi.hop.geoprocessing.core.TextListSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Geometry;

public class LayerAggregate extends BaseTransform<LayerAggregateMeta, LayerAggregateData> {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  public LayerAggregate(
      TransformMeta transformMeta,
      LayerAggregateMeta meta,
      LayerAggregateData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      initializeAndAggregate();
    }

    if (data.outputIndex >= data.outputRows.size()) {
      setOutputDone();
      return false;
    }

    putRow(data.outputRowMeta, data.outputRows.get(data.outputIndex++));
    return true;
  }

  private void initializeAndAggregate() throws HopException {
    data.initialized = true;
    Map<GroupKey, GroupAccumulator> groups = new LinkedHashMap<>();

    Object[] row;
    while ((row = getRow()) != null) {
      if (data.outputRowMeta == null) {
        initializeFromInputRowMeta();
      }
      List<Object> groupValues = groupValues(row);
      GroupKey key = new GroupKey(groupValues);
      GroupAccumulator accumulator = groups.computeIfAbsent(key, ignored -> new GroupAccumulator(groupValues));
      Geometry geometry = GeometryFieldValueHelper.readGeometry(getInputRowMeta(), data.geometryFieldIndex, row);
      if (geometry != null) {
        accumulator.geometries().add(geometry);
      }
    }

    for (GroupAccumulator accumulator : groups.values()) {
      Geometry geometry = data.executor.execute(meta.getOperationId(), accumulator.geometries());
      Object[] outputRow = RowDataUtil.allocateRowData(data.outputRowMeta.size());
      for (int index = 0; index < accumulator.groupValues().size(); index++) {
        outputRow[index] = accumulator.groupValues().get(index);
      }
      outputRow[data.outputGeometryFieldIndex] = geometry;
      data.outputRows.add(outputRow);
    }
  }

  private void initializeFromInputRowMeta() throws HopException {
    data.outputRowMeta = (IRowMeta) getInputRowMeta().clone();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    GeometryFieldSelection geometrySelection =
        GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(getInputRowMeta(), meta.getGeometryFieldName());
    logSelectionWarning(geometrySelection.warning());
    data.geometryFieldIndex =
        GEOMETRY_FIELD_SELECTION_RESOLVER.requireFieldIndex(
            getInputRowMeta(), meta.getGeometryFieldName(), "Geometry field");
    List<String> groupFields = TextListSupport.splitCsvOrSemicolon(meta.getGroupFieldNames());
    data.groupFieldIndexes = new int[groupFields.size()];
    for (int index = 0; index < groupFields.size(); index++) {
      data.groupFieldIndexes[index] = getInputRowMeta().indexOfValue(groupFields.get(index));
      if (data.groupFieldIndexes[index] < 0) {
        throw new HopException("Unknown group field: " + groupFields.get(index));
      }
    }
    data.outputGeometryFieldIndex = data.outputRowMeta.indexOfValue(meta.getOutputFieldName());
    if (data.outputGeometryFieldIndex < 0) {
      throw new HopException(
          "Output geometry field was not found on the output row: " + meta.getOutputFieldName());
    }
  }

  private void logSelectionWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      logBasic(warning);
    }
  }

  private GroupKey groupKey(Object[] row) {
    return new GroupKey(groupValues(row));
  }

  private List<Object> groupValues(Object[] row) {
    List<Object> values = new ArrayList<>(data.groupFieldIndexes.length);
    for (int groupFieldIndex : data.groupFieldIndexes) {
      values.add(row[groupFieldIndex]);
    }
    return values;
  }

  private record GroupKey(List<Object> values) {
    private GroupKey {
      values = List.copyOf(values);
    }
  }

  private record GroupAccumulator(List<Object> groupValues, List<Geometry> geometries) {
    private GroupAccumulator(List<Object> groupValues) {
      this(List.copyOf(groupValues), new ArrayList<>());
    }
  }
}
