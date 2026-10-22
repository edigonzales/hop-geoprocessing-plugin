package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import ch.so.agi.hop.geoprocessing.core.reproject.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.injection.bean.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.*;

class GeometryReprojectMetaInjectionTest {
  @BeforeAll static void initHop() throws Exception { HopClientEnvironment.init(); }
  @Test void actualHopInjectorSupportsEveryPersistedProperty() throws Exception {
    var meta = new GeometryReprojectMeta();
    BeanInjectionInfo<GeometryReprojectMeta> info = new BeanInjectionInfo(meta.getClass());
    var injector = new BeanInjector<GeometryReprojectMeta>(info, new MemoryMetadataProvider());
    var values = Map.of("configuration_version","1","geometry_field","geom","source_mode","EXPLICIT_EPSG",
        "source_epsg","${SOURCE}","target_epsg","3857","output_field","result",
        "overwrite_existing_field","Y","error_mode","RETURN_NULL");
    for (var value : values.entrySet()) {
      var rowsMeta = new RowMeta(); rowsMeta.addValueMeta(new ValueMetaString("f"));
      injector.setProperty(meta,value.getKey(),List.of(new RowMetaAndData(rowsMeta,value.getValue())),"f");
    }
    assertThat(meta.getConfigurationVersion()).isEqualTo(1);
    assertThat(meta.getGeometryField()).isEqualTo("geom");
    assertThat(meta.getSourceMode()).isEqualTo(ReprojectSourceMode.EXPLICIT_EPSG);
    assertThat(meta.getSourceEpsg()).isEqualTo("${SOURCE}");
    assertThat(meta.getTargetEpsg()).isEqualTo("3857");
    assertThat(meta.getOutputField()).isEqualTo("result");
    assertThat(meta.isOverwriteExistingField()).isTrue();
    assertThat(meta.getErrorMode()).isEqualTo(ReprojectErrorMode.RETURN_NULL);
  }
}
