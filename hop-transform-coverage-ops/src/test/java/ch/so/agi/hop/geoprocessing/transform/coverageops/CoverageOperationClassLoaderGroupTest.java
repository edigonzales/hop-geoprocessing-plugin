package ch.so.agi.hop.geoprocessing.transform.coverageops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class CoverageOperationClassLoaderGroupTest {

  @Test
  void shouldUseSharedGeometryClassLoaderGroup() {
    Transform annotation = CoverageOperationMeta.class.getAnnotation(Transform.class);

    assertNotNull(annotation);
    assertEquals("sogeo-geometry", annotation.classLoaderGroup());
  }
}
