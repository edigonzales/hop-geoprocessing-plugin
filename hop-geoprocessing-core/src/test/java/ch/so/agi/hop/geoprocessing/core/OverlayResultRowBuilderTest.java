package ch.so.agi.hop.geoprocessing.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class OverlayResultRowBuilderTest {

  @Test
  void builderPrefixesSecondaryFieldsAndAvoidsCollisions() {
    RowMeta primaryRowMeta = new RowMeta();
    primaryRowMeta.addValueMeta(new ValueMetaInteger("id"));
    primaryRowMeta.addValueMeta(new ValueMetaString("b_name"));

    RowMeta secondaryRowMeta = new RowMeta();
    secondaryRowMeta.addValueMeta(new ValueMetaString("name"));
    secondaryRowMeta.addValueMeta(new ValueMetaGeometry("geometry"));

    OverlayResultRowBuilder builder =
        new OverlayResultRowBuilder(primaryRowMeta, secondaryRowMeta, "b_", "overlay_geometry");

    IRowMeta outputRowMeta = builder.createOutputRowMeta();

    assertThat(outputRowMeta.getFieldNames())
        .containsExactly("id", "b_name", "b_name_2", "b_geometry", "overlay_geometry");
  }

  @Test
  void builderCreatesOutputRowWithPrimaryAndSecondaryValues() {
    RowMeta primaryRowMeta = new RowMeta();
    primaryRowMeta.addValueMeta(new ValueMetaInteger("id"));

    RowMeta secondaryRowMeta = new RowMeta();
    secondaryRowMeta.addValueMeta(new ValueMetaString("name"));

    OverlayResultRowBuilder builder =
        new OverlayResultRowBuilder(primaryRowMeta, secondaryRowMeta, "b_", "overlay_geometry");
    Point point = new GeometryFactory().createPoint(new Coordinate(0, 0));
    FeatureRow secondaryFeature = new FeatureRow(new Object[] {"secondary"}, point, point.getEnvelopeInternal(), null, 1);

    Object[] row = builder.buildRow(new Object[] {7}, secondaryFeature, point);

    assertThat(row[0]).isEqualTo(7);
    assertThat(row[1]).isEqualTo("secondary");
    assertThat(row[2]).isEqualTo(point);
  }
}
