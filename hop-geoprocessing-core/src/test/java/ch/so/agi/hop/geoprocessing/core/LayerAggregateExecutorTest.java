package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class LayerAggregateExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final LayerAggregateExecutor executor = new LayerAggregateExecutor();

  @Test
  void collectPreservesAllInputGeometries() throws Exception {
    Geometry collected = executor.execute("collect", List.of(square(0, 0, 1), square(2, 0, 1)));

    assertThat(collected.getNumGeometries()).isEqualTo(2);
  }

  @Test
  void unaryUnionMergesAdjacentPolygons() throws Exception {
    Geometry union = executor.execute("unary_union", List.of(square(0, 0, 1), square(1, 0, 1)));

    assertThat(union.getArea()).isEqualTo(2.0);
  }

  @Test
  void coverageUnionMergesAdjacentPolygons() throws Exception {
    Geometry union = executor.execute("coverage_union", List.of(square(0, 0, 1), square(1, 0, 1)));

    assertThat(union.getArea()).isEqualTo(2.0);
  }

  private Polygon square(double minX, double minY, double size) {
    return geometryFactory.createPolygon(
        new Coordinate[] {
          new Coordinate(minX, minY),
          new Coordinate(minX + size, minY),
          new Coordinate(minX + size, minY + size),
          new Coordinate(minX, minY + size),
          new Coordinate(minX, minY)
        });
  }
}
