import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.value.ValueMetaPluginType;

/** Runs with only Hop libraries on the launcher classpath, never plugin JARs. */
public final class RuntimeIdentityProbe {
  public static void main(String[] args) throws Exception {
    if (Path.of("plugins/misc/hop-raster-type").toFile().exists()
        || Path.of("plugins/transforms/vector-raster").toFile().exists()) {
      throw new AssertionError("Reprojection must work without Raster Type or Vector/Raster");
    }
    HopEnvironment.init();
    PluginRegistry registry = PluginRegistry.getInstance();
    IPlugin transform = registry.findPluginWithId(
        TransformPluginType.class, "GEOMETRY_REPROJECT_TRANSFORM");
    IPlugin geometry = registry.findPluginWithId(ValueMetaPluginType.class, "43663879");
    if (transform == null || geometry == null) {
      throw new AssertionError("Missing Geometry Reproject or Geometry Type registration");
    }
    ClassLoader transformLoader = registry.getClassLoader(transform);
    ClassLoader geometryLoader = registry.getClassLoader(geometry);
    if (transformLoader != geometryLoader) {
      throw new AssertionError("Geometry and Reproject use different classloaders");
    }
    Path owner = Path.of("plugins/misc/hop-geometry-type").toRealPath();
    for (String name : new String[] {
        "org.locationtech.jts.geom.Geometry",
        "com.atolcd.hop.core.row.value.ValueMetaGeometry",
        "org.geotools.referencing.CRS",
        "org.geotools.referencing.factory.epsg.hsql.ThreadedHsqlEpsgFactory",
        "tech.units.indriya.function.Calculus"}) {
      Class<?> type = Class.forName(name, false, transformLoader);
      if (type != Class.forName(name, false, geometryLoader)) {
        throw new AssertionError("Different shared class identity: " + name);
      }
      Path origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toRealPath();
      if (!origin.startsWith(owner)) {
        throw new AssertionError("Shared runtime outside Geometry Type: " + origin);
      }
      System.out.println("REPROJECT IDENTITY OK " + name + " " + origin);
    }
    registry.loadClass(transform);
    System.out.println("Geometry Reproject plugin and shared EPSG runtime loaded successfully");
  }
}
