package ch.so.agi.hop.geoprocessing.core;

import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public final class GeometryFieldValueHelper {

  private static final GeometryValueParser GEOMETRY_VALUE_PARSER = new GeometryValueParser();

  private GeometryFieldValueHelper() {}

  public static Geometry readGeometry(IRowMeta rowMeta, int fieldIndex, Object[] rowData)
      throws HopException {
    if (rowMeta == null) {
      throw new HopException("Row metadata is required to read a geometry field");
    }
    if (fieldIndex < 0 || fieldIndex >= rowMeta.size()) {
      throw new HopException("Geometry field index is invalid: " + fieldIndex);
    }
    if (rowData == null) {
      return null;
    }
    if (fieldIndex >= rowData.length) {
      throw new HopException(
          "Geometry field index "
              + fieldIndex
              + " is outside the row length "
              + rowData.length
              + " for field '"
              + rowMeta.getValueMeta(fieldIndex).getName()
              + "'");
    }
    IValueMeta valueMeta = rowMeta.getValueMeta(fieldIndex);
    return readGeometry(
        valueMeta, rowData[fieldIndex], valueMeta == null ? "" : valueMeta.getName(), fieldIndex);
  }

  public static Geometry readGeometry(IValueMeta valueMeta, Object value) throws HopException {
    return readGeometry(valueMeta, value, valueMeta == null ? "" : valueMeta.getName(), -1);
  }

  private static Geometry readGeometry(
      IValueMeta valueMeta, Object value, String fieldName, int fieldIndex) throws HopException {
    if (value == null) {
      return null;
    }
    try {
      return linearizeForProcessing(GEOMETRY_VALUE_PARSER.parseGeometry(valueMeta, value));
    } catch (Exception e) {
      throw new HopException(buildParseErrorMessage(valueMeta, value, fieldName, fieldIndex), e);
    }
  }

  public static Geometry normalize(Geometry geometry) {
    return geometry == null || geometry.isEmpty() ? null : geometry;
  }

  /**
   * Converts the custom SQL/MM curve subclasses to matching ordinary JTS geometry types using their
   * inherited densified coordinate representation. Standard JTS geometries are returned unchanged.
   */
  public static Geometry linearizeForProcessing(Geometry geometry) {
    Geometry normalized = normalize(geometry);
    if (normalized == null) {
      return normalized;
    }

    return normalize(CurveGeometrySupport.linearize(normalized, 0.001));
  }

  public static Integer sridOf(Geometry geometry) {
    if (geometry == null || geometry.isEmpty() || geometry.getSRID() == 0) {
      return null;
    }
    return geometry.getSRID();
  }

  public static void requireCompatibleSrid(Geometry left, Geometry right, String message)
      throws HopException {
    Integer leftSrid = sridOf(left);
    Integer rightSrid = sridOf(right);
    if (leftSrid != null && rightSrid != null && !leftSrid.equals(rightSrid)) {
      throw new HopException(message + " (" + leftSrid + " vs " + rightSrid + ")");
    }
  }

  public static Geometry preserveSrid(Geometry source, Geometry result) {
    Geometry normalized = normalize(result);
    if (normalized == null || source == null || source.getSRID() == 0) {
      return normalized;
    }
    normalized.setSRID(source.getSRID());
    return normalized;
  }

  public static List<Geometry> explode(Geometry geometry) {
    Geometry normalized = normalize(geometry);
    if (normalized == null) {
      return List.of();
    }
    List<Geometry> geometries = new ArrayList<>();
    for (int index = 0; index < normalized.getNumGeometries(); index++) {
      Geometry child = preserveSrid(normalized, normalized.getGeometryN(index));
      if (child instanceof LinearRing ring) {
        child = ring.getFactory().createLineString(ring.getCoordinates());
        child.setSRID(normalized.getSRID());
      }
      if (child != null) {
        geometries.add(child);
      }
    }
    return geometries;
  }

  public static Geometry force2D(Geometry geometry) {
    Geometry normalized = normalize(geometry);
    if (normalized == null) {
      return null;
    }
    Geometry clone = (Geometry) normalized.copy();
    clone.apply(
        new CoordinateSequenceFilter() {
          @Override
          public void filter(CoordinateSequence seq, int i) {
            if (seq.getDimension() > 2) {
              seq.setOrdinate(i, 2, Double.NaN);
            }
          }

          @Override
          public boolean isDone() {
            return false;
          }

          @Override
          public boolean isGeometryChanged() {
            return true;
          }
        });
    return preserveSrid(normalized, clone);
  }

  public static Geometry toMulti(Geometry geometry) {
    Geometry normalized = normalize(geometry);
    if (normalized == null) {
      return null;
    }
    if (normalized instanceof Point point) {
      return preserveSrid(normalized, point.getFactory().createMultiPoint(new Point[] {point}));
    }
    if (normalized instanceof LineString lineString) {
      return preserveSrid(
          normalized, lineString.getFactory().createMultiLineString(new LineString[] {lineString}));
    }
    if (normalized instanceof LinearRing ring) {
      LineString lineString = ring.getFactory().createLineString(ring.getCoordinates());
      return preserveSrid(
          normalized, ring.getFactory().createMultiLineString(new LineString[] {lineString}));
    }
    if (normalized instanceof Polygon polygon) {
      return preserveSrid(
          normalized, polygon.getFactory().createMultiPolygon(new Polygon[] {polygon}));
    }
    return normalized;
  }

  private static String buildParseErrorMessage(
      IValueMeta valueMeta, Object value, String fieldName, int fieldIndex) {
    String valueClass = value == null ? "null" : value.getClass().getName();
    String valueMetaClass = valueMeta == null ? "null" : valueMeta.getClass().getName();
    String valueMetaType = valueMeta == null ? "null" : valueMeta.getTypeDesc();
    StringBuilder builder = new StringBuilder("Unable to parse geometry value");
    if (fieldName != null && !fieldName.isBlank()) {
      builder.append(" for field '").append(fieldName).append("'");
    }
    if (fieldIndex >= 0) {
      builder.append(" (index ").append(fieldIndex).append(')');
    }
    builder
        .append(": valueClass=")
        .append(valueClass)
        .append(", valueMetaClass=")
        .append(valueMetaClass)
        .append(", valueMetaType=")
        .append(valueMetaType);
    return builder.toString();
  }
}
