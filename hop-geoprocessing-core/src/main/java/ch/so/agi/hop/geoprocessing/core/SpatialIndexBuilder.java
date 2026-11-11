package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;

public class SpatialIndexBuilder {

  public LayerCache build(List<FeatureRow> features, IRowMeta rowMeta, boolean buildUnionGeometry) {
    return build(features, rowMeta, buildUnionGeometry, OverlayMode.STANDARD, null);
  }

  public LayerCache build(
      List<FeatureRow> features,
      IRowMeta rowMeta,
      boolean buildUnionGeometry,
      OverlayMode overlayMode,
      Double precisionScale) {
    STRtree index = new STRtree();
    List<Geometry> geometries = new ArrayList<>();
    Integer srid = null;

    for (FeatureRow feature : features) {
      if (feature.geometry() == null || feature.geometry().isEmpty()) {
        continue;
      }
      index.insert(feature.envelope(), feature);
      if (buildUnionGeometry) {
        geometries.add(feature.geometry());
      }
      int featureSrid = feature.geometry().getSRID();
      if (featureSrid > 0) {
        if (srid != null && srid != featureSrid) {
          throw new IllegalStateException(
              "Secondary layer contains incompatible SRIDs: EPSG:" + srid + " and EPSG:" + featureSrid);
        }
        srid = featureSrid;
      }
    }
    index.build();

    Geometry unionGeometry = null;
    if (buildUnionGeometry && !geometries.isEmpty()) {
      try {
        unionGeometry =
            GeometryFieldValueHelper.normalize(
                OverlayExecution.union(geometries, overlayMode, precisionScale));
      } catch (org.apache.hop.core.exception.HopException e) {
        throw new IllegalStateException("Unable to build union geometry for spatial index", e);
      }
      if (unionGeometry != null && srid != null) {
        unionGeometry.setSRID(srid);
      }
    }

    return new LayerCache(features, rowMeta == null ? null : (IRowMeta) rowMeta.clone(), index, unionGeometry, srid);
  }
}
