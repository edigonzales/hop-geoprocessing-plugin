package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class SpatialPredicatePersistenceTest {
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

    assertThat(restored.getInfoTransformName()).isEqualTo("secondary");
    assertThat(restored.getTransformIOMeta().getInfoStreams().get(0).getTransformMeta())
        .isSameAs(secondary);
  }
}
