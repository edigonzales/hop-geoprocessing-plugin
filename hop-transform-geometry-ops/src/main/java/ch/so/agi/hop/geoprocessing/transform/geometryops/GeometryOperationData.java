package ch.so.agi.hop.geoprocessing.transform.geometryops;

import ch.so.agi.hop.geoprocessing.core.GeometryOperationExecutor;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class GeometryOperationData extends BaseTransformData implements ITransformData {
  IRowMeta outputRowMeta;
  int primaryGeometryFieldIndex = -1;
  int secondaryGeometryFieldIndex = -1;
  int distanceFieldIndex = -1;
  int outputGeometryFieldIndex = -1;
  Double staticDistance;
  final GeometryOperationExecutor executor = new GeometryOperationExecutor();
}
