package ch.so.agi.hop.geoprocessing.transform.layeroverlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
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

class LayerOverlaySwtDialogTest {
  @ParameterizedTest
  @CsvSource({"true, false", "false, false", "false, true"})
  void preservesOrExplicitlyAssignsTheInfoHop(boolean hasInfoHop, boolean accept)
      throws Exception {
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        LayerOverlayMeta.class.getName(), TransformPluginType.class, Transform.class);

    var meta = new LayerOverlayMeta();
    meta.setDefault();
    var predicate = new TransformMeta("Layer Overlay", meta);
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
    String before = meta.getXml();
    meta.setChanged(false);
    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    var failure = new AtomicReference<Throwable>();
    try {
      var dialog = new LayerOverlayDialog(parent, new Variables(), meta, pipeline);
      display.asyncExec(() -> {
        Shell shell = null;
        try {
          shell = Arrays.stream(display.getShells())
              .filter(s -> "Layer Overlay".equals(s.getText())).findFirst().orElseThrow();
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
          field(fields, "Output geometry field", Text.class).setText("edited");
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
        throw new AssertionError("Layer Overlay dialog interaction failed", failure.get());
      }
      if (accept) {
        assertThat(result).isEqualTo("Layer Overlay");
        assertThat(info.getTransformMeta()).isSameAs(polygons);
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

  private static class GeometrySource extends BaseTransformMeta<LayerOverlay, LayerOverlayData> {
    private final String field;

    GeometrySource(String field) {
      this.field = field;
    }

    @Override
    public void getFields(
        IRowMeta row, String origin, IRowMeta[] info, TransformMeta next, IVariables variables,
        IHopMetadataProvider metadataProvider) {
      // Hop sources can expose a different schema for each destination.
      if (field != null && next != null && "Layer Overlay".equals(next.getName())) {
        row.addValueMeta(new ValueMetaGeometry(field));
      }
    }
  }
}
