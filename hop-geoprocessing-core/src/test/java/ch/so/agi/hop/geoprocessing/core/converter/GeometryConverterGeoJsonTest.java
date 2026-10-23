package ch.so.agi.hop.geoprocessing.core.converter;

import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.atolcd.hop.gis.geometry.codec.GeoJsonContext;
import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import org.apache.hop.core.row.RowMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/** Integration of the shared codec with converter schema, context and explicit loss options. */
class GeometryConverterGeoJsonTest {
  @Test
  void rfcGeometryCreatesNativeValueAndContextWithoutChangingInput() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    Object[] original = {"{\"coordinates\":[7,47],\"type\":\"Point\"}"};
    var plan =
        compile(
            input,
            json(
                ConverterInputFormat.GEOJSON,
                ConverterOutputFormat.GEOMETRY,
                GeoJsonProfile.AUTO,
                GeoJsonProfile.RFC7946,
                null,
                "context",
                false,
                false,
                false));
    Object[] output = runtime(plan).convert(original).row();
    Point point = (Point) output[1];
    assertThat(point.getX()).isEqualTo(7);
    assertThat(point.getY()).isEqualTo(47);
    assertThat(point.getSRID()).isEqualTo(4326);
    assertThat(GeoJsonContext.fromJson((String) output[2]).profile())
        .isEqualTo(GeoJsonProfile.RFC7946);
    assertThat(original).hasSize(1);
    assertThat(output[0]).isEqualTo(original[0]);
    assertThat(plan.outputRowMeta().getValueMeta(2).getType())
        .isEqualTo(org.apache.hop.core.row.IValueMeta.TYPE_STRING);
  }

  @Test
  void legacyAndJsonFgUsePublishedCodecCrsSemantics() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    var legacy =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.EWKT,
                    GeoJsonProfile.LEGACY2008,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    false,
                    false)));
    assertThat(
            legacy.convert(
                    new Object[] {
                      "{\"type\":\"Point\",\"coordinates\":[2600000,1200000],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:2056\"}}}"
                    })
                .row()[1])
        .isEqualTo("SRID=2056;POINT (2600000 1200000)");

    var fg =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.JSONFG10,
                    GeoJsonProfile.RFC7946,
                    null,
                    "context",
                    false,
                    false,
                    false)));
    Point nativePoint =
        (Point)
            fg.convert(
                    new Object[] {
                      "{\"type\":\"Point\",\"coordinates\":[47,7],\"coordRefSys\":\"http://www.opengis.net/def/crs/EPSG/0/4326\",\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/1.0/conf/core\"]}"
                    })
                .row()[1];
    assertThat(nativePoint.getX()).isEqualTo(7);
    assertThat(nativePoint.getY()).isEqualTo(47);
  }

  @Test
  void compoundOrDescribedMeasuresRequireAnOutputContextForNativeConversion() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    String measured =
        "{\"type\":\"Point\",\"coordinates\":[7,47,12],\"measures\":{\"enabled\":true,\"unit\":\"m\"},\"coordRefSys\":\"http://www.opengis.net/def/crs/OGC/0/CRS84\",\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/1.0/conf/core\",\"http://www.opengis.net/spec/json-fg-1/1.0/conf/measures\"]}";
    var strict =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.JSONFG10,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    false,
                    false)));
    assertThatThrownBy(() -> strict.convert(new Object[] {measured}))
        .isInstanceOf(GeometryCodecException.class)
        .hasMessageContaining("context field");
    var preserving =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.JSONFG10,
                    GeoJsonProfile.RFC7946,
                    null,
                    "context",
                    false,
                    false,
                    false)));
    Object[] decoded = preserving.convert(new Object[] {measured}).row();
    Point point = (Point) decoded[1];
    assertThat(point.getCoordinateSequence().getM(0)).isEqualTo(12);
    assertThat(GeoJsonContext.fromJson((String) decoded[2]).measures()).containsEntry("unit", "m");
  }

  @Test
  void strictRfcOutputRejectsMeasuresUnlessDropIsExplicitAndDoesNotMutateGeometry()
      throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(geometry("geom"));
    Point source = (Point) CODEC.readText("SRID=4326;POINT M (7 47 12)");
    var strict =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    false,
                    false)));
    assertThatThrownBy(() -> strict.convert(new Object[] {source}))
        .isInstanceOf(GeometryCodecException.class);
    var explicit =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    true,
                    false)));
    String result = (String) explicit.convert(new Object[] {source}).row()[1];
    assertThat(result).contains("Point");
    assertThat(
            CODEC
                .readGeoJson(
                    result, com.atolcd.hop.gis.geometry.codec.GeoJsonReadOptions.defaults())
                .geometry()
                .getCoordinate()
                .x)
        .isEqualTo(7);
    assertThat(source.getCoordinateSequence().getM(0)).isEqualTo(12);
  }

  @Test
  void emptyReplacementAndJsonFgCurvesUseOnlyExplicitCodecOptions() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(geometry("geom"));
    Geometry empty = CODEC.readText("SRID=4326;POINT EMPTY");
    var strict =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    false,
                    false)));
    assertThatThrownBy(() -> strict.convert(new Object[] {empty}))
        .isInstanceOf(GeometryCodecException.class);
    var explicit =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    null,
                    null,
                    false,
                    false,
                    true)));
    assertThat(explicit.convert(new Object[] {empty}).row()[1]).isEqualTo("null");
    assertThat(empty.isEmpty()).isTrue();
    Geometry curve = CODEC.readText("SRID=2056;CIRCULARSTRING (0 0, 1 1, 2 0)");
    var fg =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.JSONFG10,
                    null,
                    null,
                    false,
                    false,
                    false)));
    String encoded = (String) fg.convert(new Object[] {curve}).row()[1];
    Geometry roundtrip =
        CODEC
            .readGeoJson(encoded, com.atolcd.hop.gis.geometry.codec.GeoJsonReadOptions.defaults())
            .geometry();
    assertThat(roundtrip).isInstanceOf(com.atolcd.hop.gis.geometry.curve.CircularString.class);
    assertThat(CODEC.writeText(roundtrip, com.atolcd.hop.gis.geometry.codec.WktWriteOptions.ewkt()))
        .isEqualTo("SRID=2056;CIRCULARSTRING (0 0, 1 1, 2 0)");
    assertThat(curve).isInstanceOf(com.atolcd.hop.gis.geometry.curve.CircularString.class);
  }

  @Test
  void nullStaysNullAndFeatureInputIsRejected() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    var converter =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    null,
                    "context",
                    false,
                    false,
                    false)));
    assertThat(converter.convert(new Object[] {null}).row()).containsExactly(null, null, null);
    assertThatThrownBy(
            () ->
                converter.convert(
                    new Object[] {"{\"type\":\"Feature\",\"geometry\":null,\"properties\":{}}"}))
        .isInstanceOf(GeometryCodecException.class);
    assertThatThrownBy(() -> converter.convert(new Object[] {"{broken"}))
        .isInstanceOf(GeometryCodecException.class);
  }

  @Test
  void outputContextParticipatesInCollisionsAndNullTargetHandling() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("context"));
    assertThatThrownBy(
            () ->
                compile(
                    input,
                    json(
                        ConverterInputFormat.GEOJSON,
                        ConverterOutputFormat.GEOMETRY,
                        GeoJsonProfile.AUTO,
                        GeoJsonProfile.RFC7946,
                        null,
                        "context",
                        false,
                        false,
                        false)))
        .isInstanceOf(ConverterConfigurationException.class);
    var converter =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    "context",
                    "out_context",
                    false,
                    false,
                    false)));
    assertThat(converter.nullOutputs(new Object[] {"broken", "keep"}))
        .containsExactly("broken", "keep", null, null);
  }

  @Test
  void autoIsNotAnOutputProfile() {
    RowMeta input = rowMeta();
    input.addValueMeta(geometry("geom"));
    assertThatThrownBy(
            () ->
                compile(
                    input,
                    json(
                        ConverterInputFormat.GEOMETRY,
                        ConverterOutputFormat.GEOJSON,
                        GeoJsonProfile.AUTO,
                        GeoJsonProfile.AUTO,
                        null,
                        null,
                        false,
                        false,
                        false)))
        .isInstanceOf(ConverterConfigurationException.class);
  }

  @Test
  void explicitContextSurvivesNullGeometryWithoutInventingContextForOtherNulls() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("context"));
    var metadata =
        new GeoJsonContext(
            GeoJsonProfile.JSONFG10,
            "EPSG:2056",
            java.util.Map.of("enabled", false, "description", "Unlocated survey"));
    var converter =
        runtime(
            compile(
                input,
                json(
                    ConverterInputFormat.GEOJSON,
                    ConverterOutputFormat.GEOMETRY,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    "context",
                    "out_context",
                    false,
                    false,
                    false)));
    for (String value : new String[] {null, "null"}) {
      Object[] row = converter.convert(new Object[] {value, metadata.toJson()}).row();
      assertThat(row[2]).isNull();
      assertThat(GeoJsonContext.fromJson((String) row[3])).isEqualTo(metadata);
    }
    assertThat(converter.convert(new Object[] {null, null}).row())
        .containsExactly(null, null, null, null);

    RowMeta nativeInput = rowMeta();
    nativeInput.addValueMeta(geometry("geom"));
    nativeInput.addValueMeta(string("context"));
    var encode =
        runtime(
            compile(
                nativeInput,
                json(
                    ConverterInputFormat.GEOMETRY,
                    ConverterOutputFormat.GEOJSON,
                    GeoJsonProfile.AUTO,
                    GeoJsonProfile.RFC7946,
                    "context",
                    "out_context",
                    false,
                    false,
                    false)));
    Object[] encoded = encode.convert(new Object[] {null, metadata.toJson()}).row();
    assertThat(encoded[2]).isNull();
    assertThat(GeoJsonContext.fromJson((String) encoded[3])).isEqualTo(metadata);
  }

  @Test
  void explicitSridReplacementRefreshesContextAndKeepsCoordinates() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    var settings =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOMETRY,
            GeoJsonProfile.AUTO,
            GeoJsonProfile.LEGACY2008,
            null,
            "context",
            false,
            false,
            false);
    var converter = runtime(compile(input, policy(settings, ConverterSridMode.REPLACE, 2056)));
    Object[] row =
        converter.convert(new Object[] {"{\"type\":\"Point\",\"coordinates\":[7,47]}"}).row();
    Point point = (Point) row[1];
    assertThat(point.getX()).isEqualTo(7);
    assertThat(point.getY()).isEqualTo(47);
    assertThat(point.getSRID()).isEqualTo(2056);
    assertThat(GeoJsonContext.fromJson((String) row[2]).coordRefSys()).isEqualTo("EPSG:2056");

    var encodedSettings =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOJSON,
            GeoJsonProfile.AUTO,
            GeoJsonProfile.LEGACY2008,
            null,
            "context",
            false,
            false,
            false);
    var encode = runtime(compile(input, policy(encodedSettings, ConverterSridMode.REPLACE, 2056)));
    String result =
        (String)
            encode.convert(new Object[] {"{\"type\":\"Point\",\"coordinates\":[7,47]}"}).row()[1];
    assertThat(CODEC.readGeoJson(result, null).geometry().getSRID()).isEqualTo(2056);
  }

  @Test
  void clearingSridClearsContextAndCannotAccidentallyExportAsWgs84() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    var settings =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOMETRY,
            GeoJsonProfile.AUTO,
            GeoJsonProfile.RFC7946,
            null,
            "context",
            false,
            false,
            false);
    Object[] row =
        runtime(compile(input, policy(settings, ConverterSridMode.CLEAR, null)))
            .convert(new Object[] {"{\"type\":\"Point\",\"coordinates\":[7,47]}"})
            .row();
    assertThat(((Geometry) row[1]).getSRID()).isZero();
    assertThat(GeoJsonContext.fromJson((String) row[2]).coordRefSys()).isNull();
    var encoded =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOJSON,
            GeoJsonProfile.AUTO,
            GeoJsonProfile.RFC7946,
            null,
            "context",
            false,
            false,
            false);
    var encode = runtime(compile(input, policy(encoded, ConverterSridMode.CLEAR, null)));
    assertThatThrownBy(
            () -> encode.convert(new Object[] {"{\"type\":\"Point\",\"coordinates\":[7,47]}"}))
        .isInstanceOf(GeometryCodecException.class);
    var noContext =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOMETRY,
            GeoJsonProfile.JSONFG10,
            GeoJsonProfile.RFC7946,
            null,
            null,
            false,
            false,
            false);
    Geometry clearedCompound =
        (Geometry)
            runtime(compile(input, policy(noContext, ConverterSridMode.CLEAR, null)))
                .convert(
                    new Object[] {
                      "{\"type\":\"Point\",\"coordinates\":[2600000,1200000,500],"
                          + "\"coordRefSys\":[\"EPSG:2056\",\"EPSG:5728\"],\"conformsTo\":[\""
                          + GeoJsonContext.JSONFG_CORE
                          + "\"]}"
                    })
                .row()[1];
    assertThat(clearedCompound.getSRID()).isZero();
    assertThat(clearedCompound.getCoordinate().getZ()).isEqualTo(500);
  }

  @Test
  void assignmentRetainsCompoundVerticalAndMeasureMetadata() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    String source =
        "{\"type\":\"Point\",\"coordinates\":[2600000,1200000,500,12],"
            + "\"coordRefSys\":[\"EPSG:2056\",\"EPSG:5728\"],"
            + "\"measures\":{\"enabled\":true,\"unit\":\"m\"},\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\",\""
            + GeoJsonContext.JSONFG_MEASURES
            + "\"]}";
    var settings =
        json(
            ConverterInputFormat.GEOJSON,
            ConverterOutputFormat.GEOMETRY,
            GeoJsonProfile.JSONFG10,
            GeoJsonProfile.JSONFG10,
            null,
            "context",
            false,
            false,
            false);
    Object[] row =
        runtime(compile(input, policy(settings, ConverterSridMode.REPLACE, 4326)))
            .convert(new Object[] {source})
            .row();
    var context = GeoJsonContext.fromJson((String) row[2]);
    assertThat(context.coordRefSys()).isEqualTo(java.util.List.of("EPSG:4326", "EPSG:5728"));
    assertThat(context.measures()).containsEntry("unit", "m");
    Point point = (Point) row[1];
    assertThat(point.getCoordinateSequence().getZ(0)).isEqualTo(500);
    assertThat(point.getCoordinateSequence().getM(0)).isEqualTo(12);
  }

  private static GeometryConverterSettings policy(
      GeometryConverterSettings s, ConverterSridMode mode, Integer srid) {
    return new GeometryConverterSettings(
        s.configurationVersion(),
        s.inputFormat(),
        s.inputFieldName(),
        s.inputXFieldName(),
        s.inputYFieldName(),
        s.inputZFieldName(),
        s.inputMFieldName(),
        s.outputFormat(),
        s.outputFieldName(),
        s.outputXFieldName(),
        s.outputYFieldName(),
        s.outputZFieldName(),
        s.outputMFieldName(),
        s.byteOrder(),
        mode,
        srid,
        s.overwriteExistingFields(),
        s.errorMode(),
        s.geoJsonInputProfile(),
        s.geoJsonOutputProfile(),
        s.geoJsonInputContextField(),
        s.geoJsonOutputContextField(),
        s.geoJsonDropZ(),
        s.geoJsonDropM(),
        s.geoJsonEmptyToNull());
  }

  private static GeometryConverterSettings json(
      ConverterInputFormat input,
      ConverterOutputFormat output,
      GeoJsonProfile readProfile,
      GeoJsonProfile writeProfile,
      String inputContext,
      String outputContext,
      boolean dropZ,
      boolean dropM,
      boolean emptyToNull) {
    return new GeometryConverterSettings(
        1,
        input,
        "geom",
        null,
        null,
        null,
        null,
        output,
        "result",
        "x",
        "y",
        null,
        null,
        com.atolcd.hop.gis.geometry.codec.GeometryByteOrder.BIG_ENDIAN,
        ConverterSridMode.KEEP,
        null,
        false,
        ConverterErrorMode.FAIL,
        readProfile,
        writeProfile,
        inputContext,
        outputContext,
        dropZ,
        dropM,
        emptyToNull);
  }
}
