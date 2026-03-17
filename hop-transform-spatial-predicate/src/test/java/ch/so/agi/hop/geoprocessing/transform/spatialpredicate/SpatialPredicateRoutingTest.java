package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateResultMode;

class SpatialPredicateRoutingTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final WKTReader wktReader = new WKTReader(geometryFactory);

  @Test
  void keepMatchedRoutesUnmatchedRowsToRejectTarget() throws Exception {
    TestSpatialPredicate transform = createTransform(SpatialPredicateResultMode.KEEP_MATCHED);

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    assertThat(ids(drainRows(mainOutput))).containsExactly(1L);
    assertThat(ids(drainRows(rejectOutput))).containsExactly(2L);
  }

  @Test
  void keepUnmatchedRoutesMatchedRowsToRejectTarget() throws Exception {
    TestSpatialPredicate transform = createTransform(SpatialPredicateResultMode.KEEP_UNMATCHED);

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    assertThat(ids(drainRows(mainOutput))).containsExactly(2L);
    assertThat(ids(drainRows(rejectOutput))).containsExactly(1L);
  }

  @Test
  void targetAndInfoStreamsRoundTripWithConfiguredNames() {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    meta.setDefault();
    meta.getTransformIOMeta().getInfoStreams().get(0).setSubject("secondary");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TransformMeta secondary = new TransformMeta("secondary", null);
    TransformMeta reject = new TransformMeta("reject", null);
    meta.searchInfoAndTargetTransforms(List.of(secondary, reject));

    assertThat(meta.getInfoTransformName()).isEqualTo("secondary");
    assertThat(meta.getRejectTransformName()).isEqualTo("reject");

    meta.convertIOMetaToTransformNames();

    assertThat(meta.getTransformIOMeta().getInfoStreams().get(0).getSubject()).isEqualTo("secondary");
    assertThat(meta.getTransformIOMeta().getTargetStreams().get(0).getSubject()).isEqualTo("reject");
  }

  @Test
  void checkRejectsRejectTargetForBooleanMode() {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    meta.setDefault();
    meta.setOperationId("within");
    meta.setPrimaryGeometryFieldName("geometry");
    meta.setSecondaryGeometryFieldName("geometry");
    meta.setResultMode(SpatialPredicateResultMode.BOOLEAN_COLUMN);
    meta.getTransformIOMeta().getInfoStreams().get(0).setSubject("secondary");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    RowMeta primaryRowMeta = new RowMeta();
    primaryRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    RowMeta infoRowMeta = new RowMeta();
    infoRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

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
        .contains("Reject target stream is only supported for KEEP_MATCHED and KEEP_UNMATCHED.");
  }

  private TestSpatialPredicate createTransform(SpatialPredicateResultMode resultMode) throws Exception {
    SpatialPredicateMeta meta = new SpatialPredicateMeta();
    meta.setDefault();
    meta.setOperationId("within");
    meta.setPrimaryGeometryFieldName("geometry");
    meta.setSecondaryGeometryFieldName("geometry");
    meta.setResultMode(resultMode);
    meta.getTransformIOMeta().getInfoStreams().get(0).setSubject("secondary");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TestSpatialPredicate transform =
        new TestSpatialPredicate(
            new TransformMeta("predicate", meta),
            meta,
            new SpatialPredicateData(),
            0,
            new PipelineMeta());

    RowMeta primaryRowMeta = new RowMeta();
    primaryRowMeta.addValueMeta(new ValueMetaInteger("id"));
    primaryRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.addInputRowSet(
        "primary",
        primaryRowMeta,
        List.of(
            new Object[] {1L, wktReader.read("POINT (1 1)")},
            new Object[] {2L, wktReader.read("POINT (5 5)")}));

    RowMeta secondaryRowMeta = new RowMeta();
    secondaryRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.addInputRowSet(
        "secondary",
        secondaryRowMeta,
        List.<Object[]>of(new Object[] {wktReader.read("POLYGON ((0 0, 3 0, 3 3, 0 3, 0 0))")}));

    return transform;
  }

  private BlockingRowSet addOutputRowSet(TestSpatialPredicate transform, String destination) {
    BlockingRowSet output = new BlockingRowSet(10);
    output.setThreadNameFromToCopy("predicate", 0, destination, 0);
    transform.addRowSetToOutputRowSets(output);
    return output;
  }

  private void runTransform(SpatialPredicate transform) throws HopException {
    while (transform.processRow()) {
      // keep consuming until the transform signals completion
    }
  }

  private List<Object[]> drainRows(IRowSet rowSet) {
    List<Object[]> rows = new ArrayList<>();
    Object[] row;
    while ((row = rowSet.getRow()) != null) {
      rows.add(row);
    }
    return rows;
  }

  private List<Long> ids(List<Object[]> rows) {
    return rows.stream().map(row -> (Long) row[0]).toList();
  }

  private static class TestSpatialPredicate extends SpatialPredicate {

    private final List<IRowSet> inputRowSets = new ArrayList<>();
    private final Map<String, IRowSet> inputRowSetsByOrigin = new java.util.LinkedHashMap<>();

    TestSpatialPredicate(
        TransformMeta transformMeta,
        SpatialPredicateMeta meta,
        SpatialPredicateData data,
        int copyNr,
        PipelineMeta pipelineMeta) {
      super(transformMeta, meta, data, copyNr, pipelineMeta, null);
    }

    @Override
    public void dispatch() {
      // Tests attach input/output row sets explicitly.
    }

    void addInputRowSet(String origin, RowMeta rowMeta, List<Object[]> rows) {
      BlockingRowSet input = new BlockingRowSet(10);
      input.setThreadNameFromToCopy(origin, 0, "predicate", 0);
      input.setRowMeta(rowMeta);
      for (Object[] row : rows) {
        input.putRow(rowMeta, row);
      }
      input.setDone();
      inputRowSets.add(input);
      inputRowSetsByOrigin.put(origin, input);
    }

    @Override
    public List<IRowSet> getInputRowSets() {
      return inputRowSets;
    }

    @Override
    public IRowSet findInputRowSet(String sourceTransformName) throws HopTransformException {
      IRowSet rowSet = inputRowSetsByOrigin.get(sourceTransformName);
      if (rowSet == null) {
        throw new HopTransformException("Input row set was not found: " + sourceTransformName);
      }
      return rowSet;
    }

    @Override
    public Object[] getRowFrom(IRowSet rowSet) {
      return rowSet.getRowImmediate();
    }
  }
}
