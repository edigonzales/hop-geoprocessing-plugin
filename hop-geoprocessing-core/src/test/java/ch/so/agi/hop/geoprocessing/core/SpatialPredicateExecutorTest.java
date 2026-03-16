package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.operation.relateng.RelatePredicate;

class SpatialPredicateExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final SpatialPredicateExecutor executor = new SpatialPredicateExecutor();

  @Test
  void intersectsAndContainsEvaluateAsExpected() throws Exception {
    Polygon outer = polygon(0, 0, 10, 10);
    Polygon inner = polygon(2, 2, 4, 4);

    assertThat(executor.test("intersects", outer, inner, null))
        .isEqualTo(RelateNG.relate(outer, inner, RelatePredicate.intersects()));
    assertThat(executor.test("contains", outer, inner, null))
        .isEqualTo(RelateNG.relate(outer, inner, RelatePredicate.contains()));
    assertThat(executor.test("within", inner, outer, null))
        .isEqualTo(RelateNG.relate(inner, outer, RelatePredicate.within()));
  }

  @Test
  void touchesCrossesOverlapsAndDisjointUseRelateNgPredicates() throws Exception {
    Polygon left = polygon(0, 0, 2, 2);
    Polygon touching = polygon(2, 0, 4, 2);
    Polygon overlapping = polygon(1, 0, 3, 2);
    Polygon farAway = polygon(10, 10, 12, 12);
    LineString rising = line(0, 0, 4, 4);
    LineString falling = line(0, 4, 4, 0);

    assertThat(executor.test("touches", left, touching, null))
        .isEqualTo(RelateNG.relate(left, touching, RelatePredicate.touches()));
    assertThat(executor.test("overlaps", left, overlapping, null))
        .isEqualTo(RelateNG.relate(left, overlapping, RelatePredicate.overlaps()));
    assertThat(executor.test("disjoint", left, farAway, null))
        .isEqualTo(RelateNG.relate(left, farAway, RelatePredicate.disjoint()));
    assertThat(executor.test("crosses", rising, falling, null))
        .isEqualTo(RelateNG.relate(rising, falling, RelatePredicate.crosses()));
  }

  @Test
  void preparedPrimaryGeometryCanBeReusedAcrossTopologicalPredicates() throws Exception {
    Polygon outer = polygon(0, 0, 10, 10);
    Polygon inner = polygon(2, 2, 4, 4);
    Polygon farAway = polygon(20, 20, 22, 22);

    var preparedPrimary = executor.prepare(outer);

    assertThat(executor.test("contains", preparedPrimary, outer, inner, null)).isTrue();
    assertThat(executor.test("intersects", preparedPrimary, outer, inner, null)).isTrue();
    assertThat(executor.test("disjoint", preparedPrimary, outer, farAway, null)).isTrue();
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

  private LineString line(double startX, double startY, double endX, double endY) {
    return geometryFactory.createLineString(
        new Coordinate[] {new Coordinate(startX, startY), new Coordinate(endX, endY)});
  }
}
