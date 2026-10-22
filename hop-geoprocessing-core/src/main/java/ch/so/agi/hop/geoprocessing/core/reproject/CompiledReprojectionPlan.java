package ch.so.agi.hop.geoprocessing.core.reproject;

import org.apache.hop.core.row.IRowMeta;

/** Deterministic input/output bindings shared by metadata and row processing. */
public record CompiledReprojectionPlan(
    IRowMeta inputRowMeta,
    IRowMeta outputRowMeta,
    GeometryReprojectSettings settings,
    int geometryIndex,
    int outputIndex,
    Integer sourceEpsg,
    int targetEpsg) {
  public CompiledReprojectionPlan {
    inputRowMeta = inputRowMeta.clone();
    outputRowMeta = outputRowMeta.clone();
  }
}
