package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.LayerCache;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateExecutor;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.relateng.RelateNG;

final class SpatialPredicateMatcher {

  private final SpatialPredicateExecutor executor;
  private final LayerCache secondaryLayerCache;
  private final String operationId;

  SpatialPredicateMatcher(
      SpatialPredicateExecutor executor, LayerCache secondaryLayerCache, String operationId) {
    this.executor = executor;
    this.secondaryLayerCache = secondaryLayerCache;
    this.operationId = operationId;
  }

  boolean matches(Geometry primaryGeometry, Double distance) throws HopException {
    if (primaryGeometry == null) {
      return false;
    }
    if ("disjoint".equals(operationId)) {
      RelateNG preparedPrimary = executor.prepare(primaryGeometry);
      for (FeatureRow candidate : candidateFeatures(primaryGeometry, distance)) {
        if (candidate.geometry() != null
            && executor.test("intersects", preparedPrimary, primaryGeometry, candidate.geometry(), null)) {
          return false;
        }
      }
      return true;
    }
    if ("distance_gte".equals(operationId)) {
      for (FeatureRow candidate : candidateFeatures(primaryGeometry, distance)) {
        if (candidate.geometry() != null
            && executor.test("distance_lte", primaryGeometry, candidate.geometry(), distance)) {
          return false;
        }
      }
      return true;
    }
    return !matchingFeatures(primaryGeometry, distance).isEmpty();
  }

  List<FeatureRow> matchingFeatures(Geometry primaryGeometry, Double distance) throws HopException {
    List<FeatureRow> matches = new ArrayList<>();
    if (primaryGeometry == null) {
      return matches;
    }
    RelateNG preparedPrimary = executor.prepare(primaryGeometry);
    for (FeatureRow candidate : candidateFeatures(primaryGeometry, distance)) {
      if (candidate.geometry() == null) {
        continue;
      }
      if (executor.test(operationId, preparedPrimary, primaryGeometry, candidate.geometry(), distance)) {
        matches.add(candidate);
      }
    }
    return matches;
  }

  private List<FeatureRow> candidateFeatures(Geometry primaryGeometry, Double distance) throws HopException {
    Envelope envelope = executor.searchEnvelope(operationId, primaryGeometry, distance);
    return secondaryLayerCache.query(envelope);
  }
}
