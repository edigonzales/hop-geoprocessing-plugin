package ch.so.agi.hop.geoprocessing.core.reproject;

/** Immutable, variable-resolved settings. Inactive source EPSG expressions are ignored. */
public record GeometryReprojectSettings(
    String geometryField,
    ReprojectSourceMode sourceMode,
    String sourceEpsg,
    String targetEpsg,
    String outputField,
    boolean overwriteExistingField,
    ReprojectErrorMode errorMode) {}
