package ch.so.agi.hop.geoprocessing.core.reproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.atolcd.hop.gis.geometry.codec.GeometrySridSupport;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.WKTReader;

class GeoToolsReprojectionServiceTest {
  private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 0, PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
  private final GeoToolsReprojectionService service = new GeoToolsReprojectionService(null);

  @Test
  void transformsLongitudeLatitudeUsingIndependentSphericalMercatorFormula() throws Exception {
    Point input = point(8.5, 47.5, 4326);
    Geometry result = service.reproject(input, null, 3857);
    double expectedX = 6378137 * Math.toRadians(8.5);
    double expectedY = 6378137 * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(47.5) / 2));
    assertThat(result.getCoordinate().x).isCloseTo(expectedX, within(1e-7));
    assertThat(result.getCoordinate().y).isCloseTo(expectedY, within(1e-7));
    Geometry reverse = service.reproject(result, null, 4326);
    assertThat(reverse.getCoordinate().x).isCloseTo(8.5, within(1e-9));
    assertThat(reverse.getCoordinate().y).isCloseTo(47.5, within(1e-9));
    assertThat(input.getCoordinate().x).isEqualTo(8.5);
    assertThat(input.getSRID()).isEqualTo(4326);
    assertThat(result.getSRID()).isEqualTo(3857);
  }

  @Test
  void transformsSwissOriginAgainstPublishedBernReference() throws Exception {
    // Swiss projection origin (Bern); reference WGS84 position in longitude/latitude.
    Geometry result = service.reproject(point(2600000, 1200000, 2056), null, 4326);
    assertThat(result.getCoordinate().x).isCloseTo(7.438632495, within(0.00003));
    assertThat(result.getCoordinate().y).isCloseTo(46.951082877, within(0.00003));
    Geometry reverse = service.reproject(point(7.438632495, 46.951082877, 4326), null, 2056);
    assertThat(reverse.getCoordinate().x).isCloseTo(2600000, within(3.0));
    assertThat(reverse.getCoordinate().y).isCloseTo(1200000, within(3.0));
  }

  @Test
  void retainsAllCoordinateLayoutsAndNonFiniteZMWithoutChangingInput() throws Exception {
    for (int[] layout : new int[][] {{2, 0}, {3, 0}, {3, 1}, {4, 1}}) {
      CoordinateSequence coordinates = factory.getCoordinateSequenceFactory().create(1, layout[0], layout[1]);
      coordinates.setOrdinate(0, 0, 8.5);
      coordinates.setOrdinate(0, 1, 47.5);
      for (int ordinate = 2; ordinate < layout[0]; ordinate++) {
        coordinates.setOrdinate(0, ordinate, ordinate == 2 ? Double.NaN : 42.125);
      }
      Point input = factory.createPoint(coordinates);
      input.setSRID(4326);
      Point output = (Point) service.reproject(input, null, 3857);
      assertThat(output.getCoordinateSequence().getDimension()).isEqualTo(layout[0]);
      assertThat(output.getCoordinateSequence().getMeasures()).isEqualTo(layout[1]);
      for (int ordinate = 2; ordinate < layout[0]; ordinate++) {
        assertThat(Double.doubleToLongBits(output.getCoordinateSequence().getOrdinate(0, ordinate)))
            .isEqualTo(Double.doubleToLongBits(coordinates.getOrdinate(0, ordinate)));
      }
      assertThat(input.getX()).isEqualTo(8.5);
    }
  }

  @Test
  void preservesStructureHolesAndNestedCollectionsAndUpdatesEverySrid() throws Exception {
    String[] wkts = {
      "LINESTRING (8 47, 9 48)",
      "POLYGON ((8 47,9 47,9 48,8 48,8 47),(8.2 47.2,8.2 47.4,8.4 47.4,8.4 47.2,8.2 47.2))",
      "MULTIPOINT ((8 47),(9 48))",
      "MULTILINESTRING ((8 47,9 48),(8 48,9 47))",
      "MULTIPOLYGON (((8 47,9 47,9 48,8 47)))",
      "GEOMETRYCOLLECTION (POINT (8 47),GEOMETRYCOLLECTION (LINESTRING (8 47,9 48)))"
    };
    for (String wkt : wkts) {
      Geometry input = GeometrySridSupport.copyWithSrid(new WKTReader(factory).read(wkt), 4326);
      input.getEnvelopeInternal();
      Geometry snapshot = input.copy();
      Geometry output = service.reproject(input, null, 3857);
      assertThat(output.getGeometryType()).isEqualTo(input.getGeometryType());
      assertThat(output.getNumPoints()).isEqualTo(input.getNumPoints());
      output.apply((org.locationtech.jts.geom.GeometryComponentFilter) component -> assertThat(component.getSRID()).isEqualTo(3857));
      assertThat(output.getEnvelopeInternal().getMinX()).isGreaterThan(800000);
      assertThat(input.equalsExact(snapshot)).isTrue();
      assertThat(input.getEnvelopeInternal().getMinX()).isEqualTo(8.0);
    }
  }

  @Test
  void identityStillCopiesAndValidatesCoordinates() throws Exception {
    Point input = point(8, 47, 4326);
    Geometry output = service.reproject(input, null, 4326);
    assertThat(output).isNotSameAs(input);
    assertThat(output.equalsExact(input)).isTrue();
    assertDataError(point(Double.POSITIVE_INFINITY, 47, 4326), null, 4326, "GR_NON_FINITE_COORDINATE");
  }

  @Test
  void retainsNullAndEmptyButValidatesEmptySourceSrid() throws Exception {
    assertThat(service.reproject(null, null, 3857)).isNull();
    for (String wkt : List.of("POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY")) {
      Geometry input = new WKTReader(factory).read(wkt);
      input.setSRID(4326);
      Geometry output = service.reproject(input, null, 3857);
      assertThat(output.isEmpty()).isTrue();
      assertThat(output.getGeometryType()).isEqualTo(input.getGeometryType());
      assertThat(output.getSRID()).isEqualTo(3857);
      input.setSRID(0);
      assertDataError(input, null, 3857, "GR_MISSING_SOURCE_SRID");
    }
  }

  @Test
  void explicitOverrideAllowsMissingOrDifferentPositiveSridButRejectsMixedAndNegative() throws Exception {
    Geometry output = service.reproject(point(8, 47, 2056), 4326, 3857);
    assertThat(output.getCoordinate().x).isCloseTo(890555.9263461885, within(1e-6));
    assertThat(service.reproject(point(8, 47, 0), 4326, 3857)).isNotNull();
    Geometry mixed = factory.createGeometryCollection(new Geometry[] {point(8, 47, 4326), point(9, 48, 2056)});
    assertDataError(mixed, 4326, 3857, "GR_INVALID_SRID");
    assertDataError(point(8, 47, -1), 4326, 3857, "GR_INVALID_SRID");
  }

  @Test
  void rejectsNestedCurvesEvenForIdentity() {
    CircularString curve = new CircularString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)}, factory);
    Geometry input = factory.createGeometryCollection(new Geometry[] {point(8, 47, 4326), curve});
    input.setSRID(4326);
    assertDataError(input, null, 4326, "GR_CURVE_REQUIRES_LINEARIZATION");
  }

  @Test
  void distinguishesUnknownRowSridFromInvalidFixedConfiguration() {
    assertDataError(point(8, 47, 99999999), null, 3857, "GR_UNKNOWN_EPSG");
    assertDataError(point(8, 47, 4979), null, 3857, "GR_UNSUPPORTED_CRS_DIMENSION");
    assertThatThrownBy(() -> service.validateConfiguration(settings("99999999"))).isInstanceOf(ReprojectConfigurationException.class);
    assertThatThrownBy(() -> service.validateConfiguration(settings("4979"))).isInstanceOf(ReprojectConfigurationException.class);
  }

  @Test
  void reportsOutOfDomainAndNonFiniteSourceCoordinatesAsDataErrors() {
    assertDataError(point(8, 100, 4326), null, 3857, "GR_TRANSFORM_FAILED");
    assertDataError(point(8, Double.NaN, 4326), null, 3857, "GR_NON_FINITE_COORDINATE");
  }

  @Test
  void emitsOperationMetadataOncePerCachedPair() throws Exception {
    List<String> log = new ArrayList<>();
    GeoToolsReprojectionService logged = new GeoToolsReprojectionService(log::add);
    logged.reproject(point(8, 47, 4326), null, 3857);
    logged.reproject(point(9, 48, 4326), null, 3857);
    assertThat(log).hasSize(1);
    assertThat(log.getFirst()).contains("EPSG:4326 -> EPSG:3857", "operation=", "identifiers=", "accuracy=");
  }

  @Test
  void boundsThePerInstanceOperationCache() throws Exception {
    // Adjacent WGS84 UTM zones are independent EPSG pairs and do not need datum grids.
    for (int zone = 1; zone <= 60; zone++) {
      service.reproject(point(zone * 6 - 183, 45, 4326), null, 32600 + zone);
    }
    for (int zone = 1; zone <= 8; zone++) {
      service.reproject(point(zone * 6 - 183, -45, 4326), null, 32700 + zone);
    }
    assertThat(service.cachedOperationCount()).isEqualTo(64);
  }

  private Point point(double x, double y, int srid) {
    Point result = factory.createPoint(new Coordinate(x, y));
    result.setSRID(srid);
    return result;
  }

  private void assertDataError(Geometry geometry, Integer override, int target, String code) {
    assertThatThrownBy(() -> service.reproject(geometry, override, target))
        .isInstanceOfSatisfying(ReprojectDataException.class, error -> assertThat(error.code()).isEqualTo(code));
  }

  private GeometryReprojectSettings settings(String target) {
    return new GeometryReprojectSettings("geometry", ReprojectSourceMode.GEOMETRY_SRID, null, target, "result", false, ReprojectErrorMode.FAIL);
  }
}
