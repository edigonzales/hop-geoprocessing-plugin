package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Test;

/** Exercises real SWT events so cancel cannot accidentally commit widget modifications. */
class GeometryReprojectSwtDialogTest {
  @Test void editingAndCancellingNativeDialogPreservesMetadata() throws Exception {
    HopClientEnvironment.init();
    org.apache.hop.core.plugins.PluginRegistry.getInstance().registerPluginClass(
        GeometryReprojectMeta.class.getName(), org.apache.hop.core.plugins.TransformPluginType.class,
        org.apache.hop.core.annotations.Transform.class);
    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    var meta = GeometryReprojectMetaTest.base(); meta.setChanged(false);
    String before = meta.getXml();
    var pipeline = new PipelineMeta(); pipeline.addTransform(new TransformMeta("Reproject", meta));
    var dialog = new GeometryReprojectDialog(parent,new Variables(),meta,pipeline);
    var failure = new AtomicReference<Throwable>();
    display.asyncExec(() -> {
      Shell active = java.util.Arrays.stream(display.getShells())
          .filter(s -> "Geometry Reproject".equals(s.getText())).findFirst().orElseThrow();
      try {
        TextVar source = widget(dialog,"sourceEpsg",TextVar.class);
        assertThat(source.getTextWidget().isEnabled()).isFalse();
        widget(dialog,"sourceMode",Combo.class).setText("Explicit EPSG override");
        assertThat(source.getTextWidget().isEnabled()).isTrue();
        source.setText("4326"); widget(dialog,"targetEpsg",TextVar.class).setText("3857");
        widget(dialog,"outputField",Combo.class).setText("edited_output");
        assertThat(meta.getXml()).isEqualTo(before);
        assertThat(meta.hasChanged()).isFalse();
        savePreview(active);
        java.util.Arrays.stream(active.getChildren()).filter(c -> c instanceof Button)
            .map(c -> (Button)c).filter(b -> "Cancel".equals(b.getText())).findFirst().orElseThrow()
            .notifyListeners(SWT.Selection,new Event());
      } catch (Throwable e) { failure.set(e); if (!active.isDisposed()) { active.close(); } }
    });
    try {
      assertThat(dialog.open()).isNull();
      if (failure.get() != null) { throw new AssertionError("SWT dialog interaction failed",failure.get()); }
      assertThat(meta.getXml()).isEqualTo(before);
      assertThat(meta.hasChanged()).isFalse();
    } finally { parent.dispose(); }
  }
  private static <T> T widget(Object instance,String name,Class<T> type) throws Exception {
    Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true);
    return type.cast(field.get(instance));
  }
  private static void savePreview(Shell shell) throws Exception {
    var bounds = shell.getClientArea();
    Image image = new Image(shell.getDisplay(),bounds.width,bounds.height);
    GC graphics = new GC(shell);
    try {
      graphics.copyArea(image,0,0);
      ImageLoader loader = new ImageLoader(); loader.data = new ImageData[] {image.getImageData()};
      Files.createDirectories(Path.of("target"));
      loader.save("target/geometry-reproject-dialog.png",SWT.IMAGE_PNG);
    } finally { graphics.dispose(); image.dispose(); }
  }
}
