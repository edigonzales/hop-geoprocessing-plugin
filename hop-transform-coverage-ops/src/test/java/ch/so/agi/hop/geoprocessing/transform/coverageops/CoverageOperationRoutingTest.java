package ch.so.agi.hop.geoprocessing.transform.coverageops;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import ch.so.agi.hop.geoprocessing.core.CoverageValidationErrorType;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.LinearComponentExtracter;
import org.locationtech.jts.io.WKTReader;

class CoverageOperationRoutingTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final WKTReader wktReader = new WKTReader(geometryFactory);

  @Test
  void validateDuplicatesInvalidRowsToRejectTarget() throws Exception {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.setGapWidth("0.1");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TestCoverageOperation transform =
        new TestCoverageOperation(
            new TransformMeta("coverage", meta),
            meta,
            new CoverageOperationData(),
            0,
            new PipelineMeta());

    RowMeta inputRowMeta = new RowMeta();
    inputRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.setInput(inputRowMeta, List.of(
        row(wkt("POLYGON ((0 0, 1 0, 1 2, 0 2, 0 0))")),
        row(wkt("POLYGON ((1.05 0, 2.05 0, 2.05 2, 1.05 2, 1.05 0))"))));

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    List<Object[]> mainRows = drainRows(mainOutput);
    List<Object[]> rejectRows = drainRows(rejectOutput);

    long invalidMainRows = mainRows.stream().filter(row -> Boolean.FALSE.equals(row[1])).count();

    assertThat(mainRows).hasSize(2);
    assertThat(rejectRows).hasSize((int) invalidMainRows);
    assertThat(rejectRows).allMatch(row -> Boolean.FALSE.equals(row[1]));
    assertThat(rejectRows)
        .allMatch(
            row ->
                CoverageValidationErrorType.COVERAGE_INVALID.getCode().equals(row[3]));
  }

  @Test
  void validateLeavesRejectTargetEmptyForValidCoverage() throws Exception {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TestCoverageOperation transform =
        new TestCoverageOperation(
            new TransformMeta("coverage", meta),
            meta,
            new CoverageOperationData(),
            0,
            new PipelineMeta());

    RowMeta inputRowMeta = new RowMeta();
    inputRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.setInput(inputRowMeta, List.of(
        row(wkt("POLYGON ((0 0, 1 0, 1 2, 0 2, 0 0))")),
        row(wkt("POLYGON ((1 0, 2 0, 2 2, 1 2, 1 0))"))));

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    List<Object[]> mainRows = drainRows(mainOutput);
    List<Object[]> rejectRows = drainRows(rejectOutput);

    assertThat(mainRows).hasSize(2);
    assertThat(mainRows).allMatch(row -> Boolean.TRUE.equals(row[1]));
    assertThat(mainRows).allMatch(row -> row[2] == null);
    assertThat(mainRows).allMatch(row -> row[3] == null);
    assertThat(rejectRows).isEmpty();
  }

  @Test
  void validateDuplicatesHoleInvalidRowsToRejectTargetWhenCoverageHolesAreDisallowed()
      throws Exception {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.setDisallowCoverageHoles(true);
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TestCoverageOperation transform =
        new TestCoverageOperation(
            new TransformMeta("coverage", meta),
            meta,
            new CoverageOperationData(),
            0,
            new PipelineMeta());

    RowMeta inputRowMeta = new RowMeta();
    inputRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.setInput(
        inputRowMeta,
        List.<Object[]>of(
            row(
                wkt(
                    "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 1 3, 3 3, 3 1, 1 1))"))));

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    List<Object[]> mainRows = drainRows(mainOutput);
    List<Object[]> rejectRows = drainRows(rejectOutput);

    assertThat(mainRows).hasSize(1);
    assertThat(mainRows.get(0)[1]).isEqualTo(Boolean.FALSE);
    assertThat(mainRows.get(0)[2]).isInstanceOf(Geometry.class);
    assertThat(mainRows.get(0)[3])
        .isEqualTo(CoverageValidationErrorType.FORBIDDEN_HOLE.getCode());
    assertLineworkEquals((Geometry) mainRows.get(0)[2], "LINESTRING (1 1, 1 3, 3 3, 3 1, 1 1)");
    assertThat(rejectRows).hasSize(1);
    assertThat(rejectRows.get(0)[1]).isEqualTo(Boolean.FALSE);
    assertThat(rejectRows.get(0)[2]).isInstanceOf(Geometry.class);
    assertThat(rejectRows.get(0)[3])
        .isEqualTo(CoverageValidationErrorType.FORBIDDEN_HOLE.getCode());
    assertLineworkEquals((Geometry) rejectRows.get(0)[2], "LINESTRING (1 1, 1 3, 3 3, 3 1, 1 1)");
  }

  @Test
  void validateDuplicatesMultipleErrorRowsToRejectTargetWhenGapAndHoleChecksBothFail()
      throws Exception {
    CoverageOperationMeta meta = new CoverageOperationMeta();
    meta.setDefault();
    meta.setGeometryFieldName("geometry");
    meta.setGapWidth("0.1");
    meta.setDisallowCoverageHoles(true);
    meta.getTransformIOMeta().getTargetStreams().get(0).setSubject("reject");

    TestCoverageOperation transform =
        new TestCoverageOperation(
            new TransformMeta("coverage", meta),
            meta,
            new CoverageOperationData(),
            0,
            new PipelineMeta());

    RowMeta inputRowMeta = new RowMeta();
    inputRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    transform.setInput(
        inputRowMeta,
        List.of(
            row(
                wkt(
                    "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 1 3, 3 3, 3 1, 1 1))")),
            row(wkt("POLYGON ((4.05 0, 5.05 0, 5.05 4, 4.05 4, 4.05 0))"))));

    BlockingRowSet mainOutput = addOutputRowSet(transform, "main");
    BlockingRowSet rejectOutput = addOutputRowSet(transform, "reject");

    runTransform(transform);

    List<Object[]> mainRows = drainRows(mainOutput);
    List<Object[]> rejectRows = drainRows(rejectOutput);

    assertThat(mainRows).hasSize(2);
    assertThat(rejectRows).hasSize(2);

    assertThat(mainRows.get(0)[1]).isEqualTo(Boolean.FALSE);
    assertThat(mainRows.get(0)[3]).isEqualTo(CoverageValidationErrorType.MULTIPLE.getCode());
    assertLineworkEquals(
        (Geometry) mainRows.get(0)[2],
        "LINESTRING (4 0, 4 4)",
        "LINESTRING (1 1, 1 3, 3 3, 3 1, 1 1)");
    assertThat(mainRows.get(1)[1]).isEqualTo(Boolean.FALSE);
    assertThat(mainRows.get(1)[3]).isEqualTo(CoverageValidationErrorType.COVERAGE_INVALID.getCode());
    assertLineworkEquals((Geometry) mainRows.get(1)[2], "LINESTRING (4.05 0, 4.05 4)");

    assertThat(rejectRows.get(0)[1]).isEqualTo(Boolean.FALSE);
    assertThat(rejectRows.get(0)[3]).isEqualTo(CoverageValidationErrorType.MULTIPLE.getCode());
    assertLineworkEquals(
        (Geometry) rejectRows.get(0)[2],
        "LINESTRING (4 0, 4 4)",
        "LINESTRING (1 1, 1 3, 3 3, 3 1, 1 1)");
    assertThat(rejectRows.get(1)[1]).isEqualTo(Boolean.FALSE);
    assertThat(rejectRows.get(1)[3]).isEqualTo(CoverageValidationErrorType.COVERAGE_INVALID.getCode());
    assertLineworkEquals((Geometry) rejectRows.get(1)[2], "LINESTRING (4.05 0, 4.05 4)");
  }

  private BlockingRowSet addOutputRowSet(TestCoverageOperation transform, String destination) {
    BlockingRowSet output = new BlockingRowSet(10);
    output.setThreadNameFromToCopy("coverage", 0, destination, 0);
    transform.addRowSetToOutputRowSets(output);
    return output;
  }

  private void runTransform(CoverageOperation transform) throws HopException {
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

  private Object[] row(Geometry geometry) {
    return new Object[] {geometry};
  }

  private Geometry wkt(String value) throws Exception {
    return wktReader.read(value);
  }

  private void assertLineworkEquals(Geometry actual, String... expectedWkts) throws Exception {
    assertThat(actual).isNotNull();

    List<Geometry> actualComponents = extractSegments(actual);
    List<Geometry> expectedComponents = new ArrayList<>(expectedWkts.length);
    for (String expectedWkt : expectedWkts) {
      expectedComponents.addAll(extractSegments(wktReader.read(expectedWkt)));
    }

    assertThat(actualComponents).hasSize(expectedComponents.size());
    for (Geometry actualComponent : actualComponents) {
      assertThat(expectedComponents.removeIf(expected -> actualComponent.equalsTopo(expected))).isTrue();
    }
    assertThat(expectedComponents).isEmpty();
  }

  private List<Geometry> extractSegments(Geometry geometry) {
    List<Geometry> lines = new ArrayList<>();
    LinearComponentExtracter.getLines(geometry, lines, true);

    List<Geometry> segments = new ArrayList<>();
    for (Geometry lineGeometry : lines) {
      LineString line = (LineString) lineGeometry;
      for (int index = 1; index < line.getNumPoints(); index++) {
        segments.add(
            line.getFactory()
                .createLineString(
                    new Coordinate[] {line.getCoordinateN(index - 1), line.getCoordinateN(index)}));
      }
    }
    return segments;
  }

  private static class TestCoverageOperation extends CoverageOperation {

    private IRowMeta inputRowMeta;
    private List<Object[]> inputRows = List.of();
    private int inputIndex;

    TestCoverageOperation(
        TransformMeta transformMeta,
        CoverageOperationMeta meta,
        CoverageOperationData data,
        int copyNr,
        PipelineMeta pipelineMeta) {
      super(transformMeta, meta, data, copyNr, pipelineMeta, null);
    }

    @Override
    public void dispatch() {
      // Tests attach input/output row sets explicitly.
    }

    void setInput(IRowMeta rowMeta, List<Object[]> rows) {
      this.inputRowMeta = rowMeta;
      this.inputRows = new ArrayList<>(rows);
      this.inputIndex = 0;
    }

    @Override
    public Object[] getRow() {
      if (inputIndex >= inputRows.size()) {
        return null;
      }
      return inputRows.get(inputIndex++);
    }

    @Override
    public IRowMeta getInputRowMeta() {
      return inputRowMeta;
    }
  }
}
