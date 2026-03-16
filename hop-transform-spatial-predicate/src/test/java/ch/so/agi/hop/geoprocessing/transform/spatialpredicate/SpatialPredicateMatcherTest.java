package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;

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
