package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class GeometryOperationExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final GeometryOperationExecutor executor = new GeometryOperationExecutor();

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
        executor.execute("remove_holes", multiPolygon, null, 2.0, null, null, null, false);

    assertThat(result).hasSize(1);
    assertThat(((MultiPolygon) result.get(0)).getGeometryN(0)).isInstanceOf(Polygon.class);
    assertThat(((Polygon) ((MultiPolygon) result.get(0)).getGeometryN(0)).getNumInteriorRing())
        .isZero();
  }
}
