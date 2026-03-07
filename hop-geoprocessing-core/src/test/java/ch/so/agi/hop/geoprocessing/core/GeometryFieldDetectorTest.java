package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;

class GeometryFieldDetectorTest {

  private final GeometryFieldDetector detector = new GeometryFieldDetector();

  @Test
  void detectsGeometryAndHeuristicCandidatesWithPriority() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));
    rowMeta.addValueMeta(new FakeGeometryValueMeta("shape"));
    rowMeta.addValueMeta(new ValueMetaString("geom_wkt"));
    rowMeta.addValueMeta(new ValueMetaBinary("raw_wkb"));

    List<GeometryFieldCandidate> candidates = detector.detectCandidates(rowMeta);

    assertThat(candidates).extracting(GeometryFieldCandidate::fieldName)
        .containsExactly("shape", "geom_wkt", "raw_wkb");
    assertThat(candidates.get(0).geometryValueMeta()).isTrue();
    assertThat(detector.chooseDefaultField(candidates)).isEqualTo("shape");
  }

  @Test
  void detectsGeometryByStableTypeId() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new FakeGeometryTypeIdMeta("geom"));

    List<GeometryFieldCandidate> candidates = detector.detectCandidates(rowMeta);

    assertThat(candidates).singleElement().extracting(GeometryFieldCandidate::fieldName).isEqualTo("geom");
    assertThat(candidates.get(0).geometryValueMeta()).isTrue();
  }

  @Test
  void returnsEmptyWhenNoGeometryCandidateExists() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));
    rowMeta.addValueMeta(new ValueMetaString("name"));

    assertThat(detector.detectCandidates(rowMeta)).isEmpty();
    assertThat(detector.chooseDefaultField(List.of())).isNull();
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

  private static class FakeGeometryTypeIdMeta extends ValueMetaString {
    FakeGeometryTypeIdMeta(String name) {
      super(name);
    }

    @Override
    public int getType() {
      return GeometryFieldDetector.GEOMETRY_TYPE_ID;
    }
  }
}
