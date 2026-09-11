package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import com.atolcd.hop.gis.geometry.curve.CurvePolygon;
import com.atolcd.hop.gis.geometry.curve.MultiCurve;
import com.atolcd.hop.gis.geometry.curve.MultiSurface;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

class TrueCurveSupportTest {

  private final GeometryValueParser parser = new GeometryValueParser();
  private final GeometryFactory factory = new GeometryFactory();

  @Test
  void parserPreservesAllSupportedTrueCurveTypes() throws Exception {
    for (Geometry source : curveGeometries()) {
      source.setSRID(2056);

      Geometry parsed =
          parser.parseGeometry(
              new ValueMetaString("geom"), CurveGeometrySupport.writeWkb(source));

      assertThat(parsed).isInstanceOf(source.getClass());
      assertThat(parsed.getSRID()).isEqualTo(2056);
    }
  }

  @Test
  void processingBoundaryLinearizesAllSupportedTrueCurveTypes() {
    List<Geometry> curves = curveGeometries();
    List<Class<? extends Geometry>> expectedTypes =
        List.of(LineString.class, LineString.class, Polygon.class, MultiLineString.class, MultiPolygon.class);

    for (int index = 0; index < curves.size(); index++) {
      Geometry source = curves.get(index);
      source.setSRID(2056);

      Geometry linearized = GeometryFieldValueHelper.linearizeForProcessing(source);

      assertThat(linearized).isExactlyInstanceOf(expectedTypes.get(index));
      assertThat(linearized.getSRID()).isEqualTo(2056);
      assertThat(CurveGeometrySupport.isCurveGeometry(linearized)).isFalse();
      assertThat(linearized.getCoordinates()).isNotEmpty();
    }
  }

  @Test
  void rowValueBoundaryParsesCurveWkbAndReturnsOrdinaryJts() throws Exception {
    CurvePolygon source = curvePolygon();
    source.setSRID(2056);

    Geometry parsed =
        GeometryFieldValueHelper.readGeometry(
            new ValueMetaString("geom"), CurveGeometrySupport.writeWkb(source));

    assertThat(parsed).isExactlyInstanceOf(Polygon.class);
    assertThat(parsed.getSRID()).isEqualTo(2056);
    assertThat(parsed.getNumPoints()).isGreaterThan(5);
    assertThat(CurveGeometrySupport.isCurveGeometry(parsed)).isFalse();
  }

  @Test
  void directGeometryOperationLinearizesCurveBeforeExecuting() throws Exception {
    CircularString source = circularString();
    source.setSRID(2056);

    Geometry result =
        new GeometryOperationExecutor()
            .execute(
                "to_multi",
                source,
                null,
                null,
                null,
                null,
                null,
                false)
            .get(0);

    assertThat(result).isExactlyInstanceOf(MultiLineString.class);
    assertThat(result.getSRID()).isEqualTo(2056);
    assertThat(CurveGeometrySupport.isCurveGeometry(result)).isFalse();
  }

  @Test
  void foreignCurveUsesBinaryBridgeBeforeAnyStringFallback() throws Exception {
    CurvePolygon source = curvePolygon();
    source.setSRID(2056);
    byte[] wkb = CurveGeometrySupport.writeWkb(source);

    try (ForeignCurveGeometryHandle foreign = ForeignCurveGeometryHandle.create(wkb, 2056)) {
      Geometry parsed = parser.parseGeometry(null, foreign.geometry());

      assertThat(parsed).isInstanceOf(CurvePolygon.class);
      assertThat(parsed.getSRID()).isEqualTo(2056);
      CurvePolygon parsedPolygon = (CurvePolygon) parsed;
      assertThat(parsedPolygon.getCurveRings().get(0)).isInstanceOf(CircularString.class);
      CircularString ring = (CircularString) parsedPolygon.getCurveRings().get(0);
      assertThat(ring.getControlPoints()).hasSize(5);
    }
  }

  private List<Geometry> curveGeometries() {
    CircularString circularString = circularString();
    CompoundCurve compoundCurve =
        new CompoundCurve(
            List.of(
                circularString(),
                factory.createLineString(
                    new Coordinate[] {new Coordinate(2, 0), new Coordinate(3, 0)})),
            factory);
    CurvePolygon curvePolygon = curvePolygon();
    MultiCurve multiCurve =
        new MultiCurve(
            List.of(
                factory.createLineString(
                    new Coordinate[] {new Coordinate(-2, 0), new Coordinate(-1, 0)}),
                circularString()),
            factory);
    MultiSurface multiSurface =
        new MultiSurface(List.of(square(-3, -3, -2, -2), curvePolygon()), factory);
    return List.of(circularString, compoundCurve, curvePolygon, multiCurve, multiSurface);
  }

  private CircularString circularString() {
    return new CircularString(
        new Coordinate[] {
          new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)
        },
        factory);
  }

  private CurvePolygon curvePolygon() {
    CircularString ring =
        new CircularString(
            new Coordinate[] {
              new Coordinate(0, 0),
              new Coordinate(4, 0),
              new Coordinate(4, 4),
              new Coordinate(0, 4),
              new Coordinate(0, 0)
            },
            factory);
    return new CurvePolygon(List.of(ring), factory);
  }

  private Polygon square(double minX, double minY, double maxX, double maxY) {
    return factory.createPolygon(
        new Coordinate[] {
          new Coordinate(minX, minY),
          new Coordinate(maxX, minY),
          new Coordinate(maxX, maxY),
          new Coordinate(minX, maxY),
          new Coordinate(minX, minY)
        });
  }

  private record ForeignCurveGeometryHandle(URLClassLoader classLoader, Object geometry)
      implements AutoCloseable {
    private static ForeignCurveGeometryHandle create(byte[] wkb, int srid) throws Exception {
      URL geometryTypeLocation =
          CurveGeometrySupport.class.getProtectionDomain().getCodeSource().getLocation();
      URL jtsLocation = Geometry.class.getProtectionDomain().getCodeSource().getLocation();
      URLClassLoader classLoader =
          new URLClassLoader(new URL[] {geometryTypeLocation, jtsLocation}, null);
      Class<?> supportClass =
          Class.forName(
              "com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport", true, classLoader);
      Object geometry =
          supportClass.getMethod("readWkb", byte[].class).invoke(null, (Object) wkb);
      geometry.getClass().getMethod("setSRID", int.class).invoke(geometry, srid);
      return new ForeignCurveGeometryHandle(classLoader, geometry);
    }

    @Override
    public void close() throws Exception {
      classLoader.close();
    }
  }
}
