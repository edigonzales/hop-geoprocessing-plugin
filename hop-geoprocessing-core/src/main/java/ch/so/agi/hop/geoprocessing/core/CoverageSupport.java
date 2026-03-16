package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.MultiPolygon;

public final class CoverageSupport {

  private CoverageSupport() {}

  public static Geometry[] toCoverageArray(List<Geometry> geometries, boolean allowNullOrEmpty)
      throws HopException {
    List<Geometry> coverage = new ArrayList<>();
    Integer srid = null;
    for (Geometry geometry : geometries) {
      Geometry normalized = GeometryFieldValueHelper.normalize(geometry);
      if (normalized == null) {
        if (allowNullOrEmpty) {
          continue;
        }
        throw new HopException("Coverage operations require non-empty polygonal geometries.");
      }
      requirePolygonal(normalized);
      srid = requireCompatibleSrid(srid, normalized);
      coverage.add(normalized);
    }
    return coverage.toArray(Geometry[]::new);
  }

  public static void requirePolygonal(Geometry geometry) throws HopException {
    if (!(geometry instanceof Polygon || geometry instanceof MultiPolygon)) {
      throw new HopException("Coverage operations require POLYGON or MULTIPOLYGON geometries.");
    }
  }

  public static Integer requireCompatibleSrid(Integer expectedSrid, Geometry geometry)
      throws HopException {
    Integer geometrySrid = GeometryFieldValueHelper.sridOf(geometry);
    if (expectedSrid != null && geometrySrid != null && !expectedSrid.equals(geometrySrid)) {
      throw new HopException(
          "Coverage operations require geometries with matching SRIDs ("
              + expectedSrid
              + " vs "
              + geometrySrid
              + ").");
    }
    return expectedSrid != null ? expectedSrid : geometrySrid;
  }

  public static Geometry preserveSrid(Integer srid, Geometry geometry) {
    Geometry normalized = GeometryFieldValueHelper.normalize(geometry);
    if (normalized == null || srid == null || srid == 0) {
      return normalized;
    }
    normalized.setSRID(srid);
    return normalized;
  }
}
