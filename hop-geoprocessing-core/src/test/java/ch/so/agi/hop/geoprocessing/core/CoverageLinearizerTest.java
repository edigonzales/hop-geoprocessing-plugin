package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.*;

import com.atolcd.hop.gis.geometry.curve.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.geom.*;

class CoverageLinearizerTest {
  GeometryFactory f = new GeometryFactory(new PrecisionModel(), 2056);

  Coordinate p(double x, double y) {
    return new Coordinate(x, y);
  }

  LineString line(Coordinate... p) {
    return f.createLineString(p);
  }

  CircularString arc(Coordinate... p) {
    return new CircularString(p, f);
  }

  @Test
  void sharesDifferentlyPartitionedArcsAndQuantizedEdges() {
    Coordinate a = p(5, 0), b = p(-5, 0), top = p(0, 5);
    Polygon lower =
        new CurvePolygon(
            List.of(
                new CompoundCurve(List.of(arc(a, top, b), line(b, p(-5, -10), p(5, -10), a)), f)),
            f);
    Polygon upper =
        new CurvePolygon(
            List.of(
                new CompoundCurve(
                    List.of(
                        arc(b, p(-3, 4), top),
                        arc(top, p(3, 4), a),
                        line(a, p(5, 10), p(-5, 10), b)),
                    f)),
            f);
    Geometry[] result =
        CoverageLinearizer.linearize(
            List.of(lower, upper), 0.01, new CoverageLinearizer.Grid(0.001, -100, -100));
    assertThat(CoverageValidator.hasInvalidResult(CoverageValidator.validate(result))).isFalse();
    assertThat(result[0].intersection(result[1]).getArea()).isZero();
    assertThat(result[0].union(result[1]).getArea()).isCloseTo(200, within(1e-9));
    assertThat(result[0].getBoundary().intersection(result[1].getBoundary()).getLength())
        .isGreaterThan(15);
    assertThat(result[0].getSRID()).isEqualTo(2056);
  }

  @Test
  void rejectsCoarseGridAndInvalidCoverage() {
    Polygon a = f.createPolygon(new Coordinate[] {p(0, 0), p(2, 0), p(2, 2), p(0, 2), p(0, 0)});
    assertThatThrownBy(
            () ->
                CoverageLinearizer.linearize(
                    List.of(a), 0.01, new CoverageLinearizer.Grid(1, 0, 0)))
        .hasMessageContaining("too coarse");
    assertThatThrownBy(() -> CoverageLinearizer.linearize(List.of(a, a), 0.01, null))
        .hasMessageContaining("Invalid linearized coverage");
  }

  @Test
  void oppositeSemicirclesRemainDistinct() {
    Polygon disk = new CurvePolygon(List.of(arc(p(5, 0), p(-5, 0), p(5, 0))), f);
    Geometry result = CoverageLinearizer.linearize(List.of(disk), 0.001, null)[0];
    assertThat(result.getArea()).isCloseTo(Math.PI * 25, within(0.03));
    assertThat(result.isValid()).isTrue();
  }
}
