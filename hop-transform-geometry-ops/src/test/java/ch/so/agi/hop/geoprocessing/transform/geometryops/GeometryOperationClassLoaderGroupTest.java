package ch.so.agi.hop.geoprocessing.transform.geometryops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class GeometryOperationClassLoaderGroupTest {

  @Test
  void shouldUseSharedGeometryClassLoaderGroup() {
    Transform annotation = GeometryOperationMeta.class.getAnnotation(Transform.class);

    assertNotNull(annotation);
    assertEquals("sogeo-geometry", annotation.classLoaderGroup());
  }
}
