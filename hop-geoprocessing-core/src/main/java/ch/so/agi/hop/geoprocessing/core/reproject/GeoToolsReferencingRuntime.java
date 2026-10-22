package ch.so.agi.hop.geoprocessing.core.reproject;

import java.util.ServiceConfigurationError;
import tech.units.indriya.function.Calculus;
import tech.units.indriya.function.DefaultNumberSystem;

/** Referencing-only bootstrap for Hop's grouped plugin classloader. */
final class GeoToolsReferencingRuntime {
  private static volatile boolean initialized;

  private GeoToolsReferencingRuntime() {}

  static <T> T scoped(ReferencingCall<T> call)
      throws ReprojectDataException, ReprojectIntegrationException {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(GeoToolsReferencingRuntime.class.getClassLoader());
      initialize();
      return call.run();
    } catch (LinkageError | ServiceConfigurationError | RuntimeException e) {
      throw new ReprojectIntegrationException("Unable to initialize or use the shared GeoTools referencing runtime", e);
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static synchronized void initialize() {
    if (initialized) {
      return;
    }
    try {
      Calculus.currentNumberSystem();
    } catch (IllegalArgumentException | ServiceConfigurationError e) {
      // Indriya searches with its defining loader, so changing TCCL alone is insufficient.
      Calculus.setCurrentNumberSystem(new DefaultNumberSystem());
    }
    initialized = true;
  }

  @FunctionalInterface
  interface ReferencingCall<T> {
    T run() throws ReprojectDataException, ReprojectIntegrationException;
  }
}
