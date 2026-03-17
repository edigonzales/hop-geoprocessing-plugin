package ch.so.agi.hop.geoprocessing.transform.geometryops;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class GeometryOperationMetaTest {

  @Test
  void sortOperationsOrdersByGroupThenLabel() {
    List<OperationDescriptor> sorted = GeometryOperationDialog.sortOperations(new GeometryOperationMeta().listOperations());

    assertThat(sorted.subList(0, 7))
        .extracting(OperationDescriptor::id)
        .containsExactly(
            "explode",
            "line_merge",
            "buffer",
            "buffer_extended",
            "concave_hull",
            "convex_hull",
            "polygonize");

    assertThat(sorted.subList(sorted.size() - 4, sorted.size()))
        .extracting(OperationDescriptor::id)
        .containsExactly("difference", "intersection", "sym_difference", "union");
  }

  @Test
  void checkRequiresPrecisionScaleForReducePrecision() {
    GeometryOperationMeta meta = new GeometryOperationMeta();
    meta.setDefault();
    meta.setOperationId("reduce_precision");
    meta.setPrimaryGeometryFieldName("geometry");

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
        .contains("A positive precision scale is required for Reduce Precision.");
  }

  @Test
  void checkRequiresPrecisionScaleForFixedPrecisionOverlayMode() {
    GeometryOperationMeta meta = new GeometryOperationMeta();
    meta.setDefault();
    meta.setOperationId("intersection");
    meta.setPrimaryGeometryFieldName("geometry_a");
    meta.setSecondaryGeometryFieldName("geometry_b");
    meta.setOverlayMode(ch.so.agi.hop.geoprocessing.core.OverlayMode.FIXED_PRECISION);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry_a"));
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry_b"));

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
        .contains("A positive precision scale is required in FIXED_PRECISION mode.");
  }
}
