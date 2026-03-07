package ch.so.agi.hop.geoprocessing.transform.layeraggregate;

import ch.so.agi.hop.geoprocessing.core.LayerAggregateExecutor;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class LayerAggregateData extends BaseTransformData implements ITransformData {
  IRowMeta outputRowMeta;
  int geometryFieldIndex = -1;
  int outputGeometryFieldIndex = -1;
  int[] groupFieldIndexes = new int[0];
  final List<Object[]> outputRows = new ArrayList<>();
  int outputIndex;
  boolean initialized;
  final LayerAggregateExecutor executor = new LayerAggregateExecutor();
}
