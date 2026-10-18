package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.geoprocessing.core.converter.ConfigurationIssue;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterConfigurationException;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSettings;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.TransformErrorMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeometryConverterMetaTest {
  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void setDefaultProvidesCompleteValidDefaults() {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    assertThat(meta.getConfigurationVersion()).isEqualTo(1);
    assertThat(meta.getInputFormat()).isEqualTo(ConverterInputFormat.WKT_EWKT);
    assertThat(meta.getOutputFormat()).isEqualTo(ConverterOutputFormat.GEOMETRY);
    assertThat(meta.getByteOrder()).isEqualTo(GeometryByteOrder.BIG_ENDIAN);
    assertThat(meta.getSridMode()).isEqualTo(ConverterSridMode.KEEP);
    assertThat(meta.getErrorMode()).isEqualTo(ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode.FAIL);
    assertThat(meta.getOutputFieldName()).isEqualTo("geometry");
    assertThat(meta.getOutputXFieldName()).isEqualTo("x");
    assertThat(meta.getOutputYFieldName()).isEqualTo("y");
  }

  @Test
  void toSettingsResolvesOnlyActiveNamesAndPreservesRawMeta() throws Exception {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFieldName("${SRC}");
    meta.setOutputFieldName("${DST}");
    meta.setInputZFieldName("${INACTIVE_Z}");
    meta.setSridMode(ConverterSridMode.KEEP);
    meta.setSridValue("${INACTIVE_SRID}");
    Variables variables = new Variables();
    variables.setVariable("SRC", "geom_text");
    variables.setVariable("DST", "geom_out");

    GeometryConverterSettings settings = meta.toSettings(variables);

    assertThat(settings.inputFieldName()).isEqualTo("geom_text");
    assertThat(settings.outputFieldName()).isEqualTo("geom_out");
    assertThat(settings.inputZFieldName()).isNull();
    assertThat(settings.srid()).isNull();
    assertThat(meta.getInputFieldName()).isEqualTo("${SRC}");
    assertThat(meta.getOutputFieldName()).isEqualTo("${DST}");
    assertThat(meta.getInputZFieldName()).isEqualTo("${INACTIVE_Z}");
    assertThat(meta.getSridValue()).isEqualTo("${INACTIVE_SRID}");
  }

  @Test
  void activeSridValueIsStrictlyParsedAndInactiveValueIsIgnored() throws Exception {
    GeometryConverterMeta meta = baseMeta();
    meta.setSridMode(ConverterSridMode.REPLACE);
    meta.setSridValue("4326");
    assertThat(meta.toSettings(new Variables()).srid()).isEqualTo(4326);

    for (String invalid :
        new String[] {"", "0", "-1", "4.5", "1e3", "+4326", "2147483648", "not-a-number"}) {
      meta.setSridValue(invalid);
      assertThatThrownBy(() -> meta.toSettings(new Variables()))
          .as(invalid)
          .isInstanceOf(ConverterConfigurationException.class);
    }

    meta.setSridMode(ConverterSridMode.KEEP);
    meta.setSridValue("definitely-not-an-integer");
    assertThat(meta.toSettings(new Variables()).srid()).isNull();
  }

  @Test
  void unresolvedActiveVariableIsAConfigurationError() {
    GeometryConverterMeta meta = baseMeta();
    meta.setInputFieldName("${MISSING}");
    assertThatThrownBy(() -> meta.toSettings(new Variables()))
        .isInstanceOf(ConverterConfigurationException.class)
        .satisfies(error -> assertThat(issues(error))
            .extracting(ConfigurationIssue::code)
            .contains(ConfigurationIssue.GC_UNRESOLVED_VARIABLE));
  }

  @Test
  void getFieldsUsesTheSchemaCompilerAndSetsOriginAndNormalStorage() throws Exception {
    GeometryConverterMeta meta = baseMeta();
    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("geom"));
    RowMeta output = (RowMeta) input.clone();
    meta.getFields(output, "converter-origin", null, null, new Variables(), null);

    assertThat(output.size()).isEqualTo(2);
    assertThat(output.getValueMeta(1)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(output.getValueMeta(1).getName()).isEqualTo("geometry");
    assertThat(output.getValueMeta(1).getOrigin()).isEqualTo("converter-origin");
    assertThat(output.getValueMeta(1).getStorageType())
        .isEqualTo(org.apache.hop.core.row.IValueMeta.STORAGE_TYPE_NORMAL);
    assertThat(input.size()).isEqualTo(1);
  }

  @Test
  void xmlRoundTripPreservesAllRawProperties() throws Exception {
    GeometryConverterMeta meta = baseMeta();
    meta.setInputFormat(ConverterInputFormat.COORDINATES);
    meta.setInputXFieldName("${X}");
    meta.setInputYFieldName("${Y}");
    meta.setInputZFieldName("${Z}");
    meta.setInputMFieldName("");
    meta.setOutputFormat(ConverterOutputFormat.EWKB_BYTES);
    meta.setOutputFieldName("${OUT}");
    meta.setByteOrder(GeometryByteOrder.LITTLE_ENDIAN);
    meta.setSridMode(ConverterSridMode.SET_IF_MISSING);
    meta.setSridValue("${SRID}");
    meta.setOverwriteExistingFields(true);

    GeometryConverterMeta copy = new GeometryConverterMeta();
    copy.setDefault();
    copy.loadXml(
        XmlHandler.loadXmlString("<transform>" + meta.getXml() + "</transform>", TransformMeta.XML_TAG),
        null);

    assertThat(copy.getXml()).isEqualTo(meta.getXml());
    assertThat(copy.getInputXFieldName()).isEqualTo("${X}");
    assertThat(copy.getSridValue()).isEqualTo("${SRID}");
    assertThat(copy.getByteOrder()).isEqualTo(GeometryByteOrder.LITTLE_ENDIAN);
    assertThat(copy.isOverwriteExistingFields()).isTrue();
  }

  @Test
  void missingVersionPropertyIsTreatedAsVersionOne() throws Exception {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFieldName("geom");
    String xml = meta.getXml().replaceAll("<configuration_version>.*?</configuration_version>", "");
    GeometryConverterMeta loaded = new GeometryConverterMeta();
    loaded.loadXml(
        XmlHandler.loadXmlString("<transform>" + xml + "</transform>", TransformMeta.XML_TAG),
        null);
    assertThat(loaded.toSettings(new Variables()).configurationVersion()).isEqualTo(1);
  }

  @Test
  void unknownEnumValueIsRejectedByHopMetadataLoading() {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    String xml =
        "<transform><configuration_version>1</configuration_version>"
            + "<input_format>NOT_A_FORMAT</input_format></transform>";
    assertThatThrownBy(
            () ->
                meta.loadXml(
                    XmlHandler.loadXmlString(xml, TransformMeta.XML_TAG), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkReportsMissingErrorHopAndMissingUpstreamWithoutNpe() {
    GeometryConverterMeta meta = baseMeta();
    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, new TransformMeta(), null, null, null, null, new Variables(), null);
    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anyMatch(text -> text.contains(ConfigurationIssue.GC_ERROR_HOP_REQUIRED));
    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anyMatch(text -> text.contains(ConfigurationIssue.GC_MISSING_INPUT_FIELD));

    RowMeta prev = new RowMeta();
    prev.addValueMeta(new ValueMetaString("geom"));
    TransformMeta source = new TransformMeta();
    TransformMeta target = new TransformMeta();
    TransformErrorMeta errorMeta =
        new TransformErrorMeta(source, target, "errors", "error_desc", "error_fields", "error_codes");
    errorMeta.setEnabled(true);
    TransformMeta transformMeta = new TransformMeta();
    transformMeta.setTransformErrorMeta(errorMeta);
    remarks.clear();
    meta.check(remarks, null, transformMeta, prev, null, null, null, new Variables(), null);
    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .noneMatch(text -> text.contains(ConfigurationIssue.GC_ERROR_HOP_REQUIRED)
            && text.contains("requires an enabled"));
  }

  @Test
  void supportsErrorHandlingIsEnabled() {
    assertThat(new GeometryConverterMeta().supportsErrorHandling()).isTrue();
  }

  private static GeometryConverterMeta baseMeta() {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFieldName("geom");
    return meta;
  }

  private static List<ConfigurationIssue> issues(Throwable error) {
    return ((ConverterConfigurationException) error).issues();
  }
}
