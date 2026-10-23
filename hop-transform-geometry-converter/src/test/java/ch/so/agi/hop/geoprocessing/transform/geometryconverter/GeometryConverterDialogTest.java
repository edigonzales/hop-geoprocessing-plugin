package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.geoprocessing.core.converter.ConfigurationIssue;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class GeometryConverterDialogTest {
  @Test
  void defaultsAndVisibilityFollowTheSelectedRepresentations() {
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(baseMeta());

    assertThat(model.inputUsesCoordinates()).isFalse();
    assertThat(model.outputUsesCoordinates()).isFalse();
    assertThat(model.binaryOutput()).isFalse();
    assertThat(model.sridValueActive()).isFalse();

    model.inputFormat(ConverterInputFormat.COORDINATES);
    model.outputFormat(ConverterOutputFormat.COORDINATES);
    assertThat(model.inputUsesCoordinates()).isTrue();
    assertThat(model.outputUsesCoordinates()).isTrue();

    model.outputFormat(ConverterOutputFormat.WKB_BYTES);
    assertThat(model.binaryOutput()).isTrue();
    model.outputFormat(ConverterOutputFormat.WKB_HEX);
    assertThat(model.binaryOutput()).isTrue();
    model.outputFormat(ConverterOutputFormat.WKT);
    assertThat(model.binaryOutput()).isFalse();
  }

  @Test
  void modelIsAnIndependentDraft() {
    GeometryConverterMeta source = baseMeta();
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(source);
    model.inputField("${OTHER}");
    model.outputFormat(ConverterOutputFormat.EWKT);

    assertThat(source.getInputFieldName()).isEqualTo("geom");
    assertThat(source.getOutputFormat()).isEqualTo(ConverterOutputFormat.GEOMETRY);
    assertThat(model.toMeta().getInputFieldName()).isEqualTo("${OTHER}");
    assertThat(model.toMeta().getOutputFormat()).isEqualTo(ConverterOutputFormat.EWKT);
  }

  @Test
  void jsonOptionsStayInDraftUntilAccepted() {
    GeometryConverterMeta source = baseMeta();
    GeometryConverterDialogModel draft = GeometryConverterDialogModel.from(source);
    draft.outputFormat(ConverterOutputFormat.GEOJSON);
    draft.geoJsonOutputProfile(com.atolcd.hop.gis.geometry.codec.GeoJsonProfile.JSONFG10);
    draft.geoJsonOutputContextField("context");
    draft.geoJsonDropM(true);
    assertThat(source.getOutputFormat()).isEqualTo(ConverterOutputFormat.GEOMETRY);
    assertThat(source.isGeoJsonDropM()).isFalse();
    assertThat(source.getGeoJsonOutputContextField()).isEmpty();
    source.copyFrom(draft.toMeta());
    assertThat(source.getOutputFormat()).isEqualTo(ConverterOutputFormat.GEOJSON);
    assertThat(source.isGeoJsonDropM()).isTrue();
    assertThat(source.getGeoJsonOutputContextField()).isEqualTo("context");
    assertThat(draft.defaultOutputName(ConverterOutputFormat.GEOJSON)).isEqualTo("geometry_json");
  }

  @Test
  void inactiveInvalidSridTextIsNotParsedButActiveInvalidTextIsAValidationError() {
    GeometryConverterMeta meta = baseMeta();
    meta.setSridMode(ConverterSridMode.KEEP);
    meta.setSridValue("not-an-integer");
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(meta);
    assertThat(model.tryValidate(new Variables())).isEmpty();

    model.sridMode(ConverterSridMode.REPLACE);
    assertThat(model.tryValidate(new Variables()))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_CONFIG_INVALID);
    assertThat(model.canCommitWithDeferredVariables(new Variables())).isFalse();
  }

  @Test
  void unresolvedVariableIsDeferredButEmptyResolvedVariableIsNot() {
    GeometryConverterMeta meta = baseMeta();
    meta.setInputFieldName("${MISSING}");
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(meta);
    assertThat(model.tryValidate(new Variables()))
        .extracting(ConfigurationIssue::code)
        .containsExactly(ConfigurationIssue.GC_UNRESOLVED_VARIABLE);
    assertThat(model.hasOnlyDeferredVariableIssues(new Variables())).isTrue();
    assertThat(model.canCommitWithDeferredVariables(new Variables())).isTrue();

    Variables empty = new Variables();
    empty.setVariable("EMPTY", "");
    model.inputField("geom");
    model.outputField("${EMPTY}");
    assertThat(model.tryValidate(empty))
        .extracting(ConfigurationIssue::code)
        .contains(ConfigurationIssue.GC_CONFIG_INVALID);
    assertThat(model.canCommitWithDeferredVariables(empty)).isFalse();
  }

  @Test
  void sameInputAndOutputRepresentationsAndSwappedCoordinatesRemainExpressible() {
    GeometryConverterMeta meta = baseMeta();
    meta.setInputFormat(ConverterInputFormat.COORDINATES);
    meta.setOutputFormat(ConverterOutputFormat.COORDINATES);
    meta.setInputXFieldName("north");
    meta.setInputYFieldName("east");
    meta.setOutputXFieldName("east");
    meta.setOutputYFieldName("north");
    meta.setOverwriteExistingFields(true);
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(meta);

    assertThat(model.inputFormat()).isEqualTo(ConverterInputFormat.COORDINATES);
    assertThat(model.outputFormat()).isEqualTo(ConverterOutputFormat.COORDINATES);
    assertThat(model.inputX()).isEqualTo("north");
    assertThat(model.outputX()).isEqualTo("east");
    assertThat(model.overwriteExistingFields()).isTrue();
    assertThat(model.byteOrder()).isEqualTo(GeometryByteOrder.BIG_ENDIAN);
    assertThat(model.errorMode()).isEqualTo(ConverterErrorMode.FAIL);
  }

  @Test
  void defaultOutputNamesFollowTheExplicitFormatTable() {
    GeometryConverterDialogModel model = GeometryConverterDialogModel.from(baseMeta());
    assertThat(model.defaultOutputName(ConverterOutputFormat.GEOMETRY)).isEqualTo("geometry");
    assertThat(model.defaultOutputName(ConverterOutputFormat.WKT)).isEqualTo("geometry_wkt");
    assertThat(model.defaultOutputName(ConverterOutputFormat.EWKT)).isEqualTo("geometry_ewkt");
    assertThat(model.defaultOutputName(ConverterOutputFormat.WKB_BYTES)).isEqualTo("geometry_wkb");
    assertThat(model.defaultOutputName(ConverterOutputFormat.EWKB_BYTES))
        .isEqualTo("geometry_ewkb");
    assertThat(model.defaultOutputName(ConverterOutputFormat.WKB_HEX))
        .isEqualTo("geometry_wkb_hex");
    assertThat(model.defaultOutputName(ConverterOutputFormat.EWKB_HEX))
        .isEqualTo("geometry_ewkb_hex");
  }

  private static GeometryConverterMeta baseMeta() {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFieldName("geom");
    return meta;
  }
}
