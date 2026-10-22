package ch.so.agi.hop.geoprocessing.core.reproject;

import java.util.Arrays;
import java.util.Objects;
import org.locationtech.jts.geom.Geometry;

/** Native Geometry row engine, independent of Hop transform lifecycle and SWT. */
public final class GeometryReprojectRuntime {
  private final CompiledReprojectionPlan plan;
  private final GeoToolsReprojectionService service;

  public GeometryReprojectRuntime(CompiledReprojectionPlan plan, GeoToolsReprojectionService service) {
    this.plan = Objects.requireNonNull(plan, "plan");
    this.service = Objects.requireNonNull(service, "service");
  }

  public Object[] reproject(Object[] input)
      throws ReprojectDataException, ReprojectIntegrationException {
    requireRow(input);
    Object value = input[plan.geometryIndex()];
    if (value != null && !(value instanceof Geometry)) {
      throw new ReprojectIntegrationException("Field '" + plan.settings().geometryField()
          + "' does not contain the shared native Geometry class: " + value.getClass().getName());
    }
    try {
      Geometry output = service.reproject((Geometry) value, plan.sourceEpsg(), plan.targetEpsg());
      Object[] row = Arrays.copyOf(input, plan.outputRowMeta().size());
      row[plan.outputIndex()] = output;
      return row;
    } catch (ReprojectDataException e) {
      throw new ReprojectDataException(e.code(), plan.settings().geometryField(), e.getMessage(), e);
    }
  }

  public Object[] nullOutput(Object[] input) throws ReprojectIntegrationException {
    requireRow(input);
    Object[] row = Arrays.copyOf(input, plan.outputRowMeta().size());
    row[plan.outputIndex()] = null;
    return row;
  }

  private void requireRow(Object[] input) throws ReprojectIntegrationException {
    if (input == null || input.length < plan.inputRowMeta().size()) {
      throw new ReprojectIntegrationException("Input row does not match the compiled Geometry Reproject schema");
    }
  }
}
