package ch.so.agi.hop.geoprocessing.transform.coverageops;

import ch.so.agi.hop.geoprocessing.core.CoverageMergeStrategy;
import ch.so.agi.hop.geoprocessing.core.CoverageOperationExecutor;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class CoverageOperationData extends BaseTransformData implements ITransformData {
  IRowMeta outputRowMeta;
  int geometryFieldIndex = -1;
  int[] groupFieldIndexes = new int[0];
  int booleanFieldIndex = -1;
  int outputGeometryFieldIndex = -1;
  Double staticDistance;
  Double staticGapWidth;
  Double staticSnappingDistance;
  CoverageMergeStrategy mergeStrategy = CoverageMergeStrategy.LONGEST_BORDER;
  final List<Object[]> outputRows = new ArrayList<>();
  int outputIndex;
  boolean initialized;
  final CoverageOperationExecutor executor = new CoverageOperationExecutor();
}
