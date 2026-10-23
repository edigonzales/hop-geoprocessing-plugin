package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.injection.bean.BeanInjectionInfo;
import org.apache.hop.core.injection.bean.BeanInjector;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeometryConverterMetaInjectionTest {
  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void injectsAllPersistedPropertiesWithTheActualHopInjector() throws Exception {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    BeanInjectionInfo<GeometryConverterMeta> info = new BeanInjectionInfo(meta.getClass());
    BeanInjector<GeometryConverterMeta> injector =
        new BeanInjector(info, new MemoryMetadataProvider());

    Map<String, String> values = new LinkedHashMap<>();
    values.put("configuration_version", "1");
    values.put("input_format", "COORDINATES");
    values.put("input_field", "ignored_single");
    values.put("input_x_field", "east");
    values.put("input_y_field", "north");
    values.put("input_z_field", "height");
    values.put("input_m_field", "measure");
    values.put("output_format", "EWKB_BYTES");
    values.put("output_field", "encoded");
    values.put("output_x_field", "x_out");
    values.put("output_y_field", "y_out");
    values.put("output_z_field", "z_out");
    values.put("output_m_field", "m_out");
    values.put("byte_order", "LITTLE_ENDIAN");
    values.put("srid_mode", "REPLACE");
    values.put("srid_value", "4326");
    values.put("overwrite_existing_fields", "Y");
    values.put("error_mode", "ROUTE_TO_ERROR");
    values.put("geojson_input_profile", "JSONFG10");
    values.put("geojson_output_profile", "LEGACY2008");
    values.put("geojson_input_context_field", "context_in");
    values.put("geojson_output_context_field", "context_out");
    values.put("geojson_drop_z", "Y");
    values.put("geojson_drop_m", "Y");
    values.put("geojson_empty_to_null", "Y");

    for (Map.Entry<String, String> entry : values.entrySet()) {
      RowMeta rowsMeta = new RowMeta();
      ValueMetaString valueMeta = new ValueMetaString("f");
      rowsMeta.addValueMeta(valueMeta);
      List<RowMetaAndData> rows = new ArrayList<>();
      rows.add(new RowMetaAndData(rowsMeta, entry.getValue()));
      injector.setProperty(meta, entry.getKey(), rows, "f");
    }

    assertThat(meta.getConfigurationVersion()).isEqualTo(1);
    assertThat(meta.getInputFormat()).isEqualTo(ConverterInputFormat.COORDINATES);
    assertThat(meta.getInputXFieldName()).isEqualTo("east");
    assertThat(meta.getInputYFieldName()).isEqualTo("north");
    assertThat(meta.getInputZFieldName()).isEqualTo("height");
    assertThat(meta.getInputMFieldName()).isEqualTo("measure");
    assertThat(meta.getOutputFormat()).isEqualTo(ConverterOutputFormat.EWKB_BYTES);
    assertThat(meta.getOutputFieldName()).isEqualTo("encoded");
    assertThat(meta.getByteOrder()).isEqualTo(GeometryByteOrder.LITTLE_ENDIAN);
    assertThat(meta.getSridMode()).isEqualTo(ConverterSridMode.REPLACE);
    assertThat(meta.getSridValue()).isEqualTo("4326");
    assertThat(meta.isOverwriteExistingFields()).isTrue();
    assertThat(meta.getErrorMode()).isEqualTo(ConverterErrorMode.ROUTE_TO_ERROR);

    assertThat(meta.getGeoJsonInputProfile())
        .isEqualTo(com.atolcd.hop.gis.geometry.codec.GeoJsonProfile.JSONFG10);
    assertThat(meta.getGeoJsonOutputProfile())
        .isEqualTo(com.atolcd.hop.gis.geometry.codec.GeoJsonProfile.LEGACY2008);
    assertThat(meta.getGeoJsonInputContextField()).isEqualTo("context_in");
    assertThat(meta.getGeoJsonOutputContextField()).isEqualTo("context_out");
    assertThat(meta.isGeoJsonDropZ()).isTrue();
    assertThat(meta.isGeoJsonDropM()).isTrue();
    assertThat(meta.isGeoJsonEmptyToNull()).isTrue();
    var settings = meta.toSettings(new Variables());
    assertThat(settings.inputXFieldName()).isEqualTo("east");
    assertThat(settings.outputFormat()).isEqualTo(ConverterOutputFormat.EWKB_BYTES);
    assertThat(settings.srid()).isEqualTo(4326);
  }
}
