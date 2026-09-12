package ch.so.agi.hop.geoprocessing.core;

import com.atolcd.hop.gis.geometry.curve.*;
import java.util.*;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * Group-wide chord construction. Shared analytic edges are subdivided before approximation. No
 * snapping or repair of input boundaries is performed.
 */
public final class CoverageLinearizer {
  private CoverageLinearizer() {}

  public record Grid(double resolution, double xOrigin, double yOrigin) {
    public Grid {
      if (!Double.isFinite(resolution)
          || resolution <= 0
          || !Double.isFinite(xOrigin)
          || !Double.isFinite(yOrigin))
        throw new IllegalArgumentException("Invalid target XY grid");
    }

    Coordinate apply(Coordinate p) {
      double scale = 1 / resolution;
      double x = (p.x - xOrigin) * scale, y = (p.y - yOrigin) * scale;
      if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0 || x > 9e15 || y > 9e15)
        throw new IllegalArgumentException("Coordinate outside target grid domain");
      return new Coordinate(Math.round(x) / scale + xOrigin, Math.round(y) / scale + yOrigin);
    }
  }

  private record XY(double x, double y) implements Comparable<XY> {
    XY(Coordinate c) {
      this(c.x == 0 ? 0 : c.x, c.y == 0 ? 0 : c.y);
    }

    Coordinate coordinate() {
      return new Coordinate(x, y);
    }

    public int compareTo(XY p) {
      int c = Double.compare(x, p.x);
      return c == 0 ? Double.compare(y, p.y) : c;
    }
  }

  private record Circle(double x, double y, double r) {
    double angle(Coordinate p) {
      return Math.atan2(p.y - y, p.x - x);
    }

    Coordinate at(double angle) {
      return new Coordinate(x + r * Math.cos(angle), y + r * Math.sin(angle));
    }
  }

  private record Edge(Coordinate a, Coordinate b, Circle circle, double sweep) {}

  private record Key(Circle circle, XY a, XY b, boolean major, boolean counterclockwise) {}

  private static double positive(double a) {
    double t = a % (2 * Math.PI);
    return t < 0 ? t + 2 * Math.PI : t;
  }

  public static Geometry[] linearize(List<Geometry> input, double maxError, Grid grid) {
    if (!Double.isFinite(maxError) || maxError <= 0)
      throw new IllegalArgumentException("Maximum chord deviation must be positive and finite");
    double error = maxError - (grid == null ? 0 : grid.resolution() / Math.sqrt(2));
    if (error <= 0)
      throw new IllegalArgumentException("Target grid is too coarse for the requested deviation");
    List<List<List<Edge>>> shapes = new ArrayList<>();
    Map<Circle, SortedSet<XY>> circleNodes = new HashMap<>();
    SortedSet<XY> nodes = new TreeSet<>();
    Integer srid = null;
    for (Geometry geometry : input) {
      if (geometry == null
          || geometry.isEmpty()
          || !(geometry instanceof Polygon || geometry instanceof MultiPolygon))
        throw new IllegalArgumentException("Coverage requires non-empty polygons");
      if (srid != null && srid != geometry.getSRID())
        throw new IllegalArgumentException("Coverage SRIDs differ");
      srid = geometry.getSRID();
      List<List<Edge>> rings = new ArrayList<>();
      for (int i = 0; i < geometry.getNumGeometries(); i++) {
        Polygon polygon = (Polygon) geometry.getGeometryN(i);
        List<LineString> lines = new ArrayList<>();
        if (polygon instanceof CurvePolygon p) lines.addAll(p.getCurveRings());
        else {
          lines.add(polygon.getExteriorRing());
          for (int j = 0; j < polygon.getNumInteriorRing(); j++)
            lines.add(polygon.getInteriorRingN(j));
        }
        for (LineString line : lines) {
          List<Edge> edges = new ArrayList<>();
          extract(line, edges);
          rings.add(edges);
          for (Edge e : edges) {
            nodes.add(new XY(e.a));
            nodes.add(new XY(e.b));
            if (e.circle != null) {
              circleNodes.computeIfAbsent(e.circle, k -> new TreeSet<>()).add(new XY(e.a));
              circleNodes.get(e.circle).add(new XY(e.b));
            }
          }
        }
      }
      shapes.add(rings);
    }
    // Reject ambiguous nearly equal circles instead of merging slightly displaced boundaries.
    List<Circle> circles = new ArrayList<>(circleNodes.keySet());
    for (int i = 0; i < circles.size(); i++)
      for (int j = i + 1; j < circles.size(); j++) {
        Circle a = circles.get(i), b = circles.get(j);
        double eps =
            32 * Math.ulp(Math.max(1, Math.max(Math.max(Math.abs(a.x), Math.abs(a.y)), a.r)));
        if (Math.hypot(a.x - b.x, a.y - b.y) <= eps && Math.abs(a.r - b.r) <= eps)
          throw new IllegalArgumentException(
              "Numerically ambiguous coincident circles; normalize exact curve definitions first");
      }
    Map<Key, List<Coordinate>> chords = new HashMap<>();
    Geometry[] output = new Geometry[input.size()], beforeGrid = new Geometry[input.size()];
    for (int index = 0; index < input.size(); index++) {
      Geometry source = input.get(index);
      GeometryFactory f = source.getFactory();
      List<Polygon> polygons = new ArrayList<>(), originalPolygons = new ArrayList<>();
      int ringIndex = 0;
      for (int p = 0; p < source.getNumGeometries(); p++) {
        Polygon src = (Polygon) source.getGeometryN(p);
        int count =
            src instanceof CurvePolygon cp
                ? cp.getCurveRings().size()
                : src.getNumInteriorRing() + 1;
        List<LinearRing> rings = new ArrayList<>(), originalRings = new ArrayList<>();
        for (int r = 0; r < count; r++) {
          List<Coordinate> coords = new ArrayList<>();
          for (Edge edge : shapes.get(index).get(ringIndex++)) {
            List<Coordinate> cuts = new ArrayList<>();
            cuts.add(edge.a);
            cuts.add(edge.b);
            Iterable<XY> candidates = edge.circle == null ? nodes : circleNodes.get(edge.circle);
            for (XY node : candidates) {
              Coordinate c = node.coordinate();
              double t = fraction(edge, c);
              if (t > 0 && t < 1) cuts.add(c);
            }
            cuts.sort(Comparator.comparingDouble(c -> fraction(edge, c)));
            List<Coordinate> unique = new ArrayList<>();
            for (Coordinate c : cuts)
              if (unique.isEmpty() || !unique.getLast().equals2D(c)) unique.add(c);
            for (int k = 0; k < unique.size() - 1; k++) {
              Coordinate a = unique.get(k), b = unique.get(k + 1);
              double ta = fraction(edge, a), tb = fraction(edge, b);
              double sweep = edge.sweep * (tb - ta);
              boolean reverse = new XY(a).compareTo(new XY(b)) > 0;
              Key key =
                  new Key(
                      edge.circle,
                      new XY(reverse ? b : a),
                      new XY(reverse ? a : b),
                      Math.abs(sweep) > Math.PI,
                      (reverse ? -sweep : sweep) > 0);
              List<Coordinate> part =
                  chords.computeIfAbsent(
                      key,
                      unused ->
                          sample(
                              edge.circle,
                              reverse ? b : a,
                              reverse ? a : b,
                              reverse ? -sweep : sweep,
                              error));
              for (int n = 0; n < part.size(); n++) {
                if (!coords.isEmpty() && n == 0) continue;
                Coordinate xy = part.get(reverse ? part.size() - 1 - n : n);
                double t = ta + (tb - ta) * n / (part.size() - 1.0);
                coords.add(ordinate(xy, edge.a, edge.b, t));
              }
            }
          }
          if (coords.size() < 4 || !coords.getFirst().equals2D(coords.getLast()))
            throw new IllegalArgumentException("Collapsed or unclosed coverage ring");
          originalRings.add(f.createLinearRing(coords.toArray(Coordinate[]::new)));
          if (grid != null)
            for (int n = 0; n < coords.size(); n++) {
              Coordinate c = coords.get(n), q = grid.apply(c);
              coords.set(n, ordinate(q, c, c, 0));
              if (n > 0 && coords.get(n - 1).equals2D(coords.get(n)))
                throw new IllegalArgumentException(
                    "Target grid collapses a boundary segment; use a finer grid");
            }
          rings.add(f.createLinearRing(coords.toArray(Coordinate[]::new)));
        }
        polygons.add(
            f.createPolygon(
                rings.getFirst(), rings.subList(1, rings.size()).toArray(LinearRing[]::new)));
        originalPolygons.add(
            f.createPolygon(
                originalRings.getFirst(),
                originalRings.subList(1, originalRings.size()).toArray(LinearRing[]::new)));
      }
      output[index] =
          source instanceof MultiPolygon
              ? f.createMultiPolygon(polygons.toArray(Polygon[]::new))
              : polygons.getFirst();
      beforeGrid[index] =
          source instanceof MultiPolygon
              ? f.createMultiPolygon(originalPolygons.toArray(Polygon[]::new))
              : originalPolygons.getFirst();
      output[index].setSRID(source.getSRID());
    }
    validate(beforeGrid);
    validate(output);
    if (holes(UnaryUnionOp.union(Arrays.asList(beforeGrid)))
        != holes(UnaryUnionOp.union(Arrays.asList(output))))
      throw new IllegalArgumentException("Target grid changes coverage holes");
    for (int i = 0; i < output.length; i++)
      for (int j = i + 1; j < output.length; j++) {
        if (beforeGrid[i].getBoundary().intersects(beforeGrid[j].getBoundary())
            != output[i].getBoundary().intersects(output[j].getBoundary()))
          throw new IllegalArgumentException("Target grid changes polygon adjacency");
      }
    return output;
  }

  private static void validate(Geometry[] coverage) {
    for (int i = 0; i < coverage.length; i++)
      if (!coverage[i].isValid() || coverage[i].getArea() == 0)
        throw new IllegalArgumentException(
            "Invalid linearized polygon at row " + i + "; reduce deviation or use a finer grid");
    if (CoverageValidator.hasInvalidResult(CoverageValidator.validate(coverage)))
      throw new IllegalArgumentException(
          "Invalid linearized coverage; reduce deviation or use a finer grid");
  }

  private static int holes(Geometry geometry) {
    int count = 0;
    for (int i = 0; i < geometry.getNumGeometries(); i++) {
      Polygon p = (Polygon) geometry.getGeometryN(i);
      count += p.getNumInteriorRing();
    }
    return count;
  }

  private static Coordinate ordinate(Coordinate xy, Coordinate a, Coordinate b, double t) {
    double z =
        Double.isNaN(a.getZ()) || Double.isNaN(b.getZ())
            ? Double.NaN
            : a.getZ() + (b.getZ() - a.getZ()) * t;
    double m =
        Double.isNaN(a.getM()) || Double.isNaN(b.getM())
            ? Double.NaN
            : a.getM() + (b.getM() - a.getM()) * t;
    if (!Double.isNaN(m) && Double.isNaN(z)) return new CoordinateXYM(xy.x, xy.y, m);
    return Double.isNaN(m) ? new Coordinate(xy.x, xy.y, z) : new CoordinateXYZM(xy.x, xy.y, z, m);
  }

  private static List<Coordinate> sample(
      Circle c, Coordinate a, Coordinate b, double sweep, double error) {
    if (c == null) return List.of(a.copy(), b.copy());
    double step = Math.min(Math.PI / 2, 4 * Math.asin(Math.sqrt(Math.min(1, error / (2 * c.r)))));
    double needed = Math.ceil(Math.abs(sweep) / step);
    if (!Double.isFinite(needed) || needed > 1_000_000)
      throw new IllegalArgumentException("Too many chord segments requested");
    int n = Math.max(1, (int) needed);
    List<Coordinate> result = new ArrayList<>();
    double angle = c.angle(a);
    for (int i = 0; i <= n; i++)
      result.add(i == 0 ? a.copy() : i == n ? b.copy() : c.at(angle + sweep * i / n));
    return result;
  }

  private static double fraction(Edge e, Coordinate p) {
    if (p.equals2D(e.a)) return 0;
    if (p.equals2D(e.b)) return 1;
    if (e.circle == null) {
      if (Orientation.index(e.a, e.b, p) != 0) return -1;
      return Math.abs(e.b.x - e.a.x) >= Math.abs(e.b.y - e.a.y)
          ? (p.x - e.a.x) / (e.b.x - e.a.x)
          : (p.y - e.a.y) / (e.b.y - e.a.y);
    }
    double a = e.circle.angle(e.a), b = e.circle.angle(p);
    return (e.sweep >= 0 ? positive(b - a) : positive(a - b)) / Math.abs(e.sweep);
  }

  private static void extract(LineString line, List<Edge> edges) {
    if (line instanceof CompoundCurve c) {
      for (var part : c.getComponents()) extract(part, edges);
      return;
    }
    if (line instanceof CircularString c) {
      for (ArcSegment arc : c.getArcSegments()) {
        Coordinate a = arc.getStartPoint(), m = arc.getMidPoint(), b = arc.getEndPoint();
        Circle circle = circle(a, m, b);
        if (circle == null) {
          edges.add(new Edge(a, m, null, 0));
          edges.add(new Edge(m, b, null, 0));
          continue;
        }
        double start = circle.angle(a), mid = circle.angle(m), end = circle.angle(b);
        double sweep = a.equals2D(b) ? 2 * Math.PI : positive(end - start);
        if (!a.equals2D(b) && positive(mid - start) > sweep) sweep -= 2 * Math.PI;
        double first = sweep >= 0 ? positive(mid - start) : -positive(start - mid);
        edges.add(new Edge(a, m, circle, first));
        edges.add(new Edge(m, b, circle, sweep - first));
      }
    } else {
      Coordinate[] points = line.getCoordinates();
      for (int i = 1; i < points.length; i++) {
        if (points[i - 1].equals2D(points[i]))
          throw new IllegalArgumentException("Zero length coverage edge");
        edges.add(new Edge(points[i - 1], points[i], null, 0));
      }
    }
  }

  private static Circle circle(Coordinate a, Coordinate b, Coordinate c) {
    if (a.equals2D(c)) return new Circle((a.x + b.x) / 2, (a.y + b.y) / 2, a.distance(b) / 2);
    // Sort defining points so reverse representations calculate identically.
    Coordinate[] p = {a, b, c};
    Arrays.sort(p);
    a = p[0];
    b = p[1];
    c = p[2];
    double ux = b.x - a.x,
        uy = b.y - a.y,
        vx = c.x - a.x,
        vy = c.y - a.y,
        d = 2 * (ux * vy - uy * vx);
    if (d == 0) return null;
    double u2 = ux * ux + uy * uy,
        v2 = vx * vx + vy * vy,
        x = (u2 * vy - v2 * uy) / d,
        y = (v2 * ux - u2 * vx) / d;
    return new Circle(a.x + x, a.y + y, Math.hypot(x, y));
  }
}
