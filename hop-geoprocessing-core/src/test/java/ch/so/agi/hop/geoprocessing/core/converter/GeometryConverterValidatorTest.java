package ch.so.agi.hop.geoprocessing.core.converter;

import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.binary;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.geometry;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.number;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.rowMeta;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.settings;
import static ch.so.agi.hop.geoprocessing.core.converter.ConverterTestSupport.string;
import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import org.apache.hop.core.row.RowMeta;
import org.junit.jupiter.api.Test;

class GeometryConverterValidatorTest {
  @Test
  void acceptsValidTextToGeometryConfiguration() {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build();

    assertThat(GeometryConverterValidator.validateSettings(settings)).isEmpty();
    assertThat(GeometryConverterValidator.validateInputSchema(settings, input)).isEmpty();
  }

  @Test
  void reportsMissingAndWronglyTypedInputFields() {
    RowMeta input = rowMeta();
    input.addValueMeta(binary("geom"));
    GeometryConverterSettings wrongType =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build();
    assertThat(GeometryConverterValidator.validateInputSchema(wrongType, input))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_INPUT_FIELD_TYPE);

    GeometryConverterSettings missing =
        settings(ConverterInputFormat.GEOMETRY, ConverterOutputFormat.GEOMETRY)
            .inputField("missing")
            .build();
    assertThat(GeometryConverterValidator.validateInputSchema(missing, input))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_MISSING_INPUT_FIELD);
  }

  @Test
  void coordinateInputRequiresNumericXAndY() {
    RowMeta input = rowMeta();
    input.addValueMeta(string("x"));
    input.addValueMeta(number("y"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.GEOMETRY).build();

    assertThat(GeometryConverterValidator.validateInputSchema(settings, input))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_INPUT_FIELD_TYPE);
  }

  @Test
  void outputCollisionRequiresOverwrite() {
    RowMeta input = rowMeta();
    input.addValueMeta(string("geom"));
    input.addValueMeta(string("geometry"));
    GeometryConverterSettings noOverwrite =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).build();
    assertThat(GeometryConverterValidator.validateInputSchema(noOverwrite, input))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_OUTPUT_FIELD_EXISTS);

    GeometryConverterSettings overwrite =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .overwrite(true)
            .build();
    assertThat(GeometryConverterValidator.validateInputSchema(overwrite, input)).isEmpty();
  }

  @Test
  void duplicateOutputNamesAreRejectedCaseInsensitively() {
    RowMeta input = rowMeta();
    input.addValueMeta(number("x"));
    input.addValueMeta(number("y"));
    input.addValueMeta(number("z"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.COORDINATES, ConverterOutputFormat.COORDINATES)
            .outputX("target")
            .outputY("TARGET")
            .build();

    assertThat(GeometryConverterValidator.validateSettings(settings))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_DUPLICATE_OUTPUT_FIELD);
  }

  @Test
  void sridValueIsActiveOnlyForReplacingModesAndCoordinatesOutputKeepsSrid() {
    GeometryConverterSettings inactiveValue =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .sridMode(ConverterSridMode.KEEP)
            .srid(4326)
            .build();
    assertThat(GeometryConverterValidator.validateSettings(inactiveValue))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_CONFIG_INVALID);

    GeometryConverterSettings missingReplace =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY)
            .sridMode(ConverterSridMode.REPLACE)
            .build();
    assertThat(GeometryConverterValidator.validateSettings(missingReplace))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_CONFIG_INVALID);

    GeometryConverterSettings coordinatesReplace =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.COORDINATES)
            .sridMode(ConverterSridMode.REPLACE)
            .srid(4326)
            .build();
    assertThat(GeometryConverterValidator.validateSettings(coordinatesReplace))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_CONFIG_INVALID);
  }

  @Test
  void unsupportedVersionAndMissingFormatsAreRejected() {
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKT_EWKT, ConverterOutputFormat.GEOMETRY).version(2).build();
    assertThat(GeometryConverterValidator.validateSettings(settings))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_UNSUPPORTED_CONFIG_VERSION);

    GeometryConverterSettings missing =
        new GeometryConverterSettings(
            1,
            null,
            "geom",
            "x",
            "y",
            "",
            "",
            null,
            "geometry",
            "x",
            "y",
            "",
            "",
            GeometryByteOrder.BIG_ENDIAN,
            ConverterSridMode.KEEP,
            null,
            false,
            ConverterErrorMode.FAIL);
    assertThat(GeometryConverterValidator.validateSettings(missing))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_CONFIG_INVALID);
  }

  @Test
  void binaryInputAndGeometryOutputAreAccepted() {
    RowMeta input = rowMeta();
    input.addValueMeta(binary("payload"));
    GeometryConverterSettings settings =
        settings(ConverterInputFormat.WKB_BYTES, ConverterOutputFormat.GEOMETRY)
            .inputField("payload")
            .build();
    assertThat(GeometryConverterValidator.validateInputSchema(settings, input)).isEmpty();
  }
}
