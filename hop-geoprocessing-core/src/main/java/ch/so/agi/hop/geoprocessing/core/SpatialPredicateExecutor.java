package ch.so.agi.hop.geoprocessing.core;

import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

public class SpatialPredicateExecutor {

  public boolean test(String operationId, Geometry primaryGeometry, Geometry secondaryGeometry, Double distance)
      throws HopException {
    Geometry primary = GeometryFieldValueHelper.normalize(primaryGeometry);
    Geometry secondary = GeometryFieldValueHelper.normalize(secondaryGeometry);
    if (primary == null || secondary == null) {
      return false;
    }
    GeometryFieldValueHelper.requireCompatibleSrid(primary, secondary, "Geometry SRIDs must match");

    return switch (operationId) {
      case "contains" -> primary.contains(secondary);
      case "crosses" -> primary.crosses(secondary);
      case "disjoint" -> primary.disjoint(secondary);
      case "distance_gte" -> !primary.isWithinDistance(secondary, Math.abs(requireDistance(distance)));
      case "distance_lte" -> primary.isWithinDistance(secondary, Math.abs(requireDistance(distance)));
      case "intersects" -> primary.intersects(secondary);
      case "overlaps" -> primary.overlaps(secondary);
      case "touches" -> primary.touches(secondary);
      case "within" -> primary.within(secondary);
      default -> throw new HopException("Unsupported spatial predicate: " + operationId);
    };
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
}
