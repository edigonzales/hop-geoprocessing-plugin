package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformErrorMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpatialPredicateSwtDialogTest {
  @ParameterizedTest
  @CsvSource({"true, false", "false, false", "false, true"})
  void preservesOrExplicitlyAssignsTheInfoHop(boolean hasInfoHop, boolean accept)
      throws Exception {
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        SpatialPredicateMeta.class.getName(), TransformPluginType.class, Transform.class);

    var meta = new SpatialPredicateMeta();
    meta.setDefault();
    DistanceMode legacyMode = hasInfoHop ? DistanceMode.FIELD : DistanceMode.STATIC;
    meta.loadXml(XmlHandler.loadXmlString("""
        <transform>
          <operationId>within</operationId>
          <distanceMode>%s</distanceMode>
          <distanceValue>${THRESHOLD}</distanceValue>
          <distanceFieldName>point_distance</distanceFieldName>
        </transform>
        """.formatted(legacyMode)).getDocumentElement(), null);
    var predicate = new TransformMeta("Spatial Predicate", meta);
    var points = new TransformMeta("Points", new GeometrySource("point"));
    var polygons = new TransformMeta("Polygons", new GeometrySource("mask"));
    var pipeline = new PipelineMeta();
    pipeline.addTransform(points);
    pipeline.addTransform(polygons);
    pipeline.addTransform(predicate);
    pipeline.addPipelineHop(new PipelineHopMeta(points, predicate));
    var info = meta.getTransformIOMeta().getInfoStreams().get(0);
    if (hasInfoHop) {
      info.setTransformMeta(polygons);
      meta.handleStreamSelection(info);
    }
    pipeline.addPipelineHop(new PipelineHopMeta(polygons, predicate));
    var disabled = new TransformMeta("Disabled", new GeometrySource("disabled"));
    pipeline.addTransform(disabled);
    pipeline.addPipelineHop(new PipelineHopMeta(disabled, predicate, false));
    var errors = new TransformMeta("Errors", new GeometrySource(null));
    pipeline.addTransform(errors);
    var errorMeta = new TransformErrorMeta(errors);
    errorMeta.setEnabled(true);
    errorMeta.setTargetTransform(predicate);
    errors.setTransformErrorMeta(errorMeta);
    pipeline.addPipelineHop(new PipelineHopMeta(errors, predicate));

    String before = meta.getXml();
    meta.setChanged(false);
    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    var failure = new AtomicReference<Throwable>();
    try {
      var dialog = new SpatialPredicateDialog(parent, new Variables(), meta, pipeline);
      display.asyncExec(() -> {
        Shell shell = null;
        try {
          shell = Arrays.stream(display.getShells())
              .filter(s -> "Spatial Predicate".equals(s.getText())).findFirst().orElseThrow();
          Composite fields = Arrays.stream(shell.getChildren())
              .filter(c -> c.getClass() == Composite.class)
              .map(Composite.class::cast).findFirst().orElseThrow();
          Combo source = field(fields, "Secondary info transform", Combo.class);
          assertThat(source.getStyle() & SWT.READ_ONLY).isNotZero();
          assertThat(source.getItems()).containsExactly("(Select a producer)", "Points", "Polygons");
          var primary = field(fields, "Primary geometry field", Combo.class);
          var secondary = field(fields, "Secondary geometry field", Combo.class);
          if (hasInfoHop) {
            assertThat(source.getText()).isEqualTo("Polygons");
          } else {
            assertThat(source.getSelectionIndex()).isZero();
            assertThat(secondary.getItems()).isEmpty();
            assertThat(Arrays.stream(fields.getChildren())
                .filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText))
                .contains("No secondary info transform is assigned yet.");
            source.select(source.indexOf("Polygons"));
            source.notifyListeners(SWT.Selection, new Event());
            assertThat(info.getTransformMeta()).isNull();
          }
          assertThat(primary.getItems()).containsExactly("point");
          assertThat(secondary.getItems()).containsExactly("mask");
          ValueOrFieldControl distance = field(fields, "Distance", ValueOrFieldControl.class);
          assertThat(distance.getVisible()).isFalse();
          Combo operation = field(fields, "Operation", Combo.class);
          operation.select(operation.indexOf("Distance <= (any match)"));
          operation.notifyListeners(SWT.Modify, new Event());
          assertThat(distance.getVisible()).isTrue();
          SourceMode initialSource = hasInfoHop ? SourceMode.FIELD : SourceMode.CONFIGURED;
          ValueOrField legacy = new ValueOrField(initialSource, "${THRESHOLD}", "point_distance");
          assertThat(distance.getValue()).isEqualTo(legacy);
          meta.setChanged(false);
          distance.setValue(new ValueOrField(initialSource, "3", "other_distance"));
          distance.setValue(legacy);
          assertThat(meta.hasChanged()).isFalse();
          Combo distanceSource = Arrays.stream(distance.getChildren())
              .filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
          distanceSource.select(0);
          distanceSource.notifyListeners(SWT.Selection, new Event());
          distanceSource.select(1);
          distanceSource.notifyListeners(SWT.Selection, new Event());
          Composite editors = Arrays.stream(distance.getChildren())
              .filter(control -> control.getClass() == Composite.class).map(Composite.class::cast).findFirst().orElseThrow();
          Combo fieldEditor = Arrays.stream(editors.getChildren())
              .filter(control -> control.getClass() == Composite.class).map(Composite.class::cast)
              .flatMap(page -> Arrays.stream(page.getChildren()))
              .filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
          assertThat(meta.hasChanged()).isTrue();
          assertThat(fieldEditor.getItems()).containsExactly("point_distance");
          assertThat(distance.getValue()).isEqualTo(
              new ValueOrField(SourceMode.FIELD, "${THRESHOLD}", "point_distance"));
          field(fields, "Boolean output field", Text.class).setText("edited");
          String button = accept ? "OK" : "Cancel";
          Arrays.stream(shell.getChildren()).filter(Button.class::isInstance)
              .map(Button.class::cast).filter(b -> button.equals(b.getText()))
              .findFirst().orElseThrow().notifyListeners(SWT.Selection, new Event());
        } catch (Throwable error) {
          failure.set(error);
          if (shell != null && !shell.isDisposed()) {
            shell.close();
          }
        }
      });
      String result = dialog.open();
      if (failure.get() != null) {
        throw new AssertionError("Spatial Predicate dialog interaction failed", failure.get());
      }
      if (accept) {
        assertThat(result).isEqualTo("Spatial Predicate");
        assertThat(info.getTransformMeta()).isSameAs(polygons);
        assertThat(meta.getDistanceMode()).isEqualTo(DistanceMode.FIELD);
        assertThat(meta.getDistanceValue()).isEqualTo("${THRESHOLD}");
        assertThat(meta.getDistanceFieldName()).isEqualTo("point_distance");
        assertThat(meta.getXml()).contains("<infoTransformName>Polygons</infoTransformName>");
      } else {
        assertThat(result).isNull();
        assertThat(meta.getXml()).isEqualTo(before);
        assertThat(meta.hasChanged()).isFalse();
      }
    } finally {
      parent.dispose();
    }
  }

  private static <T extends Control> T field(
      Composite parent, String label, Class<T> type) {
    return Arrays.stream(parent.getChildren()).filter(type::isInstance).map(type::cast)
        .filter(c -> c.getData("label") instanceof Label l && label.equals(l.getText()))
        .findFirst().orElseThrow();
  }

  private static class GeometrySource extends BaseTransformMeta<SpatialPredicate, SpatialPredicateData> {
    private final String field;

    GeometrySource(String field) {
      this.field = field;
    }

    @Override
    public boolean supportsErrorHandling() {
      return true;
    }

    @Override
    public void getFields(
        IRowMeta row, String origin, IRowMeta[] info, TransformMeta next, IVariables variables,
        IHopMetadataProvider metadataProvider) {
      // Hop sources can expose a different schema for each destination.
      if (field != null && next != null && "Spatial Predicate".equals(next.getName())) {
        row.addValueMeta(new ValueMetaGeometry(field));
        row.addValueMeta(new ValueMetaNumber(field + "_distance"));
      }
    }
  }
}
