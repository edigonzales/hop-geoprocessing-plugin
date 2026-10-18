package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKTReader;

class LayerOverlayStartupTest {
  @Test
  void firstRowsPublishMetadataBeforeFieldResolutionAndAreRetained() throws Exception {
    LayerOverlayMeta meta = new LayerOverlayMeta();
    meta.setDefault();
    meta.setInfoTransformName("secondary");
    meta.setPrimaryGeometryFieldName("geometry");
    meta.setSecondaryGeometryFieldName("geometry");
    DeferredMetadataOverlay transform = new DeferredMetadataOverlay(meta);
    WKTReader reader = new WKTReader();
    RowMeta primary = new RowMeta();
    primary.addValueMeta(new ValueMetaInteger("id"));
    primary.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.input("primary", primary, List.of(
        new Object[] {1L, reader.read("POINT (1 1)")},
        new Object[] {2L, reader.read("POINT (2 2)")}));
    RowMeta secondary = new RowMeta();
    secondary.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.input("secondary", secondary, List.<Object[]>of(
        new Object[] {reader.read("POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))")}));
    BlockingRowSet output = new BlockingRowSet(10);
    output.setThreadNameFromToCopy("overlay", 0, "output", 0);
    transform.addRowSetToOutputRowSets(output);

    while (transform.processRow()) {
      // Consume both input features.
    }
    List<Long> ids = new ArrayList<>();
    Object[] row;
    while ((row = output.getRowImmediate()) != null) {
      ids.add((Long) row[0]);
    }
    assertThat(ids).containsExactly(1L, 2L);
  }

  private static class DeferredMetadataOverlay extends LayerOverlay {
    private final Map<String, IRowSet> inputs = new LinkedHashMap<>();
    private final Map<IRowSet, IRowMeta> pending = new IdentityHashMap<>();

    DeferredMetadataOverlay(LayerOverlayMeta meta) {
      super(new TransformMeta("overlay", meta), meta, new LayerOverlayData(), 0,
          new PipelineMeta(), null);
    }

    void input(String origin, IRowMeta rowMeta, List<Object[]> rows) {
      BlockingRowSet input = new BlockingRowSet(10);
      input.setThreadNameFromToCopy(origin, 0, "overlay", 0);
      for (Object[] row : rows) {
        input.putRow(rowMeta, row);
      }
      input.setDone();
      pending.put(input, rowMeta);
      input.setRowMeta(null);
      inputs.put(origin, input);
    }

    @Override
    public void dispatch() {
      // Row sets are attached by the test.
    }

    @Override
    public List<IRowSet> getInputRowSets() {
      return new ArrayList<>(inputs.values());
    }

    @Override
    public IRowSet findInputRowSet(String name) {
      return inputs.get(name);
    }

    @Override
    public void putRow(IRowMeta rowMeta, Object[] row) {
      for (IRowSet output : getOutputRowSets()) {
        output.putRow(rowMeta, row);
      }
    }

    @Override
    public Object[] getRowFrom(IRowSet rowSet) {
      if (pending.containsKey(rowSet)) {
        rowSet.setRowMeta(pending.remove(rowSet));
      }
      return rowSet.getRowImmediate();
    }
  }
}
