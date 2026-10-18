package ch.so.agi.hop.geoprocessing.core.converter;

import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.CODEC;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.binary;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.compile;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.geometry;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.number;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.rowMeta;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.runtime;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.settings;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.CoordinateLayout;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecErrorCode;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import com.atolcd.hop.gis.geometry.codec.WkbWriteOptions;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/** OR11–OR16 and OR20 at the runtime level. OR14/OR19/OR22 need D/E. */
class OstRegressionRuntimeTest {
  @Test
  void or11CoordinateOutputDoesNotSilentlyDropRowsOrDuplicateIds() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(number("id"));
    input.addValueMeta(string("geom"));
    ConverterTestSupport.SettingsBuilder builder =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
            .inputField("geom")
            .outputX("xo")
            .outputY("yo");
    CompiledConversionPlan plan = compile(input, builder.build());
    GeometryConverterRuntime runtime = runtime(plan);
    List<String> rows =
        List.of(
            "POINT (1 2)",
            "POINT Z (2 3 4)",
            "POINT M (3 4 5)",
            "LINESTRING (0 0, 1 1)",
            "POLYGON ((0 0, 1 0, 1 1, 0 0))",
            "POINT (6 7)");

    List<Long> normal = new ArrayList<>();
    List<Long> errors = new ArrayList<>();
    int index = 1;
    for (String wkt : rows) {
      long id = index++;
      try {
        ConversionResult result = runtime.convert(new Object[] {id, wkt});
        normal.add((Long) result.row()[0]);
      } catch (GeometryCodecException e) {
        assertThat(e.code()).isEqualTo(GeometryCodecErrorCode.GC_NOT_A_POINT);
        errors.add(id);
      }
    }

    assertThat(normal).containsExactly(1L, 2L, 3L, 6L);
    assertThat(errors).containsExactly(4L, 5L);
    assertThat(normal).doesNotHaveDuplicates();
    assertThat(errors).doesNotHaveDuplicates();
  }

  @Test
  void or12InvalidCoordinatesAreNotSilentlyConvertedToNull() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(number("x"));
    input.addValueMeta(number("y"));
    input.addValueMeta(number("z"));
    CompiledConversionPlan plan =
        compile(
            input,
            settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.GEOMETRY)
                .inputX("x")
                .inputY("y")
                .inputZ("z")
                .build());
    GeometryConverterRuntime runtime = runtime(plan);

    assertThat(runtime.convert(new Object[] {null, null, null}).row()[3]).isNull();
    assertCode(
        () -> runtime.convert(new Object[] {null, 2.0, null}),
        GeometryCodecErrorCode.GC_PARTIAL_COORDINATE_NULL);
    assertCode(
        () -> runtime.convert(new Object[] {Double.NaN, 2.0, null}),
        GeometryCodecErrorCode.GC_NONFINITE_COORDINATE);
    assertCode(
        () -> runtime.convert(new Object[] {1.0, Double.POSITIVE_INFINITY, null}),
        GeometryCodecErrorCode.GC_NONFINITE_COORDINATE);

    Point withMissingZ = (Point) runtime.convert(new Object[] {1.0, 2.0, null}).row()[3];
    assertThat(withMissingZ.getCoordinateSequence().hasZ()).isTrue();
    assertThat(withMissingZ.getCoordinateSequence().getZ(0)).isNaN();
  }

  @Test
  void or13EmptyPointInBinaryAndTextProducesNullCoordinatesWithoutNpe() throws Exception {
    byte[] emptyPoint =
        CODEC.writeBinary(
                CODEC.readText("POINT EMPTY"), WkbWriteOptions.standardFieldBinary());
    for (Object payload :
        new Object[] {emptyPoint, "POINT EMPTY", CODEC.readText("POINT EMPTY")}) {
      RowMeta input = rowMeta();
      if (payload instanceof byte[] bytes) {
        input.addValueMeta(binary("geom"));
      } else if (payload instanceof String text) {
        input.addValueMeta(string("geom"));
      } else {
        input.addValueMeta(geometry("geom"));
      }
      ConverterInputFormat inputFormat =
          payload instanceof byte[]
              ? ConverterInputFormat.WKB_BYTES
              : payload instanceof String
                  ? ConverterInputFormat.WKT_EWKT
                  : ConverterInputFormat.GEOMETRY;
      CompiledConversionPlan plan =
          compile(
              input,
              settings(inputFormat, ConverterOutputFormat.COORDINATES)
                  .inputField("geom")
                  .outputX("xo")
                  .outputY("yo")
                  .build());
      ConversionResult result = runtime(plan).convert(new Object[] {payload});
      assertThat(result.allTargetValuesNull()).isTrue();
      assertThat(result.row()[1]).isNull();
      assertThat(result.row()[2]).isNull();
    }

    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    CompiledConversionPlan linePlan =
        compile(
            input,
            settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
                .inputField("geom")
                .outputX("xo")
                .outputY("yo")
                .build());
    assertCode(
        () -> runtime(linePlan).convert(new Object[] {"LINESTRING EMPTY"}),
        GeometryCodecErrorCode.GC_NOT_A_POINT);
  }

  @Test
  void or15ResolvedFieldNamesAndSchemaPlanDoNotChangeAfterVariableMutation() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("SOURCE_FIELD", "geom");
    String inputName =
        ConverterFieldNameResolver.resolveRequired("${SOURCE_FIELD}", "input_field", variables);
    RowMeta input = rowMeta();
    input.addValueMeta(string(inputName));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField(inputName)
            .build();
    CompiledConversionPlan plan = compile(input, settings);
    variables.setVariable("SOURCE_FIELD", "other");
    assertThat(plan.inputRowMeta().getValueMeta(plan.singleInputIndex()).getName())
        .isEqualTo("geom");
    assertThat(runtime(plan).convert(new Object[] {"POINT (1 2)"}).row()[1])
        .isInstanceOf(Geometry.class);
  }

  @Test
  void or16ExistingTargetInMiddleUsesItsIndexAndOverwriteOnlyWhenEnabled() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("geometry"));
    input.addValueMeta(string("tail"));
    GeometryConverterSettings overwrite =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .overwrite(true)
            .build();
    CompiledConversionPlan plan = compile(input, overwrite);
    assertThat(plan.outputBindings().get(0).index()).isEqualTo(1);
    assertThat(runtime(plan).convert(new Object[] {"POINT (1 2)", "old", "tail"}).row()[1])
        .isInstanceOf(Geometry.class);

    GeometryConverterSettings noOverwrite =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("geom")
            .build();
    assertThatThrownBy(() -> compile(input, noOverwrite))
        .isInstanceOf(ConverterConfigurationException.class);
  }

  @Test
  void or20NativeCurveEmptyAndMValueUseNoStringBridge() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(new RejectingGetStringGeometryMeta("geom"));
    CompiledConversionPlan plan =
        compile(
            input,
            settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
                .inputField("geom")
                .build());
    GeometryConverterRuntime runtime = runtime(plan);
    for (String wkt :
        new String[] {
          "CIRCULARSTRING (1 0, 0 1, -1 0)",
          "POINT EMPTY",
          "POINT M (1 2 9)",
          "GEOMETRYCOLLECTION (POINT (1 2), CIRCULARSTRING (1 0, 0 1, -1 0))"
        }) {
      Geometry source = CODEC.readText(wkt);
      Geometry output = (Geometry) runtime.convert(new Object[] {source}).row()[1];
      assertThat(output).isNotSameAs(source);
      assertThat(output.getClass()).isEqualTo(source.getClass());
      assertThat(output.getSRID()).isEqualTo(source.getSRID());
    }
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
