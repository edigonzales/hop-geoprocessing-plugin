package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.union.UnaryUnionOp;

public class SpatialIndexBuilder {

  public LayerCache build(List<FeatureRow> features, IRowMeta rowMeta, boolean buildUnionGeometry) {
    STRtree index = new STRtree();
    List<Geometry> geometries = new ArrayList<>();
    Integer srid = null;

    for (FeatureRow feature : features) {
      if (feature.geometry() == null) {
        continue;
      }
      index.insert(feature.envelope(), feature);
      geometries.add(feature.geometry());
      if (srid == null) {
        srid = GeometryFieldValueHelper.sridOf(feature.geometry());
      }
    }
    index.build();

    Geometry unionGeometry = null;
    if (buildUnionGeometry && !geometries.isEmpty()) {
      unionGeometry = GeometryFieldValueHelper.normalize(UnaryUnionOp.union(geometries));
      if (unionGeometry != null && srid != null) {
        unionGeometry.setSRID(srid);
      }
    }

    return new LayerCache(features, rowMeta == null ? null : (IRowMeta) rowMeta.clone(), index, unionGeometry);
  }
}
