package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;
import ch.so.agi.hop.geoprocessing.core.OverlayMode;

class LayerOverlayMetaTest {

  @Test
  void checkRequiresPrecisionScaleForFixedPrecisionMode() {
    LayerOverlayMeta meta = new LayerOverlayMeta();
    meta.setDefault();
    meta.setPrimaryGeometryFieldName("geometry_a");
    meta.setSecondaryGeometryFieldName("geometry_b");
    meta.setOverlayMode(OverlayMode.FIXED_PRECISION);
    meta.getTransformIOMeta().getInfoStreams().get(0).setSubject("secondary");

    RowMeta primaryRowMeta = new RowMeta();
    primaryRowMeta.addValueMeta(new ValueMetaGeometry("geometry_a"));

    RowMeta infoRowMeta = new RowMeta();
    infoRowMeta.addValueMeta(new ValueMetaGeometry("geometry_b"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(
        remarks,
        null,
        null,
        primaryRowMeta,
        new String[] {"primary"},
        new String[0],
        infoRowMeta,
        new Variables(),
        null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .contains("A positive precision scale is required in FIXED_PRECISION mode.");
  }
}
