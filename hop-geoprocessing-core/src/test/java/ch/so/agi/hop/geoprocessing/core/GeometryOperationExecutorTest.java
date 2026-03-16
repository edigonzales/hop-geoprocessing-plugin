package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;

class GeometryOperationExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final GeometryOperationExecutor executor = new GeometryOperationExecutor();
  private final WKTReader wktReader = new WKTReader(geometryFactory);

  @Test
  void bufferUsesDistanceAndReturnsGeometry() throws Exception {
    Point point = geometryFactory.createPoint(new Coordinate(0, 0));

    List<Geometry> result = executor.execute("buffer", point, null, 2.0, null, null, null, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getArea()).isGreaterThan(10.0);
  }

  @Test
  void bufferAndCentroidWorkForForeignGeometryValueMeta() throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new GeometryValueParserTest.ForeignGeometryStringBridgeMeta());

    try (GeometryValueParserTest.ForeignGeometryHandle foreignGeometry =
        GeometryValueParserTest.ForeignGeometryHandle.create("POINT (2 3)", 2056)) {
      Geometry geometry =
          GeometryFieldValueHelper.readGeometry(rowMeta, 0, new Object[] {foreignGeometry.geometry()});

      List<Geometry> buffered =
          executor.execute("buffer", geometry, null, 1.5, null, null, null, false);
      List<Geometry> centroid = executor.execute("centroid", geometry, null, null, null, null, null, false);

      assertThat(buffered).hasSize(1);
      assertThat(buffered.get(0)).isNotNull();
      assertThat(buffered.get(0).getArea()).isGreaterThan(0.0);
      assertThat(buffered.get(0).getSRID()).isEqualTo(2056);

      assertThat(centroid).hasSize(1);
      assertThat(centroid.get(0)).isNotNull();
      assertThat(centroid.get(0).getGeometryType()).isEqualTo("Point");
      assertThat(centroid.get(0).getSRID()).isEqualTo(2056);
    }
  }

  @Test
  void explodeReturnsEachPartOfAMultiGeometry() throws Exception {
    Polygon first =
        geometryFactory.createPolygon(
            new Coordinate[] {
              new Coordinate(0, 0),
              new Coordinate(1, 0),
              new Coordinate(1, 1),
              new Coordinate(0, 1),
              new Coordinate(0, 0)
            });
    Polygon second =
        geometryFactory.createPolygon(
            new Coordinate[] {
              new Coordinate(2, 0),
              new Coordinate(3, 0),
              new Coordinate(3, 1),
              new Coordinate(2, 1),
              new Coordinate(2, 0)
            });

    List<Geometry> result =
        executor.execute(
            "explode",
            geometryFactory.createMultiPolygon(new Polygon[] {first, second}),
            null,
            null,
            null,
            null,
            null,
            false);

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(geometry -> geometry instanceof Polygon);
  }

  @Test
  void removeHolesHandlesMultiPolygon() throws Exception {
    LinearRing shell =
        geometryFactory.createLinearRing(
            new Coordinate[] {
              new Coordinate(0, 0),
              new Coordinate(10, 0),
              new Coordinate(10, 10),
              new Coordinate(0, 10),
              new Coordinate(0, 0)
            });
    LinearRing hole =
        geometryFactory.createLinearRing(
            new Coordinate[] {
              new Coordinate(1, 1),
              new Coordinate(2, 1),
              new Coordinate(2, 2),
              new Coordinate(1, 2),
              new Coordinate(1, 1)
            });
    Polygon polygonWithHole = geometryFactory.createPolygon(shell, new LinearRing[] {hole});
    MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(new Polygon[] {polygonWithHole});

    List<Geometry> result =
        executor.execute("remove_small_holes", multiPolygon, null, 2.0, null, null, null, false);

    assertThat(result).hasSize(1);
    assertThat(((MultiPolygon) result.get(0)).getGeometryN(0)).isInstanceOf(Polygon.class);
    assertThat(((Polygon) ((MultiPolygon) result.get(0)).getGeometryN(0)).getNumInteriorRing())
        .isZero();
  }

  @Test
  void envelopeAndMinimumBoundingRectangleDiffer() throws Exception {
    Geometry diamond = wktReader.read("POLYGON ((0 1, 1 2, 2 1, 1 0, 0 1))");

    List<Geometry> envelope =
        executor.execute("envelope", diamond, null, null, null, null, null, false);
    List<Geometry> minimumBoundingRectangle =
        executor.execute(
            "minimum_bounding_rectangle", diamond, null, null, null, null, null, false);
    List<Geometry> minimumDiameter =
        executor.execute("minimum_diameter", diamond, null, null, null, null, null, false);

    assertThat(envelope.get(0).getArea()).isEqualTo(4.0);
    assertThat(minimumBoundingRectangle.get(0).getArea()).isEqualTo(2.0);
    assertThat(minimumDiameter.get(0).getLength()).isGreaterThan(0.0);
  }

  @Test
  void removeAllHolesRemovesEveryInteriorRing() throws Exception {
    Geometry polygon =
        wktReader.read(
            "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (1 1, 2 1, 2 2, 1 2, 1 1), (4 4, 8 4, 8 8, 4 8, 4 4))");

    List<Geometry> thresholdBased =
        executor.execute("remove_small_holes", polygon, null, 2.0, null, null, null, false);
    List<Geometry> removeAll =
        executor.execute("remove_all_holes", polygon, null, null, null, null, null, false);

    assertThat(((Polygon) thresholdBased.get(0)).getNumInteriorRing()).isEqualTo(1);
    assertThat(((Polygon) removeAll.get(0)).getNumInteriorRing()).isZero();
  }

  @Test
  void simplifyTopologyReturnsAValidGeometry() throws Exception {
    Geometry polygon =
        wktReader.read(
            "POLYGON ((0 0, 10 0, 10 10, 6 10, 6 4, 4 4, 4 10, 0 10, 0 0))");

    List<Geometry> result =
        executor.execute("simplify_topology", polygon, null, 1.5, null, null, null, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isValid()).isTrue();
  }

  @Test
  void fixGeometryRepairsSelfIntersectingPolygon() throws Exception {
    Geometry invalid = wktReader.read("POLYGON ((0 0, 4 4, 4 0, 0 4, 0 0))");

    List<Geometry> result =
        executor.execute("fix_geometry", invalid, null, null, null, null, null, false);

    assertThat(invalid.isValid()).isFalse();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).isValid()).isTrue();
  }

  @Test
  void reducePrecisionRoundsCoordinatesAndPreservesSrid() throws Exception {
    Geometry line = wktReader.read("LINESTRING (1.24 2.26, 3.74 4.76)");
    line.setSRID(2056);

    List<Geometry> result =
        executor.execute(
            "reduce_precision",
            line,
            null,
            null,
            OverlayMode.STANDARD,
            10.0,
            null,
            null,
            null,
            false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCoordinates()[0].x).isEqualTo(1.2);
    assertThat(result.get(0).getCoordinates()[0].y).isEqualTo(2.3);
    assertThat(result.get(0).getSRID()).isEqualTo(2056);
  }

  @Test
  void snapToSelfChangesNearSelfTouchingLinework() throws Exception {
    Geometry line = wktReader.read("LINESTRING (0 0, 5 0, 5 0.001, 10 0)");

    List<Geometry> result =
        executor.execute("snap_to_self", line, null, 0.01, null, null, null, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).toText()).isNotEqualTo(line.toText());
  }

  @Test
  void fixedPrecisionOverlayChangesResultComparedToStandardOverlay() throws Exception {
    Geometry left = wktReader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry right = wktReader.read("POLYGON ((5.0001 0, 15.0001 0, 15.0001 10, 5.0001 10, 5.0001 0))");
    Geometry expectedStandard = OverlayNG.overlay(left, right, OverlayNG.INTERSECTION);

    List<Geometry> standard =
        executor.execute(
            "intersection",
            left,
            right,
            null,
            OverlayMode.STANDARD,
            null,
            null,
            null,
            null,
            false);
    List<Geometry> fixedPrecision =
        executor.execute(
            "intersection",
            left,
            right,
            null,
            OverlayMode.FIXED_PRECISION,
            1.0,
            null,
            null,
            null,
            false);

    assertThat(standard).hasSize(1);
    assertThat(fixedPrecision).hasSize(1);
    assertThat(standard.get(0).equalsTopo(expectedStandard)).isTrue();
    assertThat(standard.get(0).getArea()).isEqualTo(49.999);
    assertThat(fixedPrecision.get(0).getArea()).isEqualTo(50.0);
  }
}
