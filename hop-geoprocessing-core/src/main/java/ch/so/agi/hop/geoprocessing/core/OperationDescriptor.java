package ch.so.agi.hop.geoprocessing.core;

import java.util.List;

public record OperationDescriptor(
    TransformFamily family,
    String id,
    String label,
    OperationGroup group,
    OperationArity arity,
    ExecutionMode executionMode,
    List<ParameterDescriptor> parameterSchema,
    ResultMode resultMode) {

  public boolean requires(ParameterId parameterId) {
    return parameterSchema.stream().anyMatch(parameter -> parameter.id() == parameterId);
  }
}
