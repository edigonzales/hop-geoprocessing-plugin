package ch.so.agi.hop.geoprocessing.transform.layeraggregate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class LayerAggregateGroupingTest {

  @Test
  void groupValuesFollowOutputSchemaAndNullRowsFormOneGroup() throws Exception {
    var factory = new GeometryFactory();
    var rows = List.of(
        new Object[] {null, "B", factory.createPoint(new Coordinate(0, 0))},
        new Object[] {"A", "B", factory.createPoint(new Coordinate(1, 0))},
        new Object[] {null, "B", factory.createPoint(new Coordinate(2, 0))});
    for (String groups : List.of("a,b", "b,a")) {
      var meta = new LayerAggregateMeta();
      meta.setDefault();
      meta.setGroupFieldNames(groups);
      IRowMeta input = inputSchema();
      var iterator = rows.iterator();
      var output = new ArrayList<Object[]>();
      var transform = new LayerAggregate(
          new TransformMeta("aggregate", meta), meta, new LayerAggregateData(), 0,
          new PipelineMeta(), null) {
        @Override
        public void dispatch() {}

        @Override
        public Object[] getRow() {
          return iterator.hasNext() ? iterator.next() : null;
        }

        @Override
        public IRowMeta getInputRowMeta() {
          return input;
        }

        @Override
        public void putRow(IRowMeta rowMeta, Object[] row) {
          assertArrayEquals(new String[] {"a", "b", "aggregated_geometry"}, rowMeta.getFieldNames());
          output.add(row);
        }
      };
      while (transform.processRow()) {
        // Drain the transform.
      }

      assertEquals(2, output.size());
      assertNull(output.get(0)[0]);
      assertEquals("B", output.get(0)[1]);
      assertEquals(2, ((Geometry) output.get(0)[2]).getNumGeometries());
      assertEquals("A", output.get(1)[0]);
      assertEquals("B", output.get(1)[1]);
      assertEquals(1, ((Geometry) output.get(1)[2]).getNumGeometries());
    }
  }

  @Test
  void rejectsInvalidGroupAndOutputFields() {
    var meta = new LayerAggregateMeta();
    meta.setDefault();
    meta.setGroupFieldNames("missing");
    assertTrue(assertThrows(HopTransformException.class,
        () -> meta.getFields(inputSchema(), "aggregate", null, null, null, null))
        .getMessage().contains("Unknown group field"));
    meta.setGroupFieldNames("a,A");
    assertTrue(assertThrows(HopTransformException.class,
        () -> meta.getFields(inputSchema(), "aggregate", null, null, null, null))
        .getMessage().contains("Duplicate group field"));
    meta.setGroupFieldNames("a");
    meta.setOutputFieldName("b");
    assertTrue(assertThrows(HopTransformException.class,
        () -> meta.getFields(inputSchema(), "aggregate", null, null, null, null))
        .getMessage().contains("already exists"));
  }

  private IRowMeta inputSchema() {
    var input = new RowMeta();
    input.addValueMeta(new ValueMetaString("a"));
    input.addValueMeta(new ValueMetaString("b"));
    input.addValueMeta(new ValueMetaGeometry("geometry"));
    return input;
  }
}
