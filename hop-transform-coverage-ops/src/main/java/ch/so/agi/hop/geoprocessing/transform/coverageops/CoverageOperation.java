package ch.so.agi.hop.geoprocessing.transform.coverageops;

import ch.so.agi.hop.geoprocessing.core.CoverageValidationResult;
import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.TextListSupport;
import java.util.ArrayList;
import java.util.Collections;
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

public class CoverageOperation extends BaseTransform<CoverageOperationMeta, CoverageOperationData> {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  public CoverageOperation(
      TransformMeta transformMeta,
      CoverageOperationMeta meta,
      CoverageOperationData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      initializeAndProcessAllRows();
    }

    if (data.outputIndex >= data.outputRows.size()) {
      setOutputDone();
      return false;
    }

    putRow(data.outputRowMeta, data.outputRows.get(data.outputIndex++));
    return true;
  }

  private void initializeAndProcessAllRows() throws HopException {
    data.initialized = true;
    Map<GroupKey, List<FeatureRow>> groups = new LinkedHashMap<>();

    long rowIndex = 0L;
    Object[] row;
    while ((row = getRow()) != null) {
      if (data.outputRowMeta == null) {
        initializeFromInputRowMeta();
      }
      FeatureRow feature = FeatureRow.fromRow(getInputRowMeta(), data.geometryFieldIndex, row, rowIndex++);
      requireCoverageGeometry(feature);
      groups.computeIfAbsent(new GroupKey(groupValues(feature.rowData())), ignored -> new ArrayList<>())
          .add(feature);
    }

    if (data.outputRowMeta == null) {
      return;
    }

    List<Object[]> orderedRows = new ArrayList<>(Collections.nCopies(Math.toIntExact(rowIndex), null));
    for (List<FeatureRow> groupFeatures : groups.values()) {
      processGroup(groupFeatures, orderedRows);
    }
    for (Object[] outputRow : orderedRows) {
      if (outputRow != null) {
        data.outputRows.add(outputRow);
      }
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
    data.booleanFieldIndex =
        meta.isValidateOperation() ? data.outputRowMeta.indexOfValue(meta.getBooleanFieldName()) : -1;
    data.outputGeometryFieldIndex =
        meta.isValidateOperation() || meta.getOutputMode() == GeometryOutputMode.APPEND
            ? data.outputRowMeta.indexOfValue(meta.getOutputFieldName())
            : data.geometryFieldIndex;
    if (meta.isValidateOperation() && data.booleanFieldIndex < 0) {
      throw new HopException("Boolean output field was not found on the output row.");
    }
    if (data.outputGeometryFieldIndex < 0) {
      throw new HopException(
          "Coverage output field was not found on the output row: " + meta.getOutputFieldName());
    }
    data.staticDistance =
        meta.descriptor().requires(ch.so.agi.hop.geoprocessing.core.ParameterId.DISTANCE)
            ? Double.parseDouble(resolve(meta.getDistanceValue()))
            : null;
    data.staticGapWidth =
        meta.descriptor().requires(ch.so.agi.hop.geoprocessing.core.ParameterId.GAP_WIDTH)
            ? Double.parseDouble(resolve(defaultText(meta.getGapWidth(), "0.0")))
            : null;
    data.staticSnappingDistance =
        meta.descriptor().requires(ch.so.agi.hop.geoprocessing.core.ParameterId.SNAPPING_DISTANCE)
                && !defaultText(meta.getSnappingDistance()).isBlank()
            ? Double.parseDouble(resolve(meta.getSnappingDistance()))
            : null;
    data.mergeStrategy = meta.getMergeStrategy();
  }

  private void processGroup(List<FeatureRow> groupFeatures, List<Object[]> orderedRows)
      throws HopException {
    List<Geometry> geometries = groupFeatures.stream().map(FeatureRow::geometry).toList();
    if (meta.isValidateOperation()) {
      CoverageValidationResult[] results = data.executor.validate(geometries, data.staticGapWidth);
      for (int index = 0; index < groupFeatures.size(); index++) {
        FeatureRow feature = groupFeatures.get(index);
        CoverageValidationResult result = results[index];
        Object[] outputRow = RowDataUtil.resizeArray(feature.rowData(), data.outputRowMeta.size());
        outputRow[data.booleanFieldIndex] = result.valid();
        outputRow[data.outputGeometryFieldIndex] = result.errorGeometry();
        orderedRows.set(Math.toIntExact(feature.sourceId()), outputRow);
      }
      return;
    }

    Geometry[] resultGeometries =
        "coverage_clean".equals(meta.getOperationId())
            ? data.executor.clean(geometries, data.staticSnappingDistance, data.mergeStrategy, data.staticGapWidth)
            : data.executor.simplify(meta.getOperationId(), geometries, data.staticDistance);
    for (int index = 0; index < groupFeatures.size(); index++) {
      FeatureRow feature = groupFeatures.get(index);
      orderedRows.set(
          Math.toIntExact(feature.sourceId()), buildGeometryRow(feature.rowData(), resultGeometries[index]));
    }
  }

  private Object[] buildGeometryRow(Object[] sourceRow, Geometry geometry) {
    Object[] outputRow =
        meta.getOutputMode() == GeometryOutputMode.REPLACE
            ? sourceRow.clone()
            : RowDataUtil.resizeArray(sourceRow, data.outputRowMeta.size());
    outputRow[data.outputGeometryFieldIndex] = geometry;
    return outputRow;
  }

  private void requireCoverageGeometry(FeatureRow feature) throws HopException {
    if (feature.geometry() == null) {
      throw new HopException(
          "Coverage operations require non-empty polygonal geometries on every input row (row "
              + (feature.sourceId() + 1)
              + ").");
    }
    if (!(feature.geometry() instanceof org.locationtech.jts.geom.Polygon
        || feature.geometry() instanceof org.locationtech.jts.geom.MultiPolygon)) {
      throw new HopException(
          "Coverage operations require POLYGON or MULTIPOLYGON geometries on every input row (row "
              + (feature.sourceId() + 1)
              + ").");
    }
  }

  private void logSelectionWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      logBasic(warning);
    }
  }

  private List<Object> groupValues(Object[] row) {
    List<Object> values = new ArrayList<>(data.groupFieldIndexes.length);
    for (int groupFieldIndex : data.groupFieldIndexes) {
      values.add(row[groupFieldIndex]);
    }
    return values;
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record GroupKey(List<Object> values) {
    private GroupKey {
      values = List.copyOf(values);
    }
  }
}
