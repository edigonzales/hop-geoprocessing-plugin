package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;

public class LayerOverlayExecutor {

  public List<OverlayFragment> execute(
      String operationId,
      FeatureRow primaryFeature,
      LayerCache secondaryCache,
      OverlayMode overlayMode,
      Double precisionScale)
      throws HopException {
    Geometry primaryGeometry = GeometryFieldValueHelper.normalize(primaryFeature.geometry());
    if (primaryGeometry == null) {
      return List.of();
    }

    return switch (operationId) {
      case "clip" -> clip(primaryFeature, secondaryCache, overlayMode, precisionScale);
      case "erase" -> erase(primaryFeature, secondaryCache, overlayMode, precisionScale);
      case "identity" -> identity(primaryFeature, secondaryCache, overlayMode, precisionScale);
      case "intersection" -> intersection(primaryFeature, secondaryCache, overlayMode, precisionScale);
      default -> throw new HopException("Unsupported layer overlay operation: " + operationId);
    };
  }

  public List<OverlayFragment> execute(String operationId, FeatureRow primaryFeature, LayerCache secondaryCache)
      throws HopException {
    return execute(operationId, primaryFeature, secondaryCache, OverlayMode.STANDARD, null);
  }

  private List<OverlayFragment> intersection(
      FeatureRow primaryFeature, LayerCache secondaryCache, OverlayMode overlayMode, Double precisionScale)
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
              primaryFeature.geometry(),
              OverlayExecution.overlay(
                  primaryFeature.geometry(),
                  secondaryFeature.geometry(),
                  overlayMode,
                  precisionScale,
                  OverlayNG.INTERSECTION));
      if (geometry != null) {
        fragments.add(new OverlayFragment(geometry, secondaryFeature));
      }
    }
    return fragments;
  }

  private List<OverlayFragment> clip(
      FeatureRow primaryFeature, LayerCache secondaryCache, OverlayMode overlayMode, Double precisionScale)
      throws HopException {
    Geometry unionGeometry = secondaryCache.getUnionGeometry();
    if (unionGeometry == null) {
      return List.of();
    }
    Geometry geometry =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(),
            OverlayExecution.overlay(
                primaryFeature.geometry(), unionGeometry, overlayMode, precisionScale, OverlayNG.INTERSECTION));
    return geometry == null ? List.of() : List.of(new OverlayFragment(geometry, null));
  }

  private List<OverlayFragment> erase(
      FeatureRow primaryFeature, LayerCache secondaryCache, OverlayMode overlayMode, Double precisionScale)
      throws HopException {
    Geometry unionGeometry = secondaryCache.getUnionGeometry();
    if (unionGeometry == null) {
      return List.of(new OverlayFragment(primaryFeature.geometry(), null));
    }
    Geometry geometry =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(),
            OverlayExecution.overlay(
                primaryFeature.geometry(), unionGeometry, overlayMode, precisionScale, OverlayNG.DIFFERENCE));
    return geometry == null ? List.of() : List.of(new OverlayFragment(geometry, null));
  }

  private List<OverlayFragment> identity(
      FeatureRow primaryFeature, LayerCache secondaryCache, OverlayMode overlayMode, Double precisionScale)
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
              primaryFeature.geometry(),
              OverlayExecution.overlay(
                  primaryFeature.geometry(),
                  secondaryFeature.geometry(),
                  overlayMode,
                  precisionScale,
                  OverlayNG.INTERSECTION));
      if (fragment != null) {
        fragments.add(new OverlayFragment(fragment, secondaryFeature));
        matchedFeatures.add(secondaryFeature);
      }
    }

    if (matchedFeatures.isEmpty()) {
      return List.of(new OverlayFragment(primaryFeature.geometry(), null));
    }

    List<Geometry> matchedGeometries = matchedFeatures.stream().map(FeatureRow::geometry).toList();
    Geometry union =
        GeometryFieldValueHelper.normalize(
            OverlayExecution.union(matchedGeometries, overlayMode, precisionScale));
    Geometry remainder =
        GeometryFieldValueHelper.preserveSrid(
            primaryFeature.geometry(),
            OverlayExecution.overlay(
                primaryFeature.geometry(), union, overlayMode, precisionScale, OverlayNG.DIFFERENCE));
    if (remainder != null) {
      fragments.add(new OverlayFragment(remainder, null));
    }
    return fragments;
  }
}
