package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;

class GeometryFieldSelectionResolverTest {

  private final GeometryFieldSelectionResolver resolver = new GeometryFieldSelectionResolver();

  @Test
  void keepsPreferredFieldWhenAvailable() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new FakeGeometryValueMeta("shape"));
    rowMeta.addValueMeta(new ValueMetaString("geom_wkt"));

    GeometryFieldSelection selection = resolver.resolve(rowMeta, "shape");

    assertThat(selection.fieldNames()).containsExactly("shape", "geom_wkt");
    assertThat(selection.selectedField()).isEqualTo("shape");
    assertThat(selection.warning()).isBlank();
  }

  @Test
  void fallsBackToDefaultFieldWhenPreferredFieldIsMissing() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new FakeGeometryValueMeta("shape"));
    rowMeta.addValueMeta(new ValueMetaString("geom_wkt"));

    GeometryFieldSelection selection = resolver.resolve(rowMeta, "missing_geom");

    assertThat(selection.selectedField()).isEqualTo("shape");
    assertThat(selection.warning()).contains("missing_geom");
  }

  @Test
  void returnsEmptySelectionWhenNoGeometryCandidateExists() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));

    GeometryFieldSelection selection = resolver.resolve(rowMeta, "geom");

    assertThat(selection.fieldNames()).isEmpty();
    assertThat(selection.selectedField()).isBlank();
  }

  @Test
  void requireFieldIndexFailsFastWhenNoGeometryFieldCanBeResolved() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));

    assertThatThrownBy(() -> resolver.requireFieldIndex(rowMeta, "geom", "Primary geometry field"))
        .isInstanceOf(HopTransformException.class)
        .hasMessageContaining("Primary geometry field");
  }

  private static class FakeGeometryValueMeta extends ValueMetaString {
    FakeGeometryValueMeta(String name) {
      super(name);
    }

    @Override
    public String getTypeDesc() {
      return "Geometry";
    }
  }
}
