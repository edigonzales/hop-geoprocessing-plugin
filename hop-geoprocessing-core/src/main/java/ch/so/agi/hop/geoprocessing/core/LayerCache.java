package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;

public class LayerCache {

  private final List<FeatureRow> features;
  private final IRowMeta rowMeta;
  private final STRtree spatialIndex;
  private final Geometry unionGeometry;

  public LayerCache(List<FeatureRow> features, IRowMeta rowMeta, STRtree spatialIndex, Geometry unionGeometry) {
    this.features = Collections.unmodifiableList(new ArrayList<>(features));
    this.rowMeta = rowMeta;
    this.spatialIndex = spatialIndex;
    this.unionGeometry = unionGeometry;
  }

  public List<FeatureRow> getFeatures() {
    return features;
  }

  public IRowMeta getRowMeta() {
    return rowMeta;
  }

  public Geometry getUnionGeometry() {
    return unionGeometry;
  }

  @SuppressWarnings("unchecked")
  public List<FeatureRow> query(Geometry geometry) {
    if (geometry == null) {
      return List.of();
    }
    return spatialIndex.query(geometry.getEnvelopeInternal());
  }

  @SuppressWarnings("unchecked")
  public List<FeatureRow> query(Envelope envelope) {
    if (envelope == null) {
      return List.of();
    }
    return spatialIndex.query(envelope);
  }

  public boolean isEmpty() {
    return features.isEmpty();
  }
}
