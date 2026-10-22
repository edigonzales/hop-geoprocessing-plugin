package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import ch.so.agi.hop.geoprocessing.core.reproject.*;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class GeometryReprojectDialogTest {
  @Test void cancelDiscardsAllDraftPropertiesAndPreservesOriginalChangedFlag() throws Exception {
    var source = GeometryReprojectMetaTest.base(); source.setChanged(false); String xml = source.getXml();
    var model = GeometryReprojectDialogModel.from(source);
    model.draft().setGeometryField("different"); model.draft().setTargetEpsg("4326");
    model.draft().setOverwriteExistingField(true); model.draft().setErrorMode(ReprojectErrorMode.RETURN_NULL);
    assertThat(source.getXml()).isEqualTo(xml);
    assertThat(source.hasChanged()).isFalse();
    model.commitTo(source);
    assertThat(source.getGeometryField()).isEqualTo("different");
    assertThat(source.getTargetEpsg()).isEqualTo("4326");
  }
  @Test void sourceControlEnablesOnlyWithOverrideAndInactiveInvalidTextIsIgnored() {
    var model = GeometryReprojectDialogModel.from(GeometryReprojectMetaTest.base());
    model.draft().setSourceEpsg("not an EPSG");
    assertThat(model.sourceEpsgEnabled()).isFalse();
    assertThat(model.validate(new Variables())).isNull();
    model.draft().setSourceMode(ReprojectSourceMode.EXPLICIT_EPSG);
    assertThat(model.sourceEpsgEnabled()).isTrue();
    assertThat(model.validate(new Variables())).isNotNull();
  }
  @Test void deferredFieldDoesNotHideInvalidFixedEpsgOrMissingRequiredField() {
    var model = GeometryReprojectDialogModel.from(GeometryReprojectMetaTest.base());
    model.draft().setGeometryField("${GEOM}"); model.draft().setTargetEpsg("invalid");
    assertThat(model.hasDeferredVariables(new Variables())).isFalse();
    assertThat(model.validate(new Variables())).contains("positive EPSG");
    model.draft().setTargetEpsg("3857"); model.draft().setOutputField("");
    assertThat(model.hasDeferredVariables(new Variables())).isFalse();
  }
  @Test void upstreamSchemaReportsWrongTypeMissingFieldAndOutputCollision() {
    var model = GeometryReprojectDialogModel.from(GeometryReprojectMetaTest.base());
    var wrong = new org.apache.hop.core.row.RowMeta();
    wrong.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("geom"));
    model.inspectInput(wrong);
    assertThat(model.validate(new Variables())).contains("native");
    model.draft().setGeometryField("missing");
    assertThat(model.validate(new Variables())).contains("not found");
    var rows = new org.apache.hop.core.row.RowMeta();
    rows.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geom"));
    rows.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("geometry_reprojected"));
    model.draft().setGeometryField("geom"); model.inspectInput(rows);
    assertThat(model.validate(new Variables())).contains("overwrite");
    model.draft().setOverwriteExistingField(true);
    assertThat(model.validate(new Variables())).isNull();
  }
  @Test void upstreamInspectionFailurePersistsAndCannotBeSavedAsDeferred() {
    var model = GeometryReprojectDialogModel.from(GeometryReprojectMetaTest.base());
    model.draft().setTargetEpsg("${TARGET}"); model.inspectionFailed("broken upstream");
    assertThat(model.validate(new Variables())).contains("broken upstream");
    assertThat(model.hasDeferredVariables(new Variables())).isFalse();
    model.inspectInput(null);
    assertThat(model.validate(new Variables())).contains("No upstream fields");
  }
  @Test void runtimeVariablesCanBeSavedButEmptyResolvedValuesCannot() {
    var model = GeometryReprojectDialogModel.from(GeometryReprojectMetaTest.base());
    model.draft().setTargetEpsg("${TARGET}");
    assertThat(model.hasDeferredVariables(new Variables())).isTrue();
    var variables = new Variables(); variables.setVariable("TARGET","");
    assertThat(model.hasDeferredVariables(variables)).isFalse();
    assertThat(model.validate(variables)).contains("empty");
  }
}
