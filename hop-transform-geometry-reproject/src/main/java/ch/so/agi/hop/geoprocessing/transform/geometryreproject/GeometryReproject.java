package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import ch.so.agi.hop.geoprocessing.core.reproject.*;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Streaming native-geometry reprojection, one output row per successful input row. */
public class GeometryReproject extends BaseTransform<GeometryReprojectMeta, GeometryReprojectData> {
  public GeometryReproject(TransformMeta transformMeta, GeometryReprojectMeta meta,
      GeometryReprojectData data, int copyNr, PipelineMeta pipelineMeta, Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean init() {
    if (!super.init()) { return false; }
    try {
      data.settings = meta.toSettings(this);
      if (data.settings.errorMode() == ReprojectErrorMode.ROUTE_TO_ERROR
          && !GeometryReprojectMeta.hasUsableErrorHop(getTransformMeta())) {
        throw new ReprojectConfigurationException("ROUTE_TO_ERROR requires an enabled Hop error connection with a target transform");
      }
      data.service = new GeoToolsReprojectionService(this::logBasic);
      data.service.validateConfiguration(data.settings);
      data.initialized = false;
      data.summaryLogged = false;
      data.inputRows = data.normalRows = data.errorRows = data.nullOutputRows = 0;
      return true;
    } catch (ReprojectConfigurationException | ReprojectIntegrationException e) {
      logError("Unable to initialize Geometry Reproject: " + e.getMessage(), e);
      return false;
    }
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      logReprojectionSummary();
      setOutputDone();
      return false;
    }
    if (!data.initialized) {
      try {
        data.plan = GeometryReprojectSchema.compile(getInputRowMeta(), data.settings, getTransformName());
        data.outputRowMeta = data.plan.outputRowMeta();
        data.runtime = new GeometryReprojectRuntime(data.plan, data.service);
        data.initialized = true;
      } catch (ReprojectConfigurationException e) {
        throw new HopException("Geometry Reproject schema compilation failed", e);
      }
    }
    data.inputRows++;
    try {
      Object[] output = data.runtime.reproject(row);
      putRow(data.outputRowMeta, output);
      data.normalRows++;
      if (output[data.plan.outputIndex()] == null) { data.nullOutputRows++; }
    } catch (ReprojectDataException e) {
      handleDataError(row, e);
    } catch (ReprojectIntegrationException e) {
      throw new HopException("Geometry Reproject integration failure", e);
    }
    return true;
  }

  private void handleDataError(Object[] row, ReprojectDataException error) throws HopException {
    data.errorRows++;
    String field = data.settings.geometryField();
    String description = "Geometry reprojection failed for field '" + field + "' ("
        + error.code() + "): " + error.getMessage();
    switch (data.settings.errorMode()) {
      case FAIL -> throw new HopException(description, error);
      case RETURN_NULL -> {
        putRow(data.outputRowMeta, data.runtime.nullOutput(row));
        data.normalRows++;
        data.nullOutputRows++;
      }
      case ROUTE_TO_ERROR -> putError(getInputRowMeta(), row, 1, description, field, error.code().toString());
    }
  }

  private void logReprojectionSummary() {
    if (!data.summaryLogged) {
      data.summaryLogged = true;
      logBasic("Geometry Reproject summary: input=" + data.inputRows + ", normal="
          + data.normalRows + ", errors=" + data.errorRows + ", nullOutputs=" + data.nullOutputRows);
    }
  }

  @Override
  public void dispose() {
    logReprojectionSummary();
    data.settings = null;
    data.plan = null;
    data.service = null;
    data.runtime = null;
    data.outputRowMeta = null;
    super.dispose();
  }
}
