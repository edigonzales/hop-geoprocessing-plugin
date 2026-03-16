package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;

class OverlayExecutionTest {

  private final WKTReader wktReader = new WKTReader();

  @Test
  void standardOverlayUsesOverlayNgFloatingPrecision() throws Exception {
    Geometry left = wktReader.read("POLYGON ((0 0, 6 0, 6 6, 0 6, 0 0))");
    Geometry right = wktReader.read("POLYGON ((3 0, 9 0, 9 6, 3 6, 3 0))");

    Geometry result = OverlayExecution.overlay(left, right, OverlayMode.STANDARD, null, OverlayNG.INTERSECTION);
    Geometry expected = OverlayNG.overlay(left, right, OverlayNG.INTERSECTION);

    assertThat(result.equalsTopo(expected)).isTrue();
  }

  @Test
  void standardUnionUsesUnaryUnionNgFloatingPrecision() throws Exception {
    Geometry first = wktReader.read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
    Geometry second = wktReader.read("POLYGON ((2 0, 6 0, 6 4, 2 4, 2 0))");

    Geometry result = OverlayExecution.union(List.of(first, second), OverlayMode.STANDARD, null);
    Geometry expected = UnaryUnionNG.union(List.of(first, second), new PrecisionModel());

    assertThat(result.equalsTopo(expected)).isTrue();
  }

  @Test
  void robustOverlayStillDelegatesToOverlayNgRobust() throws Exception {
    Geometry left = wktReader.read("POLYGON ((0 0, 6 0, 6 6, 0 6, 0 0))");
    Geometry right = wktReader.read("POLYGON ((3 0, 9 0, 9 6, 3 6, 3 0))");

    Geometry result = OverlayExecution.overlay(left, right, OverlayMode.ROBUST, null, OverlayNG.INTERSECTION);
    Geometry expected = OverlayNGRobust.overlay(left, right, OverlayNG.INTERSECTION);

    assertThat(result.equalsTopo(expected)).isTrue();
  }

  @Test
  void fixedPrecisionUnionStillUsesExplicitPrecisionModel() throws Exception {
    Geometry first = wktReader.read("POLYGON ((0 0, 4.0001 0, 4.0001 4, 0 4, 0 0))");
    Geometry second = wktReader.read("POLYGON ((4 0, 8 0, 8 4, 4 4, 4 0))");

    Geometry result = OverlayExecution.union(List.of(first, second), OverlayMode.FIXED_PRECISION, 1.0);
    Geometry expected = UnaryUnionNG.union(List.of(first, second), new PrecisionModel(1.0));

    assertThat(result.equalsTopo(expected)).isTrue();
  }
}
