package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class LayerOverlayExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final LayerOverlayExecutor executor = new LayerOverlayExecutor();
  private final RowMeta rowMeta = new RowMeta();

  LayerOverlayExecutorTest() {
    rowMeta.addValueMeta(new ValueMetaInteger("id"));
  }

  @Test
  void intersectionReturnsMatchedSecondaryFeature() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(List.of(feature(1, polygon(5, 5, 15, 15))), rowMeta, true);

    List<OverlayFragment> result = executor.execute("intersection", primaryFeature, cache);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).secondaryFeature().rowData()[0]).isEqualTo(1);
    assertThat(result.get(0).geometry().getArea()).isEqualTo(25.0);
  }

  @Test
  void clipUsesUnionGeometryAndReturnsSingleFragment() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(
                List.of(feature(1, polygon(0, 0, 5, 10)), feature(2, polygon(5, 0, 10, 10))),
                rowMeta,
                true);

    List<OverlayFragment> result = executor.execute("clip", primaryFeature, cache);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).secondaryFeature()).isNull();
    assertThat(result.get(0).geometry().getArea()).isEqualTo(100.0);
  }

  @Test
  void eraseReturnsRemainingGeometry() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder().build(List.of(feature(1, polygon(0, 0, 5, 10))), rowMeta, true);

    List<OverlayFragment> result = executor.execute("erase", primaryFeature, cache);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).geometry().getArea()).isEqualTo(50.0);
  }

  @Test
  void identityReturnsMatchedAndUnmatchedFragments() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder().build(List.of(feature(1, polygon(0, 0, 5, 10))), rowMeta, true);

    List<OverlayFragment> result = executor.execute("identity", primaryFeature, cache);

    assertThat(result).hasSize(2);
    assertThat(result).anyMatch(fragment -> fragment.secondaryFeature() != null);
    assertThat(result).anyMatch(fragment -> fragment.secondaryFeature() == null);
  }

  private FeatureRow feature(int id, Geometry geometry) {
    return new FeatureRow(new Object[] {id}, geometry, geometry.getEnvelopeInternal(), geometry.getSRID(), id);
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
