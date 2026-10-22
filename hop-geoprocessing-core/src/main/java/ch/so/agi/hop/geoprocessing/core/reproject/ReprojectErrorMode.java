package ch.so.agi.hop.geoprocessing.core.reproject;

/** Policy for row data errors; configuration and integration errors are always fatal. */
public enum ReprojectErrorMode {
  FAIL,
  RETURN_NULL,
  ROUTE_TO_ERROR
}
