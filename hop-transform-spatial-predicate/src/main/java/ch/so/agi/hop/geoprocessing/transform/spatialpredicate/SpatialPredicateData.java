package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.geoprocessing.core.LayerCache;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateExecutor;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.IRowSet;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class SpatialPredicateData extends BaseTransformData implements ITransformData {
  IRowMeta outputRowMeta;
  IRowMeta primaryRowMeta;
  IRowMeta secondaryRowMeta;
  IRowSet primaryRowSet;
  IRowSet secondaryRowSet;
  LayerCache secondaryLayerCache;
  SecondaryRowJoinBuilder joinBuilder;
  int primaryGeometryFieldIndex = -1;
  int secondaryGeometryFieldIndex = -1;
  int distanceFieldIndex = -1;
  int booleanFieldIndex = -1;
  Double staticDistance;
  final SpatialPredicateExecutor executor = new SpatialPredicateExecutor();
  SpatialPredicateMatcher matcher;
  boolean initialized;
}
