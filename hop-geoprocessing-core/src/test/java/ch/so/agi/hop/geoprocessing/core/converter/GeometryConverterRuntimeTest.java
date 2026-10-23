package ch.so.agi.hop.geoprocessing.core.converter;

import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.CODEC;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.binary;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.compile;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.geometry;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.integer;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.number;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.rowMeta;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.runtime;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.settings;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.CoordinateLayout;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecErrorCode;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import com.atolcd.hop.gis.geometry.codec.WkbWriteOptions;
import java.math.BigDecimal;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBigNumber;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

class GeometryConverterRuntimeTest {
  @Test
  void wktInputToNativeGeometryReturnsIndependentCopyAndKeepsSource() throws Exception {
    Geometry source = CODEC.readText("POINT ZM (1 2 3 4)");
    source.setSRID(2056);
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build();
    CompiledConversionPlan plan = compile(input, settings);
    Object[] row = {"POINT ZM (1 2 3 4)"};

    ConversionResult result = runtime(plan).convert(row);

    Point output = (Point) result.row()[1];
    assertThat(output).isNotSameAs(source);
    assertThat(output.getSRID()).isZero();
    assertThat(output.getCoordinateSequence().getDimension()).isEqualTo(4);
    assertThat(result.row()).isNotSameAs(row);
    assertThat(row[0]).isEqualTo("POINT ZM (1 2 3 4)");
    assertThat(result.allTargetValuesNull()).isFalse();
    assertThat(result.sridOmitted()).isFalse();
  }

  @Test
  void emptyTextAndNullInputArePreservedOnTheNativePath() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    CompiledConversionPlan plan =
        compile(
            input, settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build());
    GeometryConverterRuntime runtime = runtime(plan);

    Geometry empty = (Geometry) runtime.convert(new Object[] {"POINT EMPTY"}).row()[1];
    assertThat(empty.isEmpty()).isTrue();
    assertThat(runtime.convert(new Object[] {null}).row()[1]).isNull();
  }

  @Test
  void binaryAndHexInputsUseTheSharedCodec() throws Exception {
    byte[] wkb =
        CODEC.writeBinary(CODEC.readText("POINT (1 2)"), WkbWriteOptions.standardFieldBinary());
    for (ConverterInputFormat format :
        new ConverterInputFormat[] {ConverterInputFormat.WKB_BYTES, ConverterInputFormat.WKB_HEX}) {
      RowMeta input = rowMeta();
      input.addValueMeta(
          format == ConverterInputFormat.WKB_BYTES ? binary("geom") : string("geom"));
      CompiledConversionPlan plan =
          compile(
              input, settings(format, ConverterOutputFormat.GEOMETRY).inputField("geom").build());
      Object value =
          format == ConverterInputFormat.WKB_BYTES ? wkb : java.util.HexFormat.of().formatHex(wkb);
      Point point = (Point) runtime(plan).convert(new Object[] {value}).row()[1];
      assertThat(point.getX()).isEqualTo(1);
      assertThat(point.getY()).isEqualTo(2);
    }
  }

  @Test
  void serializedOutputOptionsFollowTheExplicitFormatContract() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    String source = "SRID=2056;POINT Z (1 2 3)";

    ConversionResult wkt =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.WKT)
                        .inputField("geom")
                        .build()))
            .convert(new Object[] {source});
    assertThat(wkt.row()[1]).isEqualTo("POINT Z (1 2 3)");

    ConversionResult ewkt =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.EWKT)
                        .inputField("geom")
                        .build()))
            .convert(new Object[] {source});
    assertThat(ewkt.row()[1]).isEqualTo("SRID=2056;POINT Z (1 2 3)");

    ConversionResult isoBe =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.WKB_BYTES)
                        .inputField("geom")
                        .byteOrder(GeometryByteOrder.BIG_ENDIAN)
                        .build()))
            .convert(new Object[] {source});
    String isoBeHex = java.util.HexFormat.of().formatHex((byte[]) isoBe.row()[1]).toUpperCase();
    assertThat(isoBeHex).startsWith("00000003E9");
    assertThat(isoBeHex).doesNotContain("00000808");

    ConversionResult isoLe =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.WKB_BYTES)
                        .inputField("geom")
                        .byteOrder(GeometryByteOrder.LITTLE_ENDIAN)
                        .build()))
            .convert(new Object[] {source});
    String isoLeHex = java.util.HexFormat.of().formatHex((byte[]) isoLe.row()[1]).toUpperCase();
    assertThat(isoLeHex).startsWith("01E9030000");

    ConversionResult ewkbBe =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.EWKB_BYTES)
                        .inputField("geom")
                        .byteOrder(GeometryByteOrder.BIG_ENDIAN)
                        .build()))
            .convert(new Object[] {source});
    String ewkbBeHex = java.util.HexFormat.of().formatHex((byte[]) ewkbBe.row()[1]).toUpperCase();
    assertThat(ewkbBeHex).startsWith("00A000000100000808");

    ConversionResult ewkbLe =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.EWKB_BYTES)
                        .inputField("geom")
                        .byteOrder(GeometryByteOrder.LITTLE_ENDIAN)
                        .build()))
            .convert(new Object[] {source});
    String ewkbLeHex = java.util.HexFormat.of().formatHex((byte[]) ewkbLe.row()[1]).toUpperCase();
    assertThat(ewkbLeHex).startsWith("01010000A008080000");

    ConversionResult hex =
        runtime(
                compile(
                    input,
                    settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.EWKB_HEX)
                        .inputField("geom")
                        .byteOrder(GeometryByteOrder.BIG_ENDIAN)
                        .build()))
            .convert(new Object[] {source});
    assertThat(hex.row()[1]).isEqualTo(ewkbBeHex);
  }

  @Test
  void sridPoliciesAlsoWorkForUnknownSourceAndClear() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(geometry("geom"));
    Geometry unknown = CODEC.readText("POINT (1 2)");
    Geometry known = CODEC.readText("POINT (1 2)");
    known.setSRID(2056);

    GeometryConverterSettings replace =
        settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .sridMode(ConverterSridMode.REPLACE)
            .srid(4326)
            .build();
    assertThat(
            ((Geometry) runtime(compile(input, replace)).convert(new Object[] {unknown}).row()[1])
                .getSRID())
        .isEqualTo(4326);

    GeometryConverterSettings clear =
        settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .sridMode(ConverterSridMode.CLEAR)
            .build();
    assertThat(
            ((Geometry) runtime(compile(input, clear)).convert(new Object[] {known}).row()[1])
                .getSRID())
        .isZero();
    assertThat(known.getSRID()).isEqualTo(2056);
  }

  @Test
  void coordinateInputSupportsAllLayoutsAndNumericTypes() throws Exception {
    for (CoordinateLayout layout :
        new CoordinateLayout[] {
          CoordinateLayout.XY, CoordinateLayout.XYZ, CoordinateLayout.XYM, CoordinateLayout.XYZM
        }) {
      RowMeta input = rowMeta();
      input.addValueMeta(number("x"));
      input.addValueMeta(integer("y"));
      input.addValueMeta(new ValueMetaBigNumber("z"));
      input.addValueMeta(number("m"));
      ConverterTestSupport.SettingsBuilder builder =
          settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.GEOMETRY)
              .inputX("x")
              .inputY("y")
              .sridMode(ConverterSridMode.REPLACE)
              .srid(2056);
      if (layout.hasZ()) {
        builder.inputZ("z");
      }
      if (layout.hasM()) {
        builder.inputM("m");
      }
      CompiledConversionPlan plan = compile(input, builder.build());
      Object[] row =
          new Object[] {
            1.0, 2L, layout.hasZ() ? BigDecimal.valueOf(3) : null, layout.hasM() ? 4.0 : null
          };

      Point point = (Point) runtime(plan).convert(row).row()[plan.outputBindings().get(0).index()];

      assertThat(point.getCoordinateSequence().getDimension()).isEqualTo(layout.dimension());
      assertThat(point.getCoordinateSequence().getMeasures()).isEqualTo(layout.measures());
      assertThat(point.getSRID()).isEqualTo(2056);
      assertThat(point.getX()).isEqualTo(1);
      assertThat(point.getY()).isEqualTo(2);
      if (layout.hasZ()) {
        assertThat(point.getCoordinateSequence().getZ(0)).isEqualTo(3);
      }
      if (layout.hasM()) {
        assertThat(point.getCoordinateSequence().getM(0)).isEqualTo(4);
      }
    }
  }

  @Test
  void coordinateInputNullAndInvalidTuplesFollowTheContract() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(number("x"));
    input.addValueMeta(number("y"));
    input.addValueMeta(number("z"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.GEOMETRY)
            .inputX("x")
            .inputY("y")
            .inputZ("z")
            .build();
    CompiledConversionPlan plan = compile(input, settings);
    GeometryConverterRuntime runtime = runtime(plan);

    assertThat(runtime.convert(new Object[] {null, null, null}).row()[1]).isNull();
    assertCode(
        () -> runtime.convert(new Object[] {null, 2.0, null}),
        GeometryCodecErrorCode.GC_PARTIAL_COORDINATE_NULL);
    assertCode(
        () -> runtime.convert(new Object[] {null, null, 3.0}),
        GeometryCodecErrorCode.GC_PARTIAL_COORDINATE_NULL);
    assertCode(
        () -> runtime.convert(new Object[] {Double.NaN, 2.0, null}),
        GeometryCodecErrorCode.GC_NONFINITE_COORDINATE);
    assertCode(
        () -> runtime.convert(new Object[] {1.0, Double.POSITIVE_INFINITY, null}),
        GeometryCodecErrorCode.GC_NONFINITE_COORDINATE);
    assertCode(
        () -> runtime.convert(new Object[] {1.0, 2.0, Double.POSITIVE_INFINITY}),
        GeometryCodecErrorCode.GC_NONFINITE_COORDINATE);
  }

  @Test
  void allOutputFormatsProduceTheExpectedTargetTypes() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    Geometry geometry = CODEC.readText("POINT Z (1 2 3)");
    geometry.setSRID(2056);
    for (ConverterOutputFormat format : ConverterOutputFormat.values()) {
      // GeoJSON has CRS/loss requirements and is covered by GeometryConverterGeoJsonTest.
      if (format == ConverterOutputFormat.GEOJSON) {
        continue;
      }
      ConverterTestSupport.SettingsBuilder builder =
          settings(ConverterInputFormat.WKT_EWKT, format).inputField("geom");
      if (format == ConverterOutputFormat.COORDINATES) {
        builder.outputX("xo").outputY("yo").outputZ("zo");
      }
      CompiledConversionPlan plan = compile(input, builder.build());
      ConversionResult result = runtime(plan).convert(new Object[] {"POINT Z (1 2 3)"});
      assertThat(result.row().length).isEqualTo(plan.outputRowMeta().size());
      if (format == ConverterOutputFormat.GEOMETRY) {
        assertThat(result.row()[1]).isInstanceOf(Geometry.class);
      } else if (format == ConverterOutputFormat.WKT || format == ConverterOutputFormat.EWKT) {
        assertThat(result.row()[1]).isInstanceOf(String.class);
      } else if (format == ConverterOutputFormat.WKB_BYTES
          || format == ConverterOutputFormat.EWKB_BYTES) {
        assertThat(result.row()[1]).isInstanceOf(byte[].class);
      } else if (format == ConverterOutputFormat.WKB_HEX
          || format == ConverterOutputFormat.EWKB_HEX) {
        assertThat(result.row()[1]).isInstanceOf(String.class);
      } else {
        assertThat(result.row()[1]).isEqualTo(1.0);
        assertThat(result.row()[2]).isEqualTo(2.0);
        assertThat(result.row()[3]).isEqualTo(3.0);
      }
    }
  }

  @Test
  void sridPoliciesDoNotMutateSourceAndSetExpectedTargetSrid() throws Exception {
    Geometry inputGeometry = CODEC.readText("POINT (1 2)");
    inputGeometry.setSRID(2056);
    RowMeta input = rowMeta();
    input.addValueMeta(geometry("geom"));
    for (ConverterSridMode mode :
        new ConverterSridMode[] {
          ConverterSridMode.KEEP,
          ConverterSridMode.SET_IF_MISSING,
          ConverterSridMode.REPLACE,
          ConverterSridMode.CLEAR
        }) {
      ConverterTestSupport.SettingsBuilder builder =
          settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
              .inputField("geom");
      if (mode == ConverterSridMode.SET_IF_MISSING || mode == ConverterSridMode.REPLACE) {
        builder.sridMode(mode).srid(4326);
      } else {
        builder.sridMode(mode);
      }
      CompiledConversionPlan plan = compile(input, builder.build());
      Geometry output = (Geometry) runtime(plan).convert(new Object[] {inputGeometry}).row()[1];
      int expected =
          switch (mode) {
            case KEEP -> 2056;
            case SET_IF_MISSING -> 2056;
            case REPLACE -> 4326;
            case CLEAR -> 0;
          };
      assertThat(output.getSRID()).isEqualTo(expected);
      assertThat(inputGeometry.getSRID()).isEqualTo(2056);
      assertThat(output).isNotSameAs(inputGeometry);
    }

    Geometry unknown = CODEC.readText("POINT (1 2)");
    GeometryConverterSettings setMissing =
        settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .sridMode(ConverterSridMode.SET_IF_MISSING)
            .srid(4326)
            .build();
    CompiledConversionPlan plan = compile(input, setMissing);
    assertThat(((Geometry) runtime(plan).convert(new Object[] {unknown}).row()[1]).getSRID())
        .isEqualTo(4326);
  }

  @Test
  void sridOmittedFlagMatchesTheOutputFormat() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    Geometry geometry = CODEC.readText("POINT (1 2)");
    geometry.setSRID(2056);
    for (ConverterOutputFormat format : ConverterOutputFormat.values()) {
      // GeoJSON has CRS/loss requirements and is covered by GeometryConverterGeoJsonTest.
      if (format == ConverterOutputFormat.GEOJSON) {
        continue;
      }
      ConverterTestSupport.SettingsBuilder builder =
          settings(ConverterInputFormat.WKT_EWKT, format).inputField("geom");
      if (format == ConverterOutputFormat.COORDINATES) {
        builder.outputX("xo").outputY("yo");
      }
      ConversionResult result =
          runtime(compile(input, builder.build())).convert(new Object[] {"SRID=2056;POINT (1 2)"});
      boolean expected =
          format == ConverterOutputFormat.WKT
              || format == ConverterOutputFormat.WKB_BYTES
              || format == ConverterOutputFormat.WKB_HEX
              || format == ConverterOutputFormat.COORDINATES;
      assertThat(result.sridOmitted()).as(format.name()).isEqualTo(expected);
    }
  }

  @Test
  void coordinateOutputExtractsPointOnlyAndHandlesEmptyAndNull() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
            .inputField("geom")
            .outputX("xo")
            .outputY("yo")
            .outputZ("zo")
            .outputM("mo")
            .build();
    CompiledConversionPlan plan = compile(input, settings);
    GeometryConverterRuntime runtime = runtime(plan);

    ConversionResult xym = runtime.convert(new Object[] {"POINT M (1 2 9)"});
    assertThat(xym.row()[1]).isEqualTo(1.0);
    assertThat(xym.row()[2]).isEqualTo(2.0);
    assertThat(xym.row()[3]).isNull();
    assertThat(xym.row()[4]).isEqualTo(9.0);

    ConversionResult empty = runtime.convert(new Object[] {"POINT EMPTY"});
    assertThat(empty.allTargetValuesNull()).isTrue();
    assertThat(empty.row()[1]).isNull();

    assertThat(runtime.convert(new Object[] {null}).allTargetValuesNull()).isTrue();
    assertCode(
        () -> runtime.convert(new Object[] {"LINESTRING (0 0, 1 1)"}),
        GeometryCodecErrorCode.GC_NOT_A_POINT);
    assertCode(
        () -> runtime.convert(new Object[] {"LINESTRING EMPTY"}),
        GeometryCodecErrorCode.GC_NOT_A_POINT);
  }

  @Test
  void nullOutputsSetsAllConfiguredTargetsToNullAndKeepsOtherFields() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("keep"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .overwrite(false)
            .build();
    CompiledConversionPlan plan = compile(input, settings);
    Object[] original = {"bad", "keep-me"};
    Object[] result = runtime(plan).nullOutputs(original);

    assertThat(result).isNotSameAs(original);
    assertThat(result[0]).isEqualTo("bad");
    assertThat(result[1]).isEqualTo("keep-me");
    assertThat(result[2]).isNull();
    assertThat(original).hasSize(2);
  }

  @Test
  void nativePathDoesNotUseStringBridgeAndRejectsForeignGeometry() throws Exception {
    Point source = (Point) CODEC.readText("POINT M (1 2 9)");
    RowMeta input = rowMeta();
    input.addValueMeta(new RejectingGetStringGeometryMeta("geom"));
    CompiledConversionPlan plan =
        compile(
            input,
            settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
                .inputField("geom")
                .build());
    Point output = (Point) runtime(plan).convert(new Object[] {source}).row()[1];
    assertThat(output).isNotSameAs(source);
    assertThat(output.getCoordinateSequence().getMeasures()).isEqualTo(1);

    Object foreign = new Object();
    assertThatThrownBy(() -> runtime(plan).convert(new Object[] {foreign}))
        .isInstanceOf(ConverterIntegrationException.class);
  }

  @Test
  void invalidBinaryIsReportedAsDataErrorAndEmptyBinaryIsNotSilentlyAccepted() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(binary("geom"));
    CompiledConversionPlan plan =
        compile(
            input,
            settings(ConverterInputFormat.WKB_BYTES, ConverterOutputFormat.GEOMETRY)
                .inputField("geom")
                .build());
    assertCode(
        () -> runtime(plan).convert(new Object[] {new byte[0]}),
        GeometryCodecErrorCode.GC_INVALID_WKB);
  }

  private static void assertCode(ThrowingCallable call, GeometryCodecErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOf(GeometryCodecException.class)
        .satisfies(
            error ->
                assertThat(((GeometryCodecException) error).code())
                    .as(error.getMessage())
                    .isEqualTo(code));
  }

  private static final class RejectingGetStringGeometryMeta extends ValueMetaGeometry {
    private RejectingGetStringGeometryMeta(String name) {
      super(name);
    }

    @Override
    public String getString(Object object) throws HopValueException {
      throw new HopValueException("Native converter path must not call getString");
    }
  }
}
