package ch.so.agi.hop.geoprocessing.core.reproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class GeometryReprojectSchemaRuntimeTest {
  @Test
  void acceptsOnlyPositiveEpsgSyntaxAndIgnoresInactiveSourceExpression() throws Exception {
    assertThat(GeometryReprojectSchema.parseEpsg(" EPSG:2056 ", "Target")).isEqualTo(2056);
    assertThat(GeometryReprojectSchema.parseEpsg("4326", "Target")).isEqualTo(4326);
    for (String invalid : new String[] {null, "", "0", "-1", "EPSG:0", "OGC:CRS84", "EPSG:4326junk", "2147483648", "${UNRESOLVED}"}) {
      assertThatThrownBy(() -> GeometryReprojectSchema.parseEpsg(invalid, "Target")).isInstanceOf(ReprojectConfigurationException.class);
    }
    GeometryReprojectSchema.validateSettings(settings("output", false));
  }

  @Test
  void compilesNativeOutputWithoutChangingInputAndChecksCollisions() throws Exception {
    IRowMeta input = schema();
    var plan = GeometryReprojectSchema.compile(input, settings("output", false), "reproject");
    assertThat(input.size()).isEqualTo(2);
    assertThat(plan.outputRowMeta().size()).isEqualTo(3);
    assertThat(plan.outputRowMeta().getValueMeta(2)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(plan.outputRowMeta().getValueMeta(2).getOrigin()).isEqualTo("reproject");
    assertThatThrownBy(() -> GeometryReprojectSchema.compile(input, settings("geometry", false), "test")).isInstanceOf(ReprojectConfigurationException.class);
    var replacement = GeometryReprojectSchema.compile(input, settings("geometry", true), "test");
    assertThat(replacement.outputIndex()).isEqualTo(1);
    assertThat(replacement.outputRowMeta().size()).isEqualTo(2);
    assertThatThrownBy(() -> GeometryReprojectSchema.compile(input, new GeometryReprojectSettings("label", ReprojectSourceMode.GEOMETRY_SRID, null, "3857", "output", false, ReprojectErrorMode.FAIL), "test")).isInstanceOf(ReprojectConfigurationException.class);
  }

  @Test
  void runtimeCopiesRowsAndNullOutputOnlyTouchesItsBinding() throws Exception {
    var plan = GeometryReprojectSchema.compile(schema(), settings("geometry", true), "test");
    var runtime = new GeometryReprojectRuntime(plan, new GeoToolsReprojectionService(null));
    var geometry = new GeometryFactory().createPoint(new Coordinate(8, 47));
    geometry.setSRID(4326);
    Object[] input = {"attribute", geometry};
    Object[] result = runtime.reproject(input);
    assertThat(result).isNotSameAs(input);
    assertThat(result[0]).isEqualTo("attribute");
    assertThat(result[1]).isNotSameAs(geometry);
    assertThat(input[1]).isSameAs(geometry);
    Object[] nullRow = runtime.nullOutput(input);
    assertThat(nullRow).containsExactly("attribute", null);
    assertThat(runtime.reproject(new Object[] {"attribute", null})).containsExactly("attribute", null);
  }

  @Test
  void integrationFailuresCannotBeRoutedAsRowErrorsAndDataErrorsCarryFieldName() throws Exception {
    var plan = GeometryReprojectSchema.compile(schema(), settings("output", false), "test");
    var runtime = new GeometryReprojectRuntime(plan, new GeoToolsReprojectionService(null));
    assertThatThrownBy(() -> runtime.reproject(new Object[] {"label", "POINT (8 47)"})).isInstanceOf(ReprojectIntegrationException.class);
    assertThatThrownBy(() -> runtime.reproject(new Object[] {"label"})).isInstanceOf(ReprojectIntegrationException.class);
    var missingSrid = new GeometryFactory().createPoint(new Coordinate(8, 47));
    assertThatThrownBy(() -> runtime.reproject(new Object[] {"label", missingSrid}))
        .isInstanceOfSatisfying(ReprojectDataException.class, e -> {
          assertThat(e.fieldName()).isEqualTo("geometry");
          assertThat(e.code()).isEqualTo("GR_MISSING_SOURCE_SRID");
        });
  }

  private IRowMeta schema() {
    RowMeta row = new RowMeta();
    row.addValueMeta(new ValueMetaString("label"));
    row.addValueMeta(new ValueMetaGeometry("geometry"));
    return row;
  }

  private GeometryReprojectSettings settings(String output, boolean overwrite) {
    return new GeometryReprojectSettings("geometry", ReprojectSourceMode.GEOMETRY_SRID, "${INACTIVE}", "EPSG:3857", output, overwrite, ReprojectErrorMode.FAIL);
  }
}
