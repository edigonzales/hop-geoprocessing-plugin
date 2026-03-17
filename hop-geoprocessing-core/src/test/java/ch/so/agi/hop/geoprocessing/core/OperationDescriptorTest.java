package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationDescriptorTest {

  @Test
  void displayLabelFormatsGeometryOperationWithGroupSuffix() {
    assertThat(OperationRegistry.find(TransformFamily.GEOMETRY_OPERATION, "buffer"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Buffer (Constructive)");

    assertThat(OperationRegistry.find(TransformFamily.GEOMETRY_OPERATION, "intersection"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Intersection (Overlay)");
  }

  @Test
  void displayLabelOmitsGroupForNonGeometryFamilies() {
    assertThat(OperationRegistry.find(TransformFamily.SPATIAL_PREDICATE, "intersects"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Intersects");

    assertThat(OperationRegistry.find(TransformFamily.LAYER_OVERLAY, "clip"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Clip");

    assertThat(OperationRegistry.find(TransformFamily.LAYER_AGGREGATE, "dissolve"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Dissolve");

    assertThat(OperationRegistry.find(TransformFamily.COVERAGE_OPERATION, "coverage_validate"))
        .get()
        .extracting(OperationDescriptor::displayLabel)
        .isEqualTo("Validate Coverage");
  }
}
