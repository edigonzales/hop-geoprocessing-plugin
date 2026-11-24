package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeometryConverterDialogTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void geoJsonEditsReachSavedMetadataOnlyAfterOk(boolean accept) throws Exception {
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        GeometryConverterMeta.class.getName(), TransformPluginType.class, Transform.class);
    GeometryConverterMeta meta = new GeometryConverterMeta();
    meta.setDefault();
    meta.setInputFieldName("geom");
    meta.setChanged(false);
    String before = meta.getXml();

    PipelineMeta pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta("Convert", meta));
    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    GeometryConverterDialog dialog =
        new GeometryConverterDialog(parent, new Variables(), meta, pipeline);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    display.asyncExec(() -> {
      Shell active = Arrays.stream(display.getShells())
          .filter(shell -> "Geometry Converter".equals(shell.getText()))
          .findFirst().orElse(null);
      try {
        assertThat(active).isNotNull();
        widget(dialog, "wOutputFormat", Combo.class).setText(ConverterOutputFormat.GEOJSON.name());
        assertThat(widget(dialog, "wOutputField", Combo.class).getText()).isEqualTo("geometry_json");
        widget(dialog, "wGeoJsonOutputProfile", Combo.class).setText(GeoJsonProfile.JSONFG10.name());
        widget(dialog, "wGeoJsonOutputContext", Combo.class).setText("context");
        widget(dialog, "wGeoJsonDropM", Button.class).setSelection(true);
        assertThat(meta.getXml()).isEqualTo(before);
        Arrays.stream(active.getChildren())
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> button.getText().equals(accept ? "OK" : "Cancel"))
            .findFirst().orElseThrow()
            .notifyListeners(SWT.Selection, new Event());
        assertThat(active.isDisposed()).isTrue();
      } catch (Throwable error) {
        failure.set(error);
        if (active == null) {
          parent.dispose();
        } else if (!active.isDisposed()) {
          active.close();
        }
      }
    });

    try {
      String result = dialog.open();
      if (failure.get() != null) {
        throw new AssertionError("Geometry Converter dialog interaction failed", failure.get());
      }
      if (accept) {
        assertThat(result).isEqualTo("Convert");
        assertThat(meta.hasChanged()).isTrue();
        GeometryConverterMeta saved = new GeometryConverterMeta();
        saved.loadXml(
            XmlHandler.loadXmlString("<transform>" + meta.getXml() + "</transform>", "transform"),
            null);
        assertThat(saved.getInputFieldName()).isEqualTo("geom");
        assertThat(saved.getOutputFormat()).isEqualTo(ConverterOutputFormat.GEOJSON);
        assertThat(saved.getOutputFieldName()).isEqualTo("geometry_json");
        assertThat(saved.getGeoJsonOutputProfile()).isEqualTo(GeoJsonProfile.JSONFG10);
        assertThat(saved.getGeoJsonOutputContextField()).isEqualTo("context");
        assertThat(saved.isGeoJsonDropM()).isTrue();
      } else {
        assertThat(result).isNull();
        assertThat(meta.hasChanged()).isFalse();
        assertThat(meta.getXml()).isEqualTo(before);
      }
    } finally {
      if (!parent.isDisposed()) {
        parent.dispose();
      }
    }
  }

  private static <T> T widget(GeometryConverterDialog dialog, String name, Class<T> type)
      throws ReflectiveOperationException {
    Field field = GeometryConverterDialog.class.getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(dialog));
  }
}
