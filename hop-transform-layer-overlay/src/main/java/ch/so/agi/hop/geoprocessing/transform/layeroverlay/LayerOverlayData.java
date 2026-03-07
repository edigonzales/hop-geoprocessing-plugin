package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import ch.so.agi.hop.geoprocessing.core.LayerCache;
import ch.so.agi.hop.geoprocessing.core.LayerOverlayExecutor;
import ch.so.agi.hop.geoprocessing.core.OverlayResultRowBuilder;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.IRowSet;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class LayerOverlayData extends BaseTransformData implements ITransformData {
  IRowMeta primaryRowMeta;
  IRowMeta secondaryRowMeta;
  IRowMeta outputRowMeta;
  IRowSet primaryRowSet;
  IRowSet secondaryRowSet;
  int primaryGeometryFieldIndex = -1;
  int secondaryGeometryFieldIndex = -1;
  LayerCache secondaryLayerCache;
  OverlayResultRowBuilder outputRowBuilder;
  final LayerOverlayExecutor executor = new LayerOverlayExecutor();
  boolean initialized;
}
