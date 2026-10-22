package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import ch.so.agi.hop.geoprocessing.core.reproject.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.*;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;

class GeometryReprojectMetaTest {
  @BeforeAll static void initHop() throws Exception { HopClientEnvironment.init(); }
  static GeometryReprojectMeta base() {
    var meta = new GeometryReprojectMeta(); meta.setDefault();
    meta.setGeometryField("geom"); meta.setTargetEpsg("3857"); return meta;
  }
  @Test void defaultsRequireExplicitTargetAndNativeField() {
    var meta = new GeometryReprojectMeta(); meta.setDefault();
    assertThat(meta.getSourceMode()).isEqualTo(ReprojectSourceMode.GEOMETRY_SRID);
    assertThat(meta.getTargetEpsg()).isEmpty();
    assertThat(meta.getOutputField()).isEqualTo("geometry_reprojected");
    assertThat(meta.isOverwriteExistingField()).isFalse();
    assertThat(meta.getErrorMode()).isEqualTo(ReprojectErrorMode.FAIL);
    assertThatThrownBy(() -> meta.toSettings(new Variables())).isInstanceOf(ReprojectConfigurationException.class);
  }
  @Test void resolvesOnlyActiveVariablesAndNeverChangesRawMetadata() throws Exception {
    var meta = base(); meta.setGeometryField("${SRC}"); meta.setTargetEpsg("${DST}");
    meta.setSourceEpsg("${IGNORED}");
    var variables = new Variables(); variables.setVariable("SRC", "shape"); variables.setVariable("DST", "epsg:4326");
    var settings = meta.toSettings(variables);
    assertThat(settings.geometryField()).isEqualTo("shape");
    assertThat(settings.targetEpsg()).isEqualTo("epsg:4326");
    assertThat(settings.sourceEpsg()).isNull();
    assertThat(meta.getGeometryField()).isEqualTo("${SRC}");
    assertThat(meta.getTargetEpsg()).isEqualTo("${DST}");
    assertThat(meta.getSourceEpsg()).isEqualTo("${IGNORED}");
    meta.setSourceMode(ReprojectSourceMode.EXPLICIT_EPSG);
    assertThatThrownBy(() -> meta.toSettings(variables)).isInstanceOf(GeometryReprojectMeta.DeferredVariableException.class);
  }
  @Test void rejectsInvalidEpsgExpressionsAndVersions() {
    var meta = base();
    for (String value : new String[] {"", "0", "-1", "+4326", "1.5", "1e3", "EPSG:0", "OGC:CRS84", "2147483648", "WKT"}) {
      meta.setTargetEpsg(value);
      assertThatThrownBy(() -> meta.toSettings(new Variables())).as(value).isInstanceOf(ReprojectConfigurationException.class);
    }
    meta.setTargetEpsg("3857"); meta.setConfigurationVersion(2);
    assertThatThrownBy(() -> meta.toSettings(new Variables())).hasMessageContaining("version");
  }
  @Test void schemaAppendsOrExplicitlyReplacesAFieldAndRetainsOrigin() throws Exception {
    var meta = base(); var rows = new RowMeta(); rows.addValueMeta(new ValueMetaGeometry("geom"));
    meta.getFields(rows, "reproject", null, null, new Variables(), null);
    assertThat(rows.size()).isEqualTo(2);
    assertThat(rows.getValueMeta(1)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(rows.getValueMeta(1).getOrigin()).isEqualTo("reproject");
    meta.setOutputField("geom");
    assertThatThrownBy(() -> meta.getFields(rows, "r", null, null, new Variables(), null)).hasMessageContaining("overwrite");
    meta.setOverwriteExistingField(true);
    meta.getFields(rows, "r", null, null, new Variables(), null);
    assertThat(rows.size()).isEqualTo(2);
    assertThat(rows.getValueMeta(0).getOrigin()).isEqualTo("r");
    var wrong = new RowMeta(); wrong.addValueMeta(new ValueMetaString("geom"));
    assertThatThrownBy(() -> meta.getFields(wrong, "r", null, null, new Variables(), null)).hasMessageContaining("native");
  }
  @Test void xmlRoundTripPreservesEveryPropertyIncludingInactiveText() throws Exception {
    var meta = base(); meta.setGeometryField("${GEOM}"); meta.setSourceMode(ReprojectSourceMode.EXPLICIT_EPSG);
    meta.setSourceEpsg("${SOURCE}"); meta.setTargetEpsg("${TARGET}"); meta.setOutputField("${OUT}");
    meta.setOverwriteExistingField(true); meta.setErrorMode(ReprojectErrorMode.ROUTE_TO_ERROR);
    var copy = new GeometryReprojectMeta();
    copy.loadXml(XmlHandler.loadXmlString("<transform>" + meta.getXml() + "</transform>", TransformMeta.XML_TAG), null);
    assertThat(copy.getXml()).isEqualTo(meta.getXml());
    assertThat(copy.getSourceEpsg()).isEqualTo("${SOURCE}");
    assertThat(copy.getSourceMode()).isEqualTo(ReprojectSourceMode.EXPLICIT_EPSG);
    assertThat(copy.getErrorMode()).isEqualTo(ReprojectErrorMode.ROUTE_TO_ERROR);
    assertThat(copy.isOverwriteExistingField()).isTrue();
  }
  @Test void checkRequiresErrorConnectionOnlyForRouteModeAndHandlesMissingSchema() {
    var meta = base(); var rows = new RowMeta(); rows.addValueMeta(new ValueMetaGeometry("geom"));
    var transform = new TransformMeta("Reproject", meta);
    for (var mode : ReprojectErrorMode.values()) {
      meta.setErrorMode(mode); var remarks = new ArrayList<ICheckResult>();
      meta.check(remarks,null,transform,rows,null,null,null,new Variables(),null);
      assertThat(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR))
          .isEqualTo(mode == ReprojectErrorMode.ROUTE_TO_ERROR);
    }
    var remarks = new ArrayList<ICheckResult>();
    meta.check(remarks,null,transform,null,null,null,null,new Variables(),null);
    assertThat(remarks).anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
    assertThat(meta.supportsErrorHandling()).isTrue();
  }
}
