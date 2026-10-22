package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import ch.so.agi.hop.geoprocessing.core.reproject.*;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Per-copy state; no input rows or geometries are retained. */
public class GeometryReprojectData extends BaseTransformData {
  GeometryReprojectSettings settings;
  CompiledReprojectionPlan plan;
  GeoToolsReprojectionService service;
  GeometryReprojectRuntime runtime;
  IRowMeta outputRowMeta;
  boolean initialized;
  boolean summaryLogged;
  long inputRows;
  long normalRows;
  long errorRows;
  long nullOutputRows;
}
