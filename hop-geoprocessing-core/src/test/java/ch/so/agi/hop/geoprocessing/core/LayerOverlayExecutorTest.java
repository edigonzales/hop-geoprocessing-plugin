package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;

class LayerOverlayExecutorTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final LayerOverlayExecutor executor = new LayerOverlayExecutor();
  private final RowMeta rowMeta = new RowMeta();

  LayerOverlayExecutorTest() {
    rowMeta.addValueMeta(new ValueMetaInteger("id"));
  }

  @Test
  void intersectionReturnsMatchedSecondaryFeature() throws Exception {
    Geometry primaryGeometry = polygon(0, 0, 10, 10);
    Geometry secondaryGeometry = polygon(5, 5, 15, 15);
    FeatureRow primaryFeature = feature(100, primaryGeometry);
    LayerCache cache =
        new SpatialIndexBuilder().build(List.of(feature(1, secondaryGeometry)), rowMeta, true);

    List<OverlayFragment> result = executor.execute("intersection", primaryFeature, cache);
    Geometry expected = OverlayNG.overlay(primaryGeometry, secondaryGeometry, OverlayNG.INTERSECTION);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).secondaryFeature().rowData()[0]).isEqualTo(1);
    assertThat(result.get(0).geometry().equalsTopo(expected)).isTrue();
  }

  @Test
  void clipUsesUnionGeometryAndReturnsSingleFragment() throws Exception {
    Geometry primaryGeometry = polygon(0, 0, 10, 10);
    Geometry leftHalf = polygon(0, 0, 5, 10);
    Geometry rightHalf = polygon(5, 0, 10, 10);
    FeatureRow primaryFeature = feature(100, primaryGeometry);
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(List.of(feature(1, leftHalf), feature(2, rightHalf)), rowMeta, true);

    List<OverlayFragment> result = executor.execute("clip", primaryFeature, cache);
    Geometry unionGeometry = UnaryUnionNG.union(List.of(leftHalf, rightHalf), new PrecisionModel());
    Geometry expected = OverlayNG.overlay(primaryGeometry, unionGeometry, OverlayNG.INTERSECTION);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).secondaryFeature()).isNull();
    assertThat(result.get(0).geometry().equalsTopo(expected)).isTrue();
  }

  @Test
  void eraseReturnsRemainingGeometry() throws Exception {
    Geometry primaryGeometry = polygon(0, 0, 10, 10);
    Geometry secondaryGeometry = polygon(0, 0, 5, 10);
    FeatureRow primaryFeature = feature(100, primaryGeometry);
    LayerCache cache = new SpatialIndexBuilder().build(List.of(feature(1, secondaryGeometry)), rowMeta, true);

    List<OverlayFragment> result = executor.execute("erase", primaryFeature, cache);
    Geometry expected = OverlayNG.overlay(primaryGeometry, secondaryGeometry, OverlayNG.DIFFERENCE);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).geometry().equalsTopo(expected)).isTrue();
  }

  @Test
  void identityReturnsMatchedAndUnmatchedFragments() throws Exception {
    Geometry primaryGeometry = polygon(0, 0, 10, 10);
    Geometry secondaryGeometry = polygon(0, 0, 5, 10);
    FeatureRow primaryFeature = feature(100, primaryGeometry);
    LayerCache cache = new SpatialIndexBuilder().build(List.of(feature(1, secondaryGeometry)), rowMeta, true);

    List<OverlayFragment> result = executor.execute("identity", primaryFeature, cache);
    Geometry expectedIntersection =
        OverlayNG.overlay(primaryGeometry, secondaryGeometry, OverlayNG.INTERSECTION);
    Geometry expectedRemainder =
        OverlayNG.overlay(
            primaryGeometry,
            UnaryUnionNG.union(List.of(secondaryGeometry), new PrecisionModel()),
            OverlayNG.DIFFERENCE);

    assertThat(result).hasSize(2);
    assertThat(result)
        .filteredOn(fragment -> fragment.secondaryFeature() != null)
        .singleElement()
        .satisfies(fragment -> assertThat(fragment.geometry().equalsTopo(expectedIntersection)).isTrue());
    assertThat(result)
        .filteredOn(fragment -> fragment.secondaryFeature() == null)
        .singleElement()
        .satisfies(fragment -> assertThat(fragment.geometry().equalsTopo(expectedRemainder)).isTrue());
  }

  @Test
  void fixedPrecisionIntersectionUsesConfiguredOverlayMode() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(
                List.of(feature(1, polygon(5.0001, 0, 15.0001, 10))),
                rowMeta,
                true,
                OverlayMode.FIXED_PRECISION,
                1.0);

    List<OverlayFragment> result =
        executor.execute("intersection", primaryFeature, cache, OverlayMode.FIXED_PRECISION, 1.0);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).geometry().getArea()).isEqualTo(50.0);
  }

  @Test
  void fixedPrecisionClipUsesPrecisionAwareUnionGeometry() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(
                List.of(feature(1, polygon(5.0001, 0, 15.0001, 10))),
                rowMeta,
                true,
                OverlayMode.FIXED_PRECISION,
                1.0);

    List<OverlayFragment> result =
        executor.execute("clip", primaryFeature, cache, OverlayMode.FIXED_PRECISION, 1.0);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).geometry().getArea()).isEqualTo(50.0);
  }

  @Test
  void fixedPrecisionEraseUsesPrecisionAwareUnionGeometry() throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(
                List.of(feature(1, polygon(5.0001, 0, 15.0001, 10))),
                rowMeta,
                true,
                OverlayMode.FIXED_PRECISION,
                1.0);

    List<OverlayFragment> result =
        executor.execute("erase", primaryFeature, cache, OverlayMode.FIXED_PRECISION, 1.0);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).geometry().getArea()).isEqualTo(50.0);
  }

  @Test
  void fixedPrecisionIdentityUsesPrecisionAwareOverlayForMatchedAndRemainderFragments()
      throws Exception {
    FeatureRow primaryFeature = feature(100, polygon(0, 0, 10, 10));
    LayerCache cache =
        new SpatialIndexBuilder()
            .build(
                List.of(feature(1, polygon(5.0001, 0, 15.0001, 10))),
                rowMeta,
                true,
                OverlayMode.FIXED_PRECISION,
                1.0);

    List<OverlayFragment> result =
        executor.execute("identity", primaryFeature, cache, OverlayMode.FIXED_PRECISION, 1.0);

    assertThat(result).hasSize(2);
    assertThat(result)
        .filteredOn(fragment -> fragment.secondaryFeature() != null)
        .singleElement()
        .satisfies(fragment -> assertThat(fragment.geometry().getArea()).isEqualTo(50.0));
    assertThat(result)
        .filteredOn(fragment -> fragment.secondaryFeature() == null)
        .singleElement()
        .satisfies(fragment -> assertThat(fragment.geometry().getArea()).isEqualTo(50.0));
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
