package ch.so.agi.hop.geoprocessing.transform.geometryops;

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
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
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
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GeometryOperationSwtDialogTest {
  @ParameterizedTest
  @EnumSource(DistanceMode.class)
  void editsLegacyDistanceWithoutLosingEitherValue(DistanceMode legacyMode) throws Exception {
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        GeometryOperationMeta.class.getName(), TransformPluginType.class, Transform.class);
    var meta = new GeometryOperationMeta();
    meta.setDefault();
    meta.loadXml(XmlHandler.loadXmlString("""
        <transform>
          <operationId>buffer</operationId>
          <primaryGeometryFieldName>geometry</primaryGeometryFieldName>
          <distanceMode>%s</distanceMode>
          <distanceValue>${DISTANCE}</distanceValue>
          <distanceFieldName>legacy_radius</distanceFieldName>
        </transform>
        """.formatted(legacyMode)).getDocumentElement(), null);
    var target = new TransformMeta("Geometry Operation", meta);
    var source = new TransformMeta("Source", new GeometrySource());
    var pipeline = new PipelineMeta();
    pipeline.addTransform(target);
    pipeline.addTransform(source);
    pipeline.addPipelineHop(new PipelineHopMeta(source, target));
    meta.setChanged(false);
    String before = meta.getXml();

    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    var failure = new AtomicReference<Throwable>();
    boolean accept = legacyMode == DistanceMode.STATIC;
    try {
      var dialog = new GeometryOperationDialog(parent, new Variables(), meta, pipeline);
      display.asyncExec(() -> {
        Shell shell = null;
        try {
          shell = Arrays.stream(display.getShells())
              .filter(candidate -> "Geometry Operation".equals(candidate.getText()))
              .findFirst().orElseThrow();
          Composite fields = Arrays.stream(shell.getChildren())
              .filter(control -> control.getClass() == Composite.class)
              .map(Composite.class::cast).findFirst().orElseThrow();
          ValueOrFieldControl distance = child(fields, ValueOrFieldControl.class);
          SourceMode mode = legacyMode == DistanceMode.FIELD ? SourceMode.FIELD : SourceMode.CONFIGURED;
          ValueOrField legacy = new ValueOrField(mode, "${DISTANCE}", "legacy_radius");
          assertThat(distance.getValue()).isEqualTo(legacy);
          assertThat(distance.getVisible()).isTrue();
          meta.setChanged(false);
          distance.setValue(new ValueOrField(mode, "3", "radius"));
          distance.setValue(legacy);
          assertThat(meta.hasChanged()).isFalse();

          Combo sourceMode = child(distance, Combo.class);
          selectSource(sourceMode, mode == SourceMode.FIELD ? 0 : 1);
          assertThat(meta.hasChanged()).isTrue();
          selectSource(sourceMode, 1);
          Composite editors = Arrays.stream(distance.getChildren())
              .filter(control -> control.getClass() == Composite.class)
              .map(Composite.class::cast).findFirst().orElseThrow();
          Combo field = Arrays.stream(editors.getChildren()).filter(control -> control.getClass() == Composite.class)
              .map(Composite.class::cast).flatMap(page -> Arrays.stream(page.getChildren()))
              .filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
          assertThat(field.getItems()).containsExactly("radius");
          assertThat(field.getText()).isEqualTo("legacy_radius");
          field.setText("radius");
          selectSource(sourceMode, 0);
          assertThat(distance.getValue()).isEqualTo(
              new ValueOrField(SourceMode.CONFIGURED, "${DISTANCE}", "radius"));

          String button = accept ? "OK" : "Cancel";
          Arrays.stream(shell.getChildren()).filter(Button.class::isInstance).map(Button.class::cast)
              .filter(candidate -> button.equals(candidate.getText())).findFirst().orElseThrow()
              .notifyListeners(SWT.Selection, new Event());
        } catch (Throwable error) {
          failure.set(error);
          if (shell != null && !shell.isDisposed()) {
            shell.close();
          }
        }
      });
      dialog.open();
      if (failure.get() != null) {
        throw new AssertionError("Geometry Operation distance editor failed", failure.get());
      }
      if (accept) {
        assertThat(meta.getXml()).contains(
            "<distanceMode>STATIC</distanceMode>",
            "<distanceValue>${DISTANCE}</distanceValue>",
            "<distanceFieldName>radius</distanceFieldName>");
      } else {
        assertThat(meta.getXml()).isEqualTo(before);
        assertThat(meta.hasChanged()).isFalse();
      }
    } finally {
      parent.dispose();
    }
  }

  private static void selectSource(Combo source, int index) {
    source.select(index);
    source.notifyListeners(SWT.Selection, new Event());
  }

  private static <T extends Control> T child(Composite parent, Class<T> type) {
    return Arrays.stream(parent.getChildren()).filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }

  private static class GeometrySource extends BaseTransformMeta<GeometryOperation, GeometryOperationData> {
    @Override
    public void getFields(
        IRowMeta row, String origin, IRowMeta[] info, TransformMeta next, IVariables variables,
        IHopMetadataProvider metadataProvider) {
      row.addValueMeta(new ValueMetaGeometry("geometry"));
      row.addValueMeta(new ValueMetaNumber("radius"));
      row.addValueMeta(new ValueMetaString("label"));
    }
  }
}
