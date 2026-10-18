package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class GeometryConverterPluginContractTest {
  @Test
  void transformAnnotationMatchesTheInstalledPluginContract() {
    Transform transform = GeometryConverterMeta.class.getAnnotation(Transform.class);
    assertThat(transform).isNotNull();
    assertThat(transform.id()).isEqualTo("GEOMETRY_CONVERTER_TRANSFORM");
    assertThat(transform.name()).isEqualTo("Geometry Converter");
    assertThat(transform.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(transform.categoryDescription()).isEqualTo("Geospatial");
    assertThat(transform.image())
        .isEqualTo(
            "ch/so/agi/hop/geoprocessing/transform/geometryconverter/icons/geometry-converter.svg");
    assertThat(transform.keywords())
        .contains("geometry", "WKT", "WKB", "EWKT", "EWKB", "coordinates", "point");
  }

  @Test
  void dialogClassIsDiscoverableByTheConvention() {
    assertThat(new GeometryConverterMeta().getDialogClassName())
        .isEqualTo(GeometryConverterDialog.class.getName());
  }
}
