package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class SpatialPredicatePersistenceTest {
  @Test
  void selectedRejectSurvivesSerializationAndIoReset() throws Exception {
    var meta = new SpatialPredicateMeta();
    meta.setDefault();
    var reject = new org.apache.hop.pipeline.transform.TransformMeta("reject", null);
    var stream = meta.getTransformIOMeta().getTargetStreams().get(0);
    stream.setTransformMeta(reject);
    meta.handleStreamSelection(stream);
    String xml = "<transform>" + meta.getXml() + "</transform>";
    var restored = new SpatialPredicateMeta();
    restored.loadXml(org.apache.hop.core.xml.XmlHandler.loadXmlString(xml).getDocumentElement(), null);
    restored.resetTransformIoMeta();
    restored.searchInfoAndTargetTransforms(List.of(reject));
    assertThat(restored.getRejectTransformName()).isEqualTo("reject");
    assertThat(restored.cleanAfterHopToRemove(reject)).isFalse();
    assertThat(restored.cleanAfterHopFromRemove(reject)).isTrue();
    restored.resetTransformIoMeta();
    restored.searchInfoAndTargetTransforms(List.of(reject));
    assertThat(restored.getRejectTransformName()).isEmpty();
  }

  @Test
  void selectedInfoHopSurvivesResolutionBeforeFirstSave() {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    meta.setDefault();
    TransformMeta polygons = new TransformMeta("Polygons", null);
    var info = meta.getTransformIOMeta().getInfoStreams().get(0);

    // HopGuiPipelineGraph assigns the reference before invoking the callback.
    info.setTransformMeta(polygons);
    meta.handleStreamSelection(info);
    meta.searchInfoAndTargetTransforms(List.of(polygons));

    assertThat(info.getTransformMeta()).isSameAs(polygons);
    assertThat(info.getSubject()).isEqualTo("Polygons");
    assertThat(meta.getInfoTransformName()).isEqualTo("Polygons");
  }

  @Test
  void secondaryInfoStreamSurvivesPipelineSerialization() throws Exception {
    SpatialPredicateMeta original = new SpatialPredicateMeta();
    original.setDefault();
    TransformMeta secondary = new TransformMeta("secondary", new SpatialPredicateMeta());
    original.getTransformIOMeta().getInfoStreams().get(0).setTransformMeta(secondary);

    String xml = "<transform>" + original.getXml() + "</transform>";
    assertThat(xml).contains("<infoTransformName>secondary</infoTransformName>");
    SpatialPredicateMeta restored = new SpatialPredicateMeta();
    restored.setDefault();
    restored.loadXml(XmlHandler.loadXmlString(xml).getDocumentElement(), null);
    restored.searchInfoAndTargetTransforms(List.of(secondary));
    restored.resetTransformIoMeta();
    restored.searchInfoAndTargetTransforms(List.of(secondary));

    assertThat(restored.getInfoTransformName()).isEqualTo("secondary");
    assertThat(restored.getTransformIOMeta().getInfoStreams().get(0).getTransformMeta())
        .isSameAs(secondary);
  }

  @Test
  void rewireAndRenameWinOverPersistedName() throws Exception {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    TransformMeta original = new TransformMeta("A", null);
    TransformMeta replacement = new TransformMeta("B", null);
    meta.setInfoTransformName("A");
    meta.searchInfoAndTargetTransforms(List.of(original, replacement));
    var info = meta.getTransformIOMeta().getInfoStreams().get(0);
    info.setTransformMeta(replacement);
    meta.handleStreamSelection(info);
    replacement.setName("Renamed");
    meta.searchInfoAndTargetTransforms(List.of(original, replacement));

    assertThat(info.getTransformMeta()).isSameAs(replacement);
    assertThat(info.getSubject()).isEqualTo("Renamed");
    assertThat(meta.getXml()).contains("<infoTransformName>Renamed</infoTransformName>");
    meta.searchInfoAndTargetTransforms(List.of(original));
    assertThat(info.getTransformMeta()).isNull();
  }

  @Test
  void onlyRemovingTheInfoHopClearsItsAssignment() {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    TransformMeta primary = new TransformMeta("Points", null);
    TransformMeta secondary = new TransformMeta("Polygons", null);
    meta.setInfoTransformName(secondary.getName());
    meta.searchInfoAndTargetTransforms(List.of(primary, secondary));

    assertThat(meta.cleanAfterHopToRemove(primary)).isFalse();
    assertThat(meta.getInfoTransformName()).isEqualTo("Polygons");
    assertThat(meta.cleanAfterHopToRemove(secondary)).isTrue();
    assertThat(meta.cleanAfterHopToRemove(secondary)).isFalse();
    meta.resetTransformIoMeta();
    meta.searchInfoAndTargetTransforms(List.of(primary, secondary));
    var info = meta.getTransformIOMeta().getInfoStreams().get(0);
    assertThat(info.getTransformMeta()).isNull();
    assertThat(info.getSubject()).isEmpty();
    assertThat(meta.getInfoTransformName()).isEmpty();
  }

  @Test
  void ignoresOtherStreamsEvenWhenTheirDescriptionsMatch() {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    var io = meta.getTransformIOMeta();
    var otherInfo = new SpatialPredicateMeta().getTransformIOMeta().getInfoStreams().get(0);
    otherInfo.setTransformMeta(new TransformMeta("Other", null));
    meta.handleStreamSelection(otherInfo);
    meta.handleStreamSelection(io.getTargetStreams().get(0));
    meta.handleStreamSelection(null);

    assertThat(meta.getInfoTransformName()).isEmpty();
    assertThat(io.getInfoStreams().get(0).getTransformMeta()).isNull();
  }
}
