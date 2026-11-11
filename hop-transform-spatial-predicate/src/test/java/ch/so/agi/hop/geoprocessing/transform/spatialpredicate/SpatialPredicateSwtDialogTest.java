package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

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
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;

class SpatialPredicateSwtDialogTest {
  @Test
  void showsOnlyFieldsFromTheAssignedStreamsAndCancelsWithoutSaving() throws Exception {
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        SpatialPredicateMeta.class.getName(), TransformPluginType.class, Transform.class);

    var meta = new SpatialPredicateMeta();
    meta.setDefault();
    var predicate = new TransformMeta("Spatial Predicate", meta);
    var points = new TransformMeta("Points", new GeometrySource("point"));
    var polygons = new TransformMeta("Polygons", new GeometrySource("mask"));
    var pipeline = new PipelineMeta();
    pipeline.addTransform(points);
    pipeline.addTransform(polygons);
    pipeline.addTransform(predicate);
    pipeline.addPipelineHop(new PipelineHopMeta(points, predicate));
    var info = meta.getTransformIOMeta().getInfoStreams().get(0);
    info.setTransformMeta(polygons);
    meta.handleStreamSelection(info);
    pipeline.addPipelineHop(new PipelineHopMeta(polygons, predicate));

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
          Text source = field(fields, "Secondary info transform", Text.class);
          assertThat(source.getText()).isEqualTo("Polygons");
          assertThat(source.getEditable()).isFalse();
          assertThat(field(fields, "Primary geometry field", Combo.class).getItems())
              .containsExactly("point");
          assertThat(field(fields, "Secondary geometry field", Combo.class).getItems())
              .containsExactly("mask");
          field(fields, "Boolean output field", Text.class).setText("edited");
          Arrays.stream(shell.getChildren()).filter(Button.class::isInstance)
              .map(Button.class::cast).filter(b -> "Cancel".equals(b.getText()))
              .findFirst().orElseThrow().notifyListeners(SWT.Selection, new Event());
        } catch (Throwable error) {
          failure.set(error);
          if (shell != null && !shell.isDisposed()) {
            shell.close();
          }
        }
      });
      assertThat(dialog.open()).isNull();
      if (failure.get() != null) {
        throw new AssertionError("Spatial Predicate dialog interaction failed", failure.get());
      }
      assertThat(meta.getXml()).isEqualTo(before);
      assertThat(meta.hasChanged()).isFalse();
    } finally {
      parent.dispose();
    }
  }

  private static <T extends org.eclipse.swt.widgets.Control> T field(
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
    public void getFields(
        IRowMeta row, String origin, IRowMeta[] info, TransformMeta next, IVariables variables,
        IHopMetadataProvider metadataProvider) {
      row.addValueMeta(new ValueMetaGeometry(field));
    }
  }
}
