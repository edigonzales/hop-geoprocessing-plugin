package ch.so.agi.hop.geoprocessing.transform.geometryops;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class GeoprocessingPluginContractTest {

  @Test
  void geometryOperationUsesTheSharedGeometryClassloaderGroup() {
    Transform transform = GeometryOperationMeta.class.getAnnotation(Transform.class);

    assertThat(transform).isNotNull();
    assertThat(transform.classLoaderGroup())
        .isEqualTo(GeoprocessingClassLoaderBootstrap.GEOMETRY_CLASSLOADER_GROUP);
  }
}
