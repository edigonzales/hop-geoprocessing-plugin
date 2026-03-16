package ch.so.agi.hop.geoprocessing.core;

import java.util.Collection;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;

public final class OverlayExecution {

  private OverlayExecution() {}

  public static Geometry overlay(
      Geometry left, Geometry right, OverlayMode mode, Double precisionScale, int operationCode)
      throws HopException {
    OverlayMode resolvedMode = mode == null ? OverlayMode.STANDARD : mode;
    return switch (resolvedMode) {
      case STANDARD -> OverlayNG.overlay(left, right, operationCode);
      case ROBUST -> OverlayNGRobust.overlay(left, right, operationCode);
      case FIXED_PRECISION ->
          OverlayNG.overlay(left, right, operationCode, precisionModel(precisionScale));
    };
  }

  public static Geometry union(Collection<Geometry> geometries, OverlayMode mode, Double precisionScale)
      throws HopException {
    OverlayMode resolvedMode = mode == null ? OverlayMode.STANDARD : mode;
    return switch (resolvedMode) {
      case STANDARD -> UnaryUnionNG.union(geometries, new PrecisionModel());
      case ROBUST -> OverlayNGRobust.union(geometries);
      case FIXED_PRECISION -> UnaryUnionNG.union(geometries, precisionModel(precisionScale));
    };
  }

  public static PrecisionModel precisionModel(Double precisionScale) throws HopException {
    if (precisionScale == null || precisionScale <= 0.0d) {
      throw new HopException("Precision scale must be a positive number");
    }
    return new PrecisionModel(precisionScale);
  }
}
