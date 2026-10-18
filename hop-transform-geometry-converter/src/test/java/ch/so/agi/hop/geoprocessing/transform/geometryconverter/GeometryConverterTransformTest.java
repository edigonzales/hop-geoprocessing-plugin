package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformErrorMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class GeometryConverterTransformTest {
  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void failModeStopsAtTheFirstInvalidRow() throws Exception {
    Harness harness = harness(ConverterErrorMode.FAIL);
    harness.add("POINT (1 2)");
    harness.add("not wkt");
    harness.add("POINT (3 4)");

    assertThatThrownBy(harness::runAll).isInstanceOf(HopException.class);
    java.util.List<Object[]> rows = harness.outputRows();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)[1]).isInstanceOf(Geometry.class);
    assertThat(harness.data.errorRows).isEqualTo(1);
  }

  @Test
  void returnNullModeKeepsTheStreamAlignedAndPreservesFollowingRows() throws Exception {
    Harness harness = harness(ConverterErrorMode.RETURN_NULL);
    harness.add("POINT (1 2)");
    harness.add("not wkt");
    harness.add("POINT (3 4)");
    harness.add(null);

    harness.runAll();

    java.util.List<Object[]> rows = harness.outputRows();
    assertThat(rows).hasSize(4);
    assertThat(rows.get(0)[1]).isInstanceOf(Geometry.class);
    assertThat(rows.get(1)[1]).isNull();
    assertThat(rows.get(2)[1]).isInstanceOf(Geometry.class);
    assertThat(rows.get(3)[1]).isNull();
    assertThat(harness.data.errorRows).isEqualTo(1);
    assertThat(harness.data.nullOutputRows).isEqualTo(2);
  }

  @Test
  void transformRequiresAnEnabledErrorHopAtInitialization() throws Exception {
    GeometryConverterMeta meta = baseMeta(ConverterErrorMode.RETURN_NULL);
    TransformMeta transformMeta = new TransformMeta("Geometry Converter", meta);
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.addTransform(transformMeta);
    GeometryConverterData data = new GeometryConverterData();
    LocalPipelineEngine pipeline = new LocalPipelineEngine(pipelineMeta);
    pipeline.setRunning(true);
    GeometryConverter transform =
        new GeometryConverter(transformMeta, meta, data, 0, pipelineMeta, pipeline);
    assertThat(transform.init()).isFalse();
  }

  @Test
  void emptyInputFinishesWithoutArtificialRows() throws Exception {
    Harness harness = harness(ConverterErrorMode.FAIL);
    harness.runAll();
    assertThat(harness.outputRows()).isEmpty();
    assertThat(harness.data.inputRows).isZero();
    assertThat(harness.data.summaryLogged).isTrue();
  }

  private static Harness harness(ConverterErrorMode errorMode) {
    GeometryConverterMeta meta = baseMeta(errorMode);
    IRowMeta inputMeta = new RowMeta();
    inputMeta.addValueMeta(new ValueMetaString("geom"));
    BlockingRowSet input = new BlockingRowSet(20);
    BlockingRowSet output = new BlockingRowSet(20);
    TransformMeta transformMeta = new TransformMeta("Geometry Converter", meta);
    TransformMeta errorTarget = new TransformMeta("Error output", new GeometryConverterMeta());
    TransformErrorMeta errorMeta =
        new TransformErrorMeta(
            transformMeta, errorTarget, "errors", "error_desc", "error_fields", "error_codes");
    errorMeta.setEnabled(true);
    transformMeta.setTransformErrorMeta(errorMeta);
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.addTransform(transformMeta);
    pipelineMeta.addTransform(errorTarget);
    GeometryConverterData data = new GeometryConverterData();
    LocalPipelineEngine pipeline = new LocalPipelineEngine(pipelineMeta);
    pipeline.setRunning(true);
    GeometryConverter transform =
        new GeometryConverter(transformMeta, meta, data, 0, pipelineMeta, pipeline);
    transform.setInputRowMeta(inputMeta);
    transform.setInputRowSets(new java.util.ArrayList<>(java.util.List.of(input)));
    transform.setOutputRowSets(new java.util.ArrayList<>(java.util.List.of(output)));
    return new Harness(meta, inputMeta, input, output, transform, data);
  }

  private static GeometryConverterMeta baseMeta(ConverterErrorMode errorMode) {
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFormat(ConverterInputFormat.WKT_EWKT);
    meta.setInputFieldName("geom");
    meta.setOutputFormat(ConverterOutputFormat.GEOMETRY);
    meta.setOutputFieldName("geometry");
    meta.setErrorMode(errorMode);
    return meta;
  }

  private static final class Harness {
    final GeometryConverterMeta meta;
    final IRowMeta inputMeta;
    final BlockingRowSet input;
    final BlockingRowSet output;
    final GeometryConverter transform;
    final GeometryConverterData data;

    Harness(
        GeometryConverterMeta meta,
        IRowMeta inputMeta,
        BlockingRowSet input,
        BlockingRowSet output,
        GeometryConverter transform,
        GeometryConverterData data) {
      this.meta = meta;
      this.inputMeta = inputMeta;
      this.input = input;
      this.output = output;
      this.transform = transform;
      this.data = data;
    }

    void add(String wkt) {
      input.putRow(inputMeta, new Object[] {wkt});
    }

    void runAll() throws Exception {
      input.setDone();
      assertThat(transform.init()).isTrue();
      while (transform.processRow()) {
        // iterate
      }
    }

    java.util.List<Object[]> outputRows() {
      java.util.List<Object[]> rows = new java.util.ArrayList<>();
      Object[] row;
      while ((row = output.getRowImmediate()) != null) {
        rows.add(row);
      }
      return rows;
    }
  }
}
