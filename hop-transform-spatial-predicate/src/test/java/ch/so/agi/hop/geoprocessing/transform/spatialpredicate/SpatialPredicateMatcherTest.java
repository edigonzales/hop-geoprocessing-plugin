package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.SpatialIndexBuilder;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateExecutor;
import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class SpatialPredicateMatcherTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final SpatialPredicateExecutor executor = new SpatialPredicateExecutor();
  private final RowMeta rowMeta = new RowMeta();

  @Test
  void disjointReturnsFalseWhenAnyCandidateIntersectsPrimaryGeometry() throws Exception {
    Polygon primary = polygon(0, 0, 10, 10);
    SpatialPredicateMatcher matcher =
        new SpatialPredicateMatcher(
            executor,
            new SpatialIndexBuilder()
                .build(
                    List.of(feature(1L, polygon(5, 5, 15, 15)), feature(2L, polygon(100, 100, 110, 110))),
                    rowMeta,
                    false),
            "disjoint");

    assertThat(matcher.matches(primary, null)).isFalse();
  }

  @Test
  void disjointKeepsEnvelopeBasedCandidateSelection() throws Exception {
    Polygon primary = polygon(0, 0, 10, 10);
    SpatialPredicateMatcher matcher =
        new SpatialPredicateMatcher(
            executor,
            new SpatialIndexBuilder()
                .build(List.of(feature(1L, polygon(100, 100, 110, 110))), rowMeta, false),
            "disjoint");

    assertThat(matcher.matches(primary, null)).isTrue();
  }

  @Test
  void matchingFeaturesReturnsOnlyIntersectingCandidates() throws Exception {
    Polygon primary = polygon(0, 0, 10, 10);
    SpatialPredicateMatcher matcher =
        new SpatialPredicateMatcher(
            executor,
            new SpatialIndexBuilder()
                .build(
                    List.of(feature(1L, polygon(5, 5, 15, 15)), feature(2L, polygon(20, 20, 30, 30))),
                    rowMeta,
                    false),
            "intersects");

    assertThat(matcher.matchingFeatures(primary, null))
        .extracting(FeatureRow::sourceId)
        .containsExactly(1L);
  }

  @Test
  void minimumDistanceIncludesTheBoundaryAndUsesTheNearestFeature() throws Exception {
    Geometry primary = geometryFactory.createPoint(new Coordinate(0, 0));
    Geometry near = geometryFactory.createPoint(new Coordinate(3, 0));
    Geometry far = geometryFactory.createPoint(new Coordinate(30, 0));
    var cache = new SpatialIndexBuilder().build(
        List.of(feature(1, near), feature(2, far)), rowMeta, false);
    var matcher = new SpatialPredicateMatcher(executor, cache, "distance_gte");

    assertThat(matcher.matches(primary, 2.0)).isTrue();
    assertThat(matcher.matches(primary, 3.0)).isTrue();
    assertThat(matcher.matches(primary, 4.0)).isFalse();
    assertThat(matcher.matches(primary, 0.0)).isTrue();
    assertThat(matcher.matches(null, 0.0)).isFalse();
  }

  @Test
  void checksLayerSridBeforeQueryForBooleanAndJoin() throws Exception {
    Geometry primary = geometryFactory.createPoint(new Coordinate(0, 0));
    primary.setSRID(2056);
    Geometry secondary = geometryFactory.createPoint(new Coordinate(30, 0));
    secondary.setSRID(4326);
    var cache = new SpatialIndexBuilder().build(List.of(feature(1, secondary)), rowMeta, false);
    var matcher = new SpatialPredicateMatcher(executor, cache, "intersects");

    assertThatThrownBy(() -> matcher.matches(primary, null)).hasMessageContaining("2056 vs 4326");
    assertThatThrownBy(() -> matcher.matchingFeatures(primary, null)).hasMessageContaining("2056 vs 4326");
    primary.setSRID(0);
    assertThat(matcher.matches(primary, null)).isFalse();
  }

  @Test
  void validatesThresholdsEvenForAnEmptyLayer() throws Exception {
    Geometry primary = geometryFactory.createPoint(new Coordinate(0, 0));
    var cache = new SpatialIndexBuilder().build(List.of(), rowMeta, false);
    var matcher = new SpatialPredicateMatcher(executor, cache, "distance_gte");
    assertThat(matcher.matches(primary, 3.0)).isTrue();
    assertThat(matcher.matches(geometryFactory.createPoint(), 3.0)).isFalse();
    for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThatThrownBy(() -> matcher.matches(primary, invalid))
          .hasMessageContaining("finite and nonnegative");
    }
  }

  private FeatureRow feature(long sourceId, Geometry geometry) {
    return new FeatureRow(
        new Object[] {sourceId}, geometry, geometry.getEnvelopeInternal(), geometry.getSRID(), sourceId);
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
