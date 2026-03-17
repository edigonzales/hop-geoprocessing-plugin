package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldValueHelper;
import ch.so.agi.hop.geoprocessing.core.LayerCache;
import ch.so.agi.hop.geoprocessing.core.SpatialIndexBuilder;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateResultMode;
import ch.so.agi.hop.geoprocessing.core.TransformStreamRouting;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Geometry;

public class SpatialPredicate extends BaseTransform<SpatialPredicateMeta, SpatialPredicateData> {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  public SpatialPredicate(
      TransformMeta transformMeta,
      SpatialPredicateMeta meta,
      SpatialPredicateData data,
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

    Geometry primaryGeometry =
        GeometryFieldValueHelper.readGeometry(data.primaryRowMeta, data.primaryGeometryFieldIndex, primaryRow);
    Double distance = resolveDistance(primaryRow);

    if (meta.getResultMode() == SpatialPredicateResultMode.INNER_JOIN) {
      for (FeatureRow matchedFeature : data.matcher.matchingFeatures(primaryGeometry, distance)) {
        TransformStreamRouting.putRowToRowSets(
            this,
            data.outputRowMeta,
            data.joinBuilder.buildRow(primaryRow, matchedFeature),
            data.mainOutputRowSets);
      }
      return true;
    }

    boolean matched = data.matcher.matches(primaryGeometry, distance);
    switch (meta.getResultMode()) {
      case BOOLEAN_COLUMN -> {
        Object[] outputRow = RowDataUtil.resizeArray(primaryRow, data.outputRowMeta.size());
        outputRow[data.booleanFieldIndex] = matched;
        TransformStreamRouting.putRowToRowSets(this, data.outputRowMeta, outputRow, data.mainOutputRowSets);
      }
      case KEEP_MATCHED -> {
        if (matched) {
          TransformStreamRouting.putRowToRowSets(
              this, data.outputRowMeta, primaryRow.clone(), data.mainOutputRowSets);
        } else {
          TransformStreamRouting.putRowToRowSets(
              this, data.outputRowMeta, primaryRow.clone(), data.rejectOutputRowSets);
        }
      }
      case KEEP_UNMATCHED -> {
        if (!matched) {
          TransformStreamRouting.putRowToRowSets(
              this, data.outputRowMeta, primaryRow.clone(), data.mainOutputRowSets);
        } else {
          TransformStreamRouting.putRowToRowSets(
              this, data.outputRowMeta, primaryRow.clone(), data.rejectOutputRowSets);
        }
      }
      default -> throw new HopTransformException("Unexpected result mode: " + meta.getResultMode());
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
    data.distanceFieldIndex =
        meta.getDistanceMode() == DistanceMode.FIELD
            ? data.primaryRowMeta.indexOfValue(meta.getDistanceFieldName())
            : -1;
    if (meta.getDistanceMode() == DistanceMode.FIELD && data.distanceFieldIndex < 0) {
      throw new HopTransformException(
          "Distance field was not found on the primary input: " + meta.getDistanceFieldName());
    }
    data.staticDistance =
        meta.getDistanceMode() == DistanceMode.STATIC && meta.getDistanceValue() != null
            ? Double.parseDouble(resolve(meta.getDistanceValue()))
            : null;
    TransformStreamRouting.RowSetTargets outputTargets =
        TransformStreamRouting.partitionRowSets(getOutputRowSets(), meta.getRejectTransformName());
    data.mainOutputRowSets = outputTargets.mainRowSets();
    data.rejectOutputRowSets = outputTargets.targetRowSets();

    List<FeatureRow> secondaryFeatures = new ArrayList<>();
    long featureId = 0L;
    Object[] secondaryRow;
    while ((secondaryRow = getRowFrom(data.secondaryRowSet)) != null) {
      secondaryFeatures.add(
          FeatureRow.fromRow(data.secondaryRowMeta, data.secondaryGeometryFieldIndex, secondaryRow, featureId++));
    }

    LayerCache secondaryLayerCache =
        new SpatialIndexBuilder().build(secondaryFeatures, data.secondaryRowMeta, false);
    data.secondaryLayerCache = secondaryLayerCache;
    data.matcher = new SpatialPredicateMatcher(data.executor, data.secondaryLayerCache, meta.getOperationId());

    if (meta.getResultMode() == SpatialPredicateResultMode.INNER_JOIN) {
      data.joinBuilder =
          new SecondaryRowJoinBuilder(data.primaryRowMeta, data.secondaryRowMeta, meta.getFieldPrefix());
      data.outputRowMeta = data.joinBuilder.createOutputRowMeta();
    } else {
      data.outputRowMeta = (IRowMeta) data.primaryRowMeta.clone();
      meta.getFields(data.outputRowMeta, getTransformName(), new IRowMeta[] {data.secondaryRowMeta}, null, this, metadataProvider);
      data.booleanFieldIndex =
          meta.getResultMode() == SpatialPredicateResultMode.BOOLEAN_COLUMN
              ? data.outputRowMeta.indexOfValue(meta.getBooleanFieldName())
              : -1;
    }
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

  private Double resolveDistance(Object[] primaryRow) throws HopException {
    if (!meta.descriptor().requires(ch.so.agi.hop.geoprocessing.core.ParameterId.DISTANCE)) {
      return null;
    }
    if (meta.getDistanceMode() == DistanceMode.FIELD) {
      Double distance = data.primaryRowMeta.getNumber(primaryRow, data.distanceFieldIndex);
      if (distance == null) {
        throw new HopTransformException("Distance field value is null");
      }
      return distance;
    }
    return data.staticDistance;
  }
}
