package ch.so.agi.hop.geoprocessing.core.reproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoToolsReferencingRuntimeTest {
  private static final String NUMBER_SYSTEM_SERVICE = "META-INF/services/tech.units.indriya.spi.NumberSystem";

  @Test
  void restoresContextClassLoaderAfterSuccessDataErrorAndIntegrationFailure() throws Exception {
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    ClassLoader sentinel = new ClassLoader(original) {};
    Thread.currentThread().setContextClassLoader(sentinel);
    try {
      assertThat(GeoToolsReferencingRuntime.scoped(() -> "ok")).isEqualTo("ok");
      assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(sentinel);
      assertThatThrownBy(() -> GeoToolsReferencingRuntime.scoped(() -> {
        throw new ReprojectDataException("GR_TEST", "test");
      })).isInstanceOf(ReprojectDataException.class);
      assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(sentinel);
      assertThatThrownBy(() -> GeoToolsReferencingRuntime.scoped(() -> {
        throw new NoClassDefFoundError("missing runtime dependency");
      })).isInstanceOf(ReprojectIntegrationException.class);
      assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(sentinel);
    } finally {
      Thread.currentThread().setContextClassLoader(original);
    }
  }

  @Test
  void coldIsolatedRuntimeTransformsSwissCoordinatesWithInvisibleIndriyaService(@TempDir Path temp)
      throws Exception {
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Path output = temp.resolve("cold-runtime.log");
    Path classpathFile = temp.resolve("plugin-classpath.txt");
    Files.writeString(classpathFile, classpath);
    String launcherClasspath = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    // Keep plugin libraries out of the outer JVM loader, as in the installed-Hop probe.
    Process process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString(),
        "-DEPSG-HSQL.directory=" + temp.resolve("epsg-database"),
        "-cp", launcherClasspath, ColdRuntimeProbe.class.getName(), classpathFile.toString())
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    if (!process.waitFor(45, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Cold GeoTools runtime probe timed out");
    }
    assertThat(process.exitValue()).withFailMessage(Files.readString(output)).isZero();
    assertThat(Files.readString(output)).contains("Cold referencing runtime passed");
  }

  /** Reflection-only entry point; deliberately runs without plugin JARs in the outer classpath. */
  public static final class ColdRuntimeProbe {
    public static void main(String[] args) throws Exception {
      URL[] urls = Arrays.stream(Files.readString(Path.of(args[0])).split(File.pathSeparator)).map(path -> {
        try {
          return new File(path).toURI().toURL();
        } catch (Exception e) {
          throw new IllegalArgumentException(e);
        }
      }).toArray(URL[]::new);
      ClassLoader original = Thread.currentThread().getContextClassLoader();
      try (ServiceHidingClassLoader isolated = new ServiceHidingClassLoader(urls)) {
        Class<?> serviceClass = isolated.loadClass("ch.so.agi.hop.geoprocessing.core.reproject.GeoToolsReprojectionService");
        Object service = serviceClass.getConstructor(Consumer.class).newInstance((Consumer<String>) message -> {});
        Class<?> readerClass = isolated.loadClass("org.locationtech.jts.io.WKTReader");
        Object reader = readerClass.getConstructor().newInstance();
        Object point = readerClass.getMethod("read", String.class).invoke(reader, "POINT (2600000 1200000)");
        Class<?> geometryClass = isolated.loadClass("org.locationtech.jts.geom.Geometry");
        geometryClass.getMethod("setSRID", int.class).invoke(point, 2056);
        Object result = serviceClass.getMethod("reproject", geometryClass, Integer.class, int.class).invoke(service, point, null, 4326);
        Object coordinate = geometryClass.getMethod("getCoordinate").invoke(result);
        double x = coordinate.getClass().getField("x").getDouble(coordinate);
        double y = coordinate.getClass().getField("y").getDouble(coordinate);
        if (Math.abs(x - 7.438632495) > 0.00003 || Math.abs(y - 46.951082877) > 0.00003) {
          throw new AssertionError("Unexpected Swiss reference coordinate: " + x + ", " + y);
        }
        Class<?> calculus = isolated.loadClass("tech.units.indriya.function.Calculus");
        Object numberSystem = calculus.getMethod("currentNumberSystem").invoke(null);
        if (!isolated.serviceWasHidden
            || !numberSystem.getClass().getName().equals("tech.units.indriya.function.DefaultNumberSystem")) {
          throw new AssertionError("Indriya fallback was not exercised");
        }
        if (Thread.currentThread().getContextClassLoader() != original) {
          throw new AssertionError("Context classloader was not restored");
        }
      }
      System.out.println("Cold referencing runtime passed");
    }
  }

  private static final class ServiceHidingClassLoader extends URLClassLoader {
    private boolean serviceWasHidden;

    private ServiceHidingClassLoader(URL[] urls) {
      super(urls, ClassLoader.getPlatformClassLoader());
    }

    @Override
    public URL getResource(String name) {
      if (NUMBER_SYSTEM_SERVICE.equals(name)) {
        serviceWasHidden = true;
        return null;
      }
      return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      if (NUMBER_SYSTEM_SERVICE.equals(name)) {
        serviceWasHidden = true;
        return Collections.emptyEnumeration();
      }
      return super.getResources(name);
    }
  }
}
