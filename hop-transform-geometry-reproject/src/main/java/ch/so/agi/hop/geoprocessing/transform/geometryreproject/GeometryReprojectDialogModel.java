package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import ch.so.agi.hop.geoprocessing.core.reproject.ReprojectConfigurationException;
import ch.so.agi.hop.geoprocessing.core.reproject.ReprojectSourceMode;
import ch.so.agi.hop.geoprocessing.core.reproject.GeometryReprojectSchema;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;

/** Independent dialog draft. Cancel discards it without changing the persisted metadata. */
final class GeometryReprojectDialogModel {
  private final GeometryReprojectMeta draft;
  private IRowMeta inputSchema;
  private String inspectionError;

  private GeometryReprojectDialogModel(GeometryReprojectMeta source) {
    draft = (GeometryReprojectMeta) source.clone();
  }
  static GeometryReprojectDialogModel from(GeometryReprojectMeta source) {
    return new GeometryReprojectDialogModel(source);
  }
  GeometryReprojectMeta draft() { return draft; }
  boolean sourceEpsgEnabled() { return draft.getSourceMode() == ReprojectSourceMode.EXPLICIT_EPSG; }
  void inspectInput(IRowMeta schema) {
    inputSchema = schema == null ? null : schema.clone();
    inspectionError = schema == null ? "No upstream fields are available for schema validation" : null;
  }
  void inspectionFailed(String message) { inspectionError = "Unable to inspect upstream fields: " + message; }
  String validate(IVariables variables) {
    if (inspectionError != null) { return inspectionError; }
    try {
      var settings = draft.toSettings(variables);
      if (inputSchema != null) { GeometryReprojectSchema.compile(inputSchema,settings,"Geometry Reproject"); }
      return null;
    } catch (ReprojectConfigurationException e) { return e.getMessage(); }
  }
  boolean hasDeferredVariables(IVariables variables) {
    if (inspectionError != null) { return false; }
    try { draft.toSettings(variables); return false; }
    catch (GeometryReprojectMeta.DeferredVariableException e) { return true; }
    catch (ReprojectConfigurationException e) { return false; }
  }
  void commitTo(GeometryReprojectMeta target) { target.copyFrom(draft); }
}
