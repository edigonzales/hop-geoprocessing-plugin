package ch.so.agi.hop.geoprocessing.core;

import java.util.List;

public record OperationDescriptor(
    TransformFamily family,
    String id,
    String label,
    OperationGroup group,
    OperationArity arity,
    ExecutionMode executionMode,
    List<ParameterId> parameters) {

  public boolean requires(ParameterId parameterId) {
    return parameters.contains(parameterId);
  }

  public String displayLabel() {
    return switch (family) {
      case GEOMETRY_OPERATION -> label + " (" + group.getLabel() + ")";
      default -> label;
    };
  }
}
