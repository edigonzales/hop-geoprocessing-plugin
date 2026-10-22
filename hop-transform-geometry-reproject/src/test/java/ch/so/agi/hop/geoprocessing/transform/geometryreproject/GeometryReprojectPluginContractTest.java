package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class GeometryReprojectPluginContractTest {
  @Test void registrationSharesGeometryRuntimeAndLinksCanonicalManual() {
    var annotation = GeometryReprojectMeta.class.getAnnotation(Transform.class);
    assertThat(annotation.id()).isEqualTo("GEOMETRY_REPROJECT_TRANSFORM");
    assertThat(annotation.name()).isEqualTo("Geometry Reproject");
    assertThat(annotation.categoryDescription()).isEqualTo("Geospatial");
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(annotation.documentationUrl()).endsWith("#geometry-reproject");
    assertThat(GeometryReprojectMeta.class.getClassLoader().getResource(annotation.image())).isNotNull();
    assertThat(new GeometryReprojectMeta().getDialogClassName()).isEqualTo(GeometryReprojectDialog.class.getName());
  }
}
