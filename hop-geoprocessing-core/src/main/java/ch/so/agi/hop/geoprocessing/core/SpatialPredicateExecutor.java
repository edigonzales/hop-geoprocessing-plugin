package ch.so.agi.hop.geoprocessing.core;

import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.operation.relateng.RelatePredicate;
import org.locationtech.jts.operation.relateng.TopologyPredicate;

public class SpatialPredicateExecutor {

  public boolean test(String operationId, Geometry primaryGeometry, Geometry secondaryGeometry, Double distance)
      throws HopException {
    return test(operationId, null, primaryGeometry, secondaryGeometry, distance);
  }

  public boolean test(
      String operationId,
      RelateNG preparedPrimary,
      Geometry primaryGeometry,
      Geometry secondaryGeometry,
      Double distance)
      throws HopException {
    Geometry primary = GeometryFieldValueHelper.normalize(primaryGeometry);
    Geometry secondary = GeometryFieldValueHelper.normalize(secondaryGeometry);
    if (primary == null || secondary == null) {
      return false;
    }
    GeometryFieldValueHelper.requireCompatibleSrid(primary, secondary, "Geometry SRIDs must match");

    return switch (operationId) {
      case "contains",
          "crosses",
          "disjoint",
          "intersects",
          "overlaps",
          "touches",
          "within" -> evaluateRelate(operationId, preparedPrimary, primary, secondary);
      case "distance_gte" -> !primary.isWithinDistance(secondary, Math.abs(requireDistance(distance)));
      case "distance_lte" -> primary.isWithinDistance(secondary, Math.abs(requireDistance(distance)));
      default -> throw new HopException("Unsupported spatial predicate: " + operationId);
    };
  }

  public RelateNG prepare(Geometry primaryGeometry) {
    Geometry primary = GeometryFieldValueHelper.normalize(primaryGeometry);
    return primary == null ? null : RelateNG.prepare(primary);
  }

  public Envelope searchEnvelope(String operationId, Geometry geometry, Double distance) throws HopException {
    Envelope envelope = new Envelope(geometry.getEnvelopeInternal());
    if ("distance_lte".equals(operationId) || "distance_gte".equals(operationId)) {
      envelope.expandBy(Math.abs(requireDistance(distance)));
    }
    return envelope;
  }

  private double requireDistance(Double distance) throws HopException {
    if (distance == null) {
      throw new HopException("Distance is required");
    }
    return distance;
  }

  private boolean evaluateRelate(
      String operationId, RelateNG preparedPrimary, Geometry primary, Geometry secondary)
      throws HopException {
    TopologyPredicate predicate = topologyPredicate(operationId);
    if (preparedPrimary != null) {
      return preparedPrimary.evaluate(secondary, predicate);
    }
    return RelateNG.relate(primary, secondary, predicate);
  }

  private TopologyPredicate topologyPredicate(String operationId) throws HopException {
    return switch (operationId) {
      case "contains" -> RelatePredicate.contains();
      case "crosses" -> RelatePredicate.crosses();
      case "disjoint" -> RelatePredicate.disjoint();
      case "intersects" -> RelatePredicate.intersects();
      case "overlaps" -> RelatePredicate.overlaps();
      case "touches" -> RelatePredicate.touches();
      case "within" -> RelatePredicate.within();
      default -> throw new HopException("Unsupported spatial predicate: " + operationId);
    };
  }
}
