package ch.so.agi.hop.geoprocessing.transform.coverageops;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import ch.so.agi.hop.geoprocessing.core.ParameterId;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class CoverageOperationMetaTest {

  @Test
  void validateDefaultsHoleConstraintToDisabledAndExposesParameter() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();

    assertThat(meta.isDisallowCoverageHoles()).isFalse();
    assertThat(meta.descriptor().requires(ParameterId.DISALLOW_HOLES)).isTrue();
  }

  @Test
  void getFieldsForValidateAddsBooleanAndErrorGeometry() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(4);
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("coverage_is_valid");
    assertThat(rowMeta.getValueMeta(2).getName()).isEqualTo("coverage_error");
    assertThat(rowMeta.getValueMeta(3).getName()).isEqualTo("coverage_error_type");
  }

  @Test
  void getFieldsForSimplifyReplaceKeepsSingleGeometryColumn() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setOperationId("coverage_simplify");
    meta.setGeometryFieldName("geometry");
    meta.setOutputMode(ch.so.agi.hop.geoprocessing.core.GeometryOutputMode.REPLACE);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(1);
    assertThat(rowMeta.getValueMeta(0)).isInstanceOf(ValueMetaGeometry.class);
  }

  @Test
  void checkRequiresToleranceForSimplify() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setOperationId("coverage_simplify");
    meta.setGeometryFieldName("geometry");
    meta.setDistanceValue("");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(
        remarks,
        null,
        null,
        rowMeta,
        new String[] {"upstream"},
        new String[0],
        null,
        new Variables(),
        null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .contains("A non-negative simplification tolerance is required.");
  }

  @Test
  void checkRejectsDuplicateValidationFields() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.setOutputFieldName("geometry");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(
        remarks,
        null,
        null,
        rowMeta,
        new String[] {"upstream"},
        new String[0],
        null,
        new Variables(),
        null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .contains("Error geometry field already exists on the input row.");
  }

  @Test
  void getFieldsForValidateFallsBackToDefaultErrorTypeFieldWhenMissing() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setErrorTypeFieldName(null);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getValueMeta(3).getName()).isEqualTo("coverage_error_type");
  }

  @Test
  void checkRejectsDuplicateErrorTypeField() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.setErrorTypeFieldName("geometry");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(
        remarks,
        null,
        null,
        rowMeta,
        new String[] {"upstream"},
        new String[0],
        null,
        new Variables(),
        null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .contains("Error type field already exists on the input row.");
  }

  @Test
  void targetStreamRoundTripUsesConfiguredRejectTransform() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TransformMeta rejectTransform = new TransformMeta("reject", null);
    meta.searchInfoAndTargetTransforms(List.of(rejectTransform));

    assertThat(meta.getRejectTransformName()).isEqualTo("reject");

    meta.convertIOMetaToTransformNames();

    assertThat(meta.getTransformIOMeta().getTargetStreams().get(0).getSubject()).isEqualTo("reject");
  }

  @Test
  void checkRejectsRejectTargetForNonValidateOperations() {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setOperationId("coverage_simplify");
    meta.setGeometryFieldName("geometry");
    meta.setDistanceValue("1.0");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(
        remarks,
        null,
        null,
        rowMeta,
        new String[] {"upstream"},
        new String[0],
        null,
        new Variables(),
        null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .contains("Reject target stream is only supported for Validate Coverage.");
  }
}
