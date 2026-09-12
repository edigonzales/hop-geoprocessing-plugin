package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.locationtech.jts.simplify.VWSimplifier;

public class GeometryOperationExecutor {

  public List<Geometry> execute(
      String operationId,
      Geometry primaryGeometry,
      Geometry secondaryGeometry,
      Double distance,
      OverlayMode overlayMode,
      Double precisionScale,
      Integer bufferSegments,
      BufferCapStyle bufferCapStyle,
      BufferJoinStyle bufferJoinStyle,
      boolean singleSided)
      throws HopException {
    if ("linearize_curves".equals(operationId)) {
      double error = requireDistance(distance, "Maximum chord deviation (coordinate units)");
      Geometry result =
          com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.linearize(primaryGeometry, error);
      return result == null ? List.of() : List.of(result);
    }
    Geometry primary = GeometryFieldValueHelper.linearizeForProcessing(primaryGeometry);
    Geometry secondary = GeometryFieldValueHelper.linearizeForProcessing(secondaryGeometry);

    if ("explode".equals(operationId)) {
      return GeometryFieldValueHelper.explode(primary);
    }
    if (primary == null) {
      return List.of();
    }

    Geometry result =
        switch (operationId) {
          case "boundary" -> normalize(primary, unwrapBoundary(primary.getBoundary(), primary));
          case "buffer" ->
              normalize(primary, primary.buffer(requireDistance(distance, "Distance")));
          case "buffer_extended" ->
              normalize(
                  primary,
                  BufferOp.bufferOp(
                      primary,
                      singleSided
                          ? requireDistance(distance, "Distance")
                          : Math.abs(requireDistance(distance, "Distance")),
                      createBufferParameters(
                          bufferSegments, bufferCapStyle, bufferJoinStyle, singleSided)));
          case "centroid" -> normalize(primary, primary.getCentroid());
          case "concave_hull" ->
              normalize(
                  primary,
                  ConcaveHull.concaveHullByLength(
                      primary, Math.abs(requireDistance(distance, "Maximum edge length"))));
          case "convex_hull" -> normalize(primary, primary.convexHull());
          case "densify" ->
              normalize(
                  primary,
                  Densifier.densify(primary, Math.abs(requireDistance(distance, "Distance"))));
          case "difference" ->
              normalize(
                  primary,
                  binaryOverlay(
                      primary,
                      secondary,
                      overlayMode,
                      precisionScale,
                      org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE));
          case "extract_coordinates" ->
              normalize(
                  primary,
                  primary.getFactory().createMultiPointFromCoords(primary.getCoordinates()));
          case "fix_geometry" -> normalize(primary, GeometryFixer.fix(primary));
          case "interior_point" -> normalize(primary, primary.getInteriorPoint());
          case "intersection" ->
              normalize(
                  primary,
                  binaryOverlay(
                      primary,
                      secondary,
                      overlayMode,
                      precisionScale,
                      org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION));
          case "line_merge" -> normalize(primary, lineMerge(primary));
          case "linear_referencing" ->
              normalize(primary, linearReferencing(primary, requireDistance(distance, "Distance")));
          case "mbc" -> normalize(primary, new MinimumBoundingCircle(primary).getCircle());
          case "envelope" -> normalize(primary, primary.getEnvelope());
          case "minimum_bounding_rectangle" ->
              normalize(primary, new MinimumDiameter(primary).getMinimumRectangle());
          case "minimum_diameter" -> normalize(primary, new MinimumDiameter(primary).getDiameter());
          case "polygonize" -> normalize(primary, polygonize(primary));
          case "reduce_precision" ->
              normalize(
                  primary,
                  GeometryPrecisionReducer.reduce(
                      primary,
                      OverlayExecution.precisionModel(requirePrecisionScale(precisionScale))));
          case "remove_all_holes" -> normalize(primary, removeAllHoles(primary));
          case "remove_small_holes" ->
              normalize(
                  primary, removeSmallHoles(primary, requireDistance(distance, "Area threshold")));
          case "reverse" -> normalize(primary, primary.reverse());
          case "simplify" ->
              normalize(
                  primary,
                  DouglasPeuckerSimplifier.simplify(
                      primary, Math.abs(requireDistance(distance, "Distance"))));
          case "simplify_topology" ->
              normalize(
                  primary,
                  TopologyPreservingSimplifier.simplify(
                      primary, Math.abs(requireDistance(distance, "Distance"))));
          case "simplify_vw" ->
              normalize(
                  primary,
                  VWSimplifier.simplify(primary, Math.abs(requireDistance(distance, "Distance"))));
          case "snap" ->
              normalize(
                  primary,
                  snap(primary, secondary, Math.abs(requireDistance(distance, "Snap distance"))));
          case "snap_to_self" ->
              normalize(
                  primary,
                  GeometrySnapper.snapToSelf(
                      primary, Math.abs(requireDistance(distance, "Snap distance")), true));
          case "split" -> normalize(primary, split(primary, secondary));
          case "sym_difference" ->
              normalize(
                  primary,
                  binaryOverlay(
                      primary,
                      secondary,
                      overlayMode,
                      precisionScale,
                      org.locationtech.jts.operation.overlayng.OverlayNG.SYMDIFFERENCE));
          case "to_2d" -> normalize(primary, GeometryFieldValueHelper.force2D(primary));
          case "to_multi" -> normalize(primary, GeometryFieldValueHelper.toMulti(primary));
          case "union" ->
              normalize(
                  primary,
                  binaryOverlay(
                      primary,
                      secondary,
                      overlayMode,
                      precisionScale,
                      org.locationtech.jts.operation.overlayng.OverlayNG.UNION));
          default -> throw new HopException("Unsupported geometry operation: " + operationId);
        };

    return result == null ? List.of() : List.of(result);
  }

  public List<Geometry> execute(
      String operationId,
      Geometry primaryGeometry,
      Geometry secondaryGeometry,
      Double distance,
      Integer bufferSegments,
      BufferCapStyle bufferCapStyle,
      BufferJoinStyle bufferJoinStyle,
      boolean singleSided)
      throws HopException {
    return execute(
        operationId,
        primaryGeometry,
        secondaryGeometry,
        distance,
        OverlayMode.STANDARD,
        null,
        bufferSegments,
        bufferCapStyle,
        bufferJoinStyle,
        singleSided);
  }

  private Geometry binaryOverlay(
      Geometry left,
      Geometry right,
      OverlayMode overlayMode,
      Double precisionScale,
      int operationCode)
      throws HopException {
    if (right == null) {
      return null;
    }
    GeometryFieldValueHelper.requireCompatibleSrid(left, right, "Geometry SRIDs must match");
    return OverlayExecution.overlay(left, right, overlayMode, precisionScale, operationCode);
  }

  private Geometry unwrapBoundary(Geometry boundary, Geometry source) {
    if (boundary instanceof LinearRing ring) {
      Geometry lineString = source.getFactory().createLineString(ring.getCoordinates());
      lineString.setSRID(source.getSRID());
      return lineString;
    }
    return boundary;
  }

  private BufferParameters createBufferParameters(
      Integer segments, BufferCapStyle capStyle, BufferJoinStyle joinStyle, boolean singleSided) {
    BufferParameters parameters = new BufferParameters();
    parameters.setQuadrantSegments(segments == null ? 8 : segments);
    parameters.setEndCapStyle((capStyle == null ? BufferCapStyle.ROUND : capStyle).getJtsValue());
    parameters.setJoinStyle((joinStyle == null ? BufferJoinStyle.ROUND : joinStyle).getJtsValue());
    parameters.setSingleSided(singleSided);
    return parameters;
  }

  private Geometry lineMerge(Geometry geometry) {
    LineMerger merger = new LineMerger();
    merger.add(geometry);
    @SuppressWarnings("unchecked")
    Collection<LineString> merged = merger.getMergedLineStrings();
    return geometry.getFactory().buildGeometry(new ArrayList<>(merged));
  }

  private Geometry polygonize(Geometry geometry) {
    Polygonizer polygonizer = new Polygonizer();
    polygonizer.add(geometry);
    @SuppressWarnings("unchecked")
    Collection<Polygon> polygons = polygonizer.getPolygons();
    return geometry.getFactory().buildGeometry(new ArrayList<>(polygons));
  }

  private Geometry removeSmallHoles(Geometry geometry, double areaThreshold) {
    GeometryFactory factory = geometry.getFactory();
    if (geometry instanceof Polygon polygon) {
      List<LinearRing> retained = new ArrayList<>();
      for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
        LinearRing ring = (LinearRing) polygon.getInteriorRingN(index);
        if (factory.createPolygon(ring).getArea() > areaThreshold) {
          retained.add(ring);
        }
      }
      return factory.createPolygon(
          (LinearRing) polygon.getExteriorRing(), retained.toArray(new LinearRing[0]));
    }
    if (geometry instanceof MultiPolygon multiPolygon) {
      Polygon[] polygons = new Polygon[multiPolygon.getNumGeometries()];
      for (int index = 0; index < multiPolygon.getNumGeometries(); index++) {
        polygons[index] =
            (Polygon) removeSmallHoles(multiPolygon.getGeometryN(index), areaThreshold);
      }
      return factory.createMultiPolygon(polygons);
    }
    return geometry;
  }

  private Geometry removeAllHoles(Geometry geometry) {
    GeometryFactory factory = geometry.getFactory();
    if (geometry instanceof Polygon polygon) {
      return factory.createPolygon((LinearRing) polygon.getExteriorRing());
    }
    if (geometry instanceof MultiPolygon multiPolygon) {
      Polygon[] polygons = new Polygon[multiPolygon.getNumGeometries()];
      for (int index = 0; index < multiPolygon.getNumGeometries(); index++) {
        polygons[index] = (Polygon) removeAllHoles(multiPolygon.getGeometryN(index));
      }
      return factory.createMultiPolygon(polygons);
    }
    return geometry;
  }

  private Geometry linearReferencing(Geometry geometry, double distance) throws HopException {
    if (!(geometry instanceof LineString lineString)) {
      throw new HopException("Linear referencing requires a LINESTRING input geometry");
    }
    Coordinate coordinate = new LengthIndexedLine(lineString).extractPoint(distance);
    return geometry.getFactory().createPoint(coordinate);
  }

  private Geometry snap(Geometry primary, Geometry secondary, double distance) throws HopException {
    if (secondary == null) {
      throw new HopException("Snap requires a secondary geometry");
    }
    GeometryFieldValueHelper.requireCompatibleSrid(primary, secondary, "Geometry SRIDs must match");
    GeometrySnapper snapper = new GeometrySnapper(primary);
    return snapper.snapTo(secondary, distance);
  }

  private Geometry split(Geometry primary, Geometry secondary) throws HopException {
    if (secondary == null) {
      throw new HopException("Split requires a secondary geometry");
    }
    GeometryFieldValueHelper.requireCompatibleSrid(primary, secondary, "Geometry SRIDs must match");

    GeometryFactory factory = primary.getFactory();
    if (primary instanceof Polygonal && secondary instanceof Lineal) {
      Polygonizer polygonizer = new Polygonizer();
      List<Geometry> boundaries = new ArrayList<>();
      boundaries.add(primary.getBoundary());
      boundaries.add(secondary);
      Geometry union = UnaryUnionOp.union(boundaries);
      polygonizer.add(union);

      @SuppressWarnings("unchecked")
      Collection<Polygon> polygons = polygonizer.getPolygons();
      List<Geometry> selected = new ArrayList<>();
      for (Polygon polygon : polygons) {
        if (primary.contains(polygon.getInteriorPoint())) {
          selected.add(polygon);
        }
      }
      return factory.buildGeometry(selected);
    }
    if (primary instanceof Lineal && secondary instanceof Lineal) {
      return primary.difference(secondary);
    }
    if (primary instanceof Lineal && secondary instanceof Puntal) {
      List<LineString> lineStrings = new ArrayList<>();
      if (primary instanceof LineString lineString) {
        addSplitLines(lineStrings, secondary.getCoordinates(), lineString);
      } else if (primary instanceof MultiLineString multiLineString) {
        for (int index = 0; index < multiLineString.getNumGeometries(); index++) {
          addSplitLines(
              lineStrings,
              secondary.getCoordinates(),
              (LineString) multiLineString.getGeometryN(index));
        }
      }
      return factory.buildGeometry(new ArrayList<>(lineStrings));
    }
    throw new HopException(
        "Split supports polygon/line, line/line, or line/point input combinations only");
  }

  private void addSplitLines(
      List<LineString> target, Coordinate[] splitCoordinates, LineString lineString) {
    LengthIndexedLine indexedLine = new LengthIndexedLine(lineString);
    java.util.TreeSet<Double> indexes = new java.util.TreeSet<>();
    indexes.add(indexedLine.getStartIndex());
    indexes.add(indexedLine.getEndIndex());

    for (Coordinate coordinate : splitCoordinates) {
      if (lineString.getFactory().createPoint(coordinate).distance(lineString) <= 1e-6) {
        indexes.add(indexedLine.project(coordinate));
      }
    }

    Double[] values = indexes.toArray(new Double[0]);
    for (int index = 0; index < values.length - 1; index++) {
      Geometry part = indexedLine.extractLine(values[index], values[index + 1]);
      if (part instanceof LineString linePart && !linePart.isEmpty()) {
        target.add(linePart);
      }
    }
  }

  private Geometry normalize(Geometry source, Geometry result) {
    return GeometryFieldValueHelper.preserveSrid(source, result);
  }

  private double requireDistance(Double distance, String label) throws HopException {
    if (distance == null) {
      throw new HopException(label + " is required");
    }
    return distance;
  }

  private double requirePrecisionScale(Double precisionScale) throws HopException {
    if (precisionScale == null) {
      throw new HopException("Precision scale is required");
    }
    if (precisionScale <= 0.0d) {
      throw new HopException("Precision scale must be a positive number");
    }
    return precisionScale;
  }
}
