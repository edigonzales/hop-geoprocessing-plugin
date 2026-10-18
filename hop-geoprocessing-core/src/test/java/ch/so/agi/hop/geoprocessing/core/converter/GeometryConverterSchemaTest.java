package ch.so.agi.hop.geoprocessing.core.converter;

import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.binary;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.compile;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.geometry;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.number;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.rowMeta;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.settings;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;

class GeometryConverterSchemaTest {
  @Test
  void appendsSingleTargetAndLeavesInputMetaUntouched() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("other"));
    IRowMeta snapshot = input.clone();
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build();

    CompiledConversionPlan plan = compile(input, settings);

    assertThat(plan.inputRowMeta().size()).isEqualTo(2);
    assertThat(plan.outputRowMeta().size()).isEqualTo(3);
    assertThat(plan.outputRowMeta().getValueMeta(2).getName()).isEqualTo("geometry");
    assertThat(plan.outputRowMeta().getValueMeta(2)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(plan.outputRowMeta().getValueMeta(2).getOrigin()).isEqualTo("test");
    assertThat(plan.outputBindings()).hasSize(1);
    assertThat(plan.outputBindings().get(0).index()).isEqualTo(2);
    assertThat(plan.outputBindings().get(0).role()).isEqualTo(OutputRole.GEOMETRY);
    assertThat(input.size()).isEqualTo(snapshot.size());
    assertThat(input.getValueMeta(0).getName()).isEqualTo(snapshot.getValueMeta(0).getName());
  }

  @Test
  void replacesExistingTargetInTheMiddleOnlyWithOverwrite() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("a"));
    input.addValueMeta(string("geometry"));
    input.addValueMeta(string("b"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("a")
            .overwrite(true)
            .build();

    CompiledConversionPlan plan = compile(input, settings);

    assertThat(plan.outputRowMeta().size()).isEqualTo(3);
    assertThat(plan.outputRowMeta().getValueMeta(1)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(plan.outputBindings().get(0).index()).isEqualTo(1);
    assertThat(input.getValueMeta(1)).isInstanceOf(ValueMetaString.class);

    GeometryConverterSettings noOverwrite =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("a")
            .build();
    assertThatThrownBy(() -> compile(input, noOverwrite))
        .isInstanceOf(ConverterConfigurationException.class);
  }

  @Test
  void coordinateOutputCreatesOnlyConfiguredBindingsInOrder() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
            .outputX("x_out")
            .outputY("y_out")
            .outputZ("z_out")
            .outputM("")
            .build();

    CompiledConversionPlan plan = compile(input, settings);

    assertThat(plan.outputRowMeta().size()).isEqualTo(4);
    assertThat(plan.outputBindings())
        .extracting(OutputBinding::role, OutputBinding::index)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(OutputRole.X, 1),
            org.assertj.core.groups.Tuple.tuple(OutputRole.Y, 2),
            org.assertj.core.groups.Tuple.tuple(OutputRole.Z, 3));
    assertThat(plan.outputRowMeta().getValueMeta(1)).isInstanceOf(ValueMetaNumber.class);
  }

  @Test
  void coordinateInputUsesDeclaredLayoutAndSupportsXYSwapTargets() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(number("east"));
    input.addValueMeta(number("north"));
    input.addValueMeta(number("height"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.COORDINATES)
            .inputX("east")
            .inputY("north")
            .inputZ("height")
            .outputX("north")
            .outputY("east")
            .outputZ("height")
            .overwrite(true)
            .build();

    CompiledConversionPlan plan = compile(input, settings);

    assertThat(plan.coordinateLayout()).isEqualTo(com.atolcd.hop.gis.geometry.codec.CoordinateLayout.XYZ);
    assertThat(plan.inputXIndex()).isEqualTo(0);
    assertThat(plan.inputYIndex()).isEqualTo(1);
    assertThat(plan.inputZIndex()).isEqualTo(2);
    assertThat(plan.outputBindings())
        .extracting(OutputBinding::role, OutputBinding::index)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(OutputRole.X, 1),
            org.assertj.core.groups.Tuple.tuple(OutputRole.Y, 0),
            org.assertj.core.groups.Tuple.tuple(OutputRole.Z, 2));
  }

  @Test
  void byteAndHexTargetsUseCorrectValueMetaAndOptions() throws Exception {
    RowMeta input = rowMeta();
    input.addValueMeta(binary("payload"));
    CompiledConversionPlan wkb =
        compile(
            input,
            settings(ConverterInputFormat.WKB_BYTES, ConverterOutputFormat.EWKB_BYTES)
                .inputField("payload")
                .outputField("encoded")
                .byteOrder(GeometryByteOrder.LITTLE_ENDIAN)
                .build());
    assertThat(wkb.outputRowMeta().getValueMeta(1)).isInstanceOf(ValueMetaBinary.class);
    assertThat(wkb.wkbWriteOptions().dialect())
        .isEqualTo(com.atolcd.hop.gis.geometry.codec.WkbDialect.EWKB);
    assertThat(wkb.wkbWriteOptions().byteOrder()).isEqualTo(GeometryByteOrder.LITTLE_ENDIAN);
    assertThat(wkb.wkbWriteOptions().sridEncoding())
        .isEqualTo(com.atolcd.hop.gis.geometry.codec.SridEncoding.ALWAYS);

    CompiledConversionPlan hex =
        compile(
            input,
            settings(ConverterInputFormat.WKB_BYTES, ConverterOutputFormat.WKT)
                .inputField("payload")
                .outputField("text")
                .build());
    assertThat(hex.outputRowMeta().getValueMeta(1)).isInstanceOf(ValueMetaString.class);
    assertThat(hex.wktWriteOptions().sridEncoding())
        .isEqualTo(com.atolcd.hop.gis.geometry.codec.SridEncoding.OMIT);
  }

  @Test
  void duplicateAndMissingSchemaFieldsAreRejected() {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    GeometryConverterSettings duplicate =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
            .outputX("same")
            .outputY("same")
            .build();
    assertThatThrownBy(() -> compile(input, duplicate))
        .isInstanceOf(ConverterConfigurationException.class);

    GeometryConverterSettings missing =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .inputField("missing")
            .build();
    assertThatThrownBy(() -> compile(input, missing))
        .isInstanceOf(ConverterConfigurationException.class);
  }
}
