package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

public class LayerOverlayExecutor {

  public List<OverlayFragment> execute(String operationId, FeatureRow primaryFeature, LayerCache secondaryCache)
      throws HopException {
    Geometry primaryGeometry = GeometryFieldValueHelper.normalize(primaryFeature.geometry());
    if (primaryGeometry == null) {
      return List.of();
    }

    return switch (operationId) {
      case "clip" -> clip(primaryFeature, secondaryCache);
      case "erase" -> erase(primaryFeature, secondaryCache);
      case "identity" -> identity(primaryFeature, secondaryCache);
      case "intersection" -> intersection(primaryFeature, secondaryCache);
      default -> throw new HopException("Unsupported layer overlay operation: " + operationId);
    };
  }

  private List<OverlayFragment> intersection(FeatureRow primaryFeature, LayerCache secondaryCache)
      throws HopException {
    List<OverlayFragment> fragments = new ArrayList<>();
    for (FeatureRow secondaryFeature : secondaryCache.query(primaryFeature.geometry())) {
      if (secondaryFeature.geometry() == null) {
        continue;
      }
      GeometryFieldValueHelper.requireCompatibleSrid(
          primaryFeature.geometry(), secondaryFeature.geometry(), "Geometry SRIDs must match");
      if (!primaryFeature.geometry().intersects(secondaryFeature.geometry())) {
        continue;
      }
      Geometry geometry =
          GeometryFieldValueHelper.preserveSrid(
              primaryFeature.geometry(), primaryFeature.geometry().intersection(secondaryFeature.geometry()));
      if (geometry != null) {
        fragments.add(new OverlayFragment(geometry, secondaryFeature));
      }
    }
    return fragments;
  }

  private List<OverlayFragment> clip(FeatureRow primaryFeature, LayerCache secondaryCache) {
    Geometry unionGeometry = secondaryCache.getUnionGeometry();
    if (unionGeometry == null) {
      return List.of();
    }
    Geometry geometry =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(), primaryFeature.geometry().intersection(unionGeometry));
    return geometry == null ? List.of() : List.of(new OverlayFragment(geometry, null));
  }

  private List<OverlayFragment> erase(FeatureRow primaryFeature, LayerCache secondaryCache) {
    Geometry unionGeometry = secondaryCache.getUnionGeometry();
    if (unionGeometry == null) {
      return List.of(new OverlayFragment(primaryFeature.geometry(), null));
    }
    Geometry geometry =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(), primaryFeature.geometry().difference(unionGeometry));
    return geometry == null ? List.of() : List.of(new OverlayFragment(geometry, null));
  }

  private List<OverlayFragment> identity(FeatureRow primaryFeature, LayerCache secondaryCache)
      throws HopException {
    List<OverlayFragment> fragments = new ArrayList<>();
    List<FeatureRow> matchedFeatures = new ArrayList<>();

    for (FeatureRow secondaryFeature : secondaryCache.query(primaryFeature.geometry())) {
      if (secondaryFeature.geometry() == null) {
        continue;
      }
      GeometryFieldValueHelper.requireCompatibleSrid(
          primaryFeature.geometry(), secondaryFeature.geometry(), "Geometry SRIDs must match");
      if (!primaryFeature.geometry().intersects(secondaryFeature.geometry())) {
        continue;
      }
      Geometry fragment =
          GeometryFieldValueHelper.preserveSrid(
              primaryFeature.geometry(), primaryFeature.geometry().intersection(secondaryFeature.geometry()));
      if (fragment != null) {
        fragments.add(new OverlayFragment(fragment, secondaryFeature));
        matchedFeatures.add(secondaryFeature);
      }
    }

    if (matchedFeatures.isEmpty()) {
      return List.of(new OverlayFragment(primaryFeature.geometry(), null));
    }

    List<Geometry> matchedGeometries = matchedFeatures.stream().map(FeatureRow::geometry).toList();
    Geometry union = GeometryFieldValueHelper.normalize(UnaryUnionOp.union(matchedGeometries));
    Geometry remainder =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(), primaryFeature.geometry().difference(union));
    if (remainder != null) {
      fragments.add(new OverlayFragment(remainder, null));
    }
    return fragments;
  }
}
