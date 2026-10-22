package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import ch.so.agi.hop.geoprocessing.core.reproject.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.*;

class GeometryReprojectTransformTest {
  @BeforeAll static void initHop() throws Exception { HopClientEnvironment.init(); }
  private static Geometry point(int srid) { var g = new GeometryFactory().createPoint(new Coordinate(7,47)); g.setSRID(srid); return g; }
  @Test void failModeDoesNotRequireErrorHopAndStopsOnFirstInvalidRow() throws Exception {
    var h = new Harness(ReprojectErrorMode.FAIL,false); h.add(point(4326)); h.add(point(0)); h.add(point(4326));
    assertThatThrownBy(h::run).isInstanceOf(HopException.class).hasMessageContaining("GR_MISSING_SOURCE_SRID");
    assertThat(h.rows(h.output)).hasSize(1); assertThat(h.data.errorRows).isEqualTo(1);
  }
  @Test void returnNullPreservesAttributesAndContinuesWithoutErrorHop() throws Exception {
    var h = new Harness(ReprojectErrorMode.RETURN_NULL,false); Geometry original = point(4326);
    h.add(original); h.add(point(0)); h.add(null); h.add(point(4326)); h.run();
    var rows = h.rows(h.output); assertThat(rows).hasSize(4);
    assertThat(rows.get(0)[0]).isSameAs(original); assertThat(rows.get(0)[1]).isEqualTo("attribute");
    assertThat(((Geometry)rows.get(0)[2]).getSRID()).isEqualTo(3857);
    assertThat(rows.get(1)[2]).isNull(); assertThat(rows.get(2)[2]).isNull();
    assertThat(h.data.errorRows).isEqualTo(1); assertThat(h.data.nullOutputRows).isEqualTo(2);
  }
  @Test void routeModeRequiresAnEnabledTargetAndUsesActualHopErrorRows() throws Exception {
    var missing = new Harness(ReprojectErrorMode.ROUTE_TO_ERROR,false);
    assertThat(missing.transform.init()).isFalse();
    var h = new Harness(ReprojectErrorMode.ROUTE_TO_ERROR,true); Geometry invalid = point(0);
    h.add(point(4326)); h.add(invalid); h.add(point(4326)); h.run();
    assertThat(h.rows(h.output)).hasSize(2);
    var errors = h.rows(h.error); assertThat(errors).hasSize(1);
    assertThat(errors.get(0)[0]).isSameAs(invalid); assertThat(errors.get(0)[1]).isEqualTo("attribute");
    var errorMeta = h.transform.getErrorRowMeta();
    assertThat(errors.get(0)[errorMeta.indexOfValue("error_fields")]).isEqualTo("geom");
    assertThat(errors.get(0)[errorMeta.indexOfValue("error_codes")]).isEqualTo("GR_MISSING_SOURCE_SRID");
  }
  @Test void emptyStreamProducesNoSyntheticRowsAndDisposeReleasesRuntime() throws Exception {
    var h = new Harness(ReprojectErrorMode.FAIL,false); h.run();
    assertThat(h.rows(h.output)).isEmpty(); assertThat(h.data.inputRows).isZero();
    assertThat(h.data.summaryLogged).isTrue(); h.transform.dispose(); assertThat(h.data.service).isNull();
  }
  @Test void invalidFixedCrsFailsDuringInitEvenForEmptyStream() {
    var h = new Harness(ReprojectErrorMode.RETURN_NULL,false); h.meta.setTargetEpsg("99999999");
    assertThat(h.transform.init()).isFalse();
  }
  static final class Harness {
    final GeometryReprojectMeta meta = GeometryReprojectMetaTest.base();
    final IRowMeta inputMeta = new RowMeta();
    final BlockingRowSet input = new BlockingRowSet(30), output = new BlockingRowSet(30), error = new BlockingRowSet(30);
    final GeometryReprojectData data = new GeometryReprojectData();
    final GeometryReproject transform;
    Harness(ReprojectErrorMode mode,boolean errorHop) {
      meta.setErrorMode(mode); inputMeta.addValueMeta(new ValueMetaGeometry("geom")); inputMeta.addValueMeta(new ValueMetaString("attribute"));
      var tm = new TransformMeta("Reproject",meta); var pipelineMeta = new PipelineMeta(); pipelineMeta.addTransform(tm);
      var normalTarget = new TransformMeta("Normal output",new GeometryReprojectMeta());
      pipelineMeta.addTransform(normalTarget); pipelineMeta.addPipelineHop(new PipelineHopMeta(tm,normalTarget));
      if (errorHop) {
        var target = new TransformMeta("Error output",new GeometryReprojectMeta()); pipelineMeta.addTransform(target);
        var em = new TransformErrorMeta(tm,target,"errors","error_desc","error_fields","error_codes"); em.setEnabled(true); tm.setTransformErrorMeta(em);
        pipelineMeta.addPipelineHop(new PipelineHopMeta(tm,target));
      }
      output.setThreadNameFromToCopy("Reproject",0,"Normal output",0);
      error.setThreadNameFromToCopy("Reproject",0,"Error output",0);
      var pipeline = new LocalPipelineEngine(pipelineMeta);
      pipeline.rowsets = new ArrayList<>(errorHop ? List.of(output,error) : List.of(output));
      pipeline.setRunning(true);
      transform = new GeometryReproject(tm,meta,data,0,pipelineMeta,pipeline);
      transform.setInputRowMeta(inputMeta); transform.setInputRowSets(new ArrayList<>(List.of(input)));
      transform.setOutputRowSets(new ArrayList<>(errorHop ? List.of(output,error) : List.of(output)));

    }
    void add(Geometry geometry) { input.putRow(inputMeta,new Object[] {geometry,"attribute"}); }
    void run() throws Exception { input.setDone(); assertThat(transform.init()).isTrue(); transform.identifyErrorOutput(); while(transform.processRow()) {} }
    List<Object[]> rows(BlockingRowSet set) { var rows = new ArrayList<Object[]>(); Object[] row; while((row=set.getRowImmediate())!=null){ rows.add(row); } return rows; }
  }
}
