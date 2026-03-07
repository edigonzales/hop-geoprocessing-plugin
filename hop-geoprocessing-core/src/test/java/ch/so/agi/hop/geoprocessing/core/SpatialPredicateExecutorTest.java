package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class SpatialPredicateExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final SpatialPredicateExecutor executor = new SpatialPredicateExecutor();

  @Test
  void intersectsAndContainsEvaluateAsExpected() throws Exception {
    Polygon outer = polygon(0, 0, 10, 10);
    Polygon inner = polygon(2, 2, 4, 4);

    assertThat(executor.test("intersects", outer, inner, null)).isTrue();
    assertThat(executor.test("contains", outer, inner, null)).isTrue();
    assertThat(executor.test("within", inner, outer, null)).isTrue();
  }

  @Test
  void distanceGreaterOrEqualActsAsMinDistanceCheck() throws Exception {
    Polygon left = polygon(0, 0, 1, 1);
    Polygon right = polygon(5, 0, 6, 1);

    assertThat(executor.test("distance_gte", left, right, 2.0)).isTrue();
    assertThat(executor.test("distance_lte", left, right, 2.0)).isFalse();
  }

  @Test
  void searchEnvelopeExpandsForDistancePredicates() throws Exception {
    Polygon polygon = polygon(0, 0, 1, 1);

    Envelope envelope = executor.searchEnvelope("distance_lte", polygon, 2.0);

    assertThat(envelope.getMinX()).isEqualTo(-2.0);
    assertThat(envelope.getMaxX()).isEqualTo(3.0);
  }

  private Polygon polygon(double minX, double minY, double maxX, double maxY) {
    return geometryFactory.createPolygon(
        new Coordinate[] {
          new Coordinate(minX, minY),
          new Coordinate(maxX, minY),
          new Coordinate(maxX, maxY),
          new Coordinate(minX, maxY),
          new Coordinate(minX, minY)
        });
  }
}
