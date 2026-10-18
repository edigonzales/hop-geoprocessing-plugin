package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import ch.so.agi.hop.geoprocessing.core.converter.CompiledConversionPlan;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterConfigurationException;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterIntegrationException;
import ch.so.agi.hop.geoprocessing.core.converter.ConversionResult;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterRuntime;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSchema;
import com.atolcd.hop.gis.geometry.codec.GeometryCodec;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecErrorCode;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import com.atolcd.hop.gis.geometry.codec.GeometryReadLimits;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformErrorMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Row-wise Geometry Converter transform. */
public class GeometryConverter
    extends BaseTransform<GeometryConverterMeta, GeometryConverterData> {

  public GeometryConverter(
      TransformMeta transformMeta,
      GeometryConverterMeta meta,
      GeometryConverterData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean init() {
    if (!super.init()) {
      return false;
    }
    try {
      data.settings = meta.toSettings(this);
    } catch (ConverterConfigurationException e) {
      logError("Invalid Geometry Converter configuration: " + e.getMessage());
      return false;
    }
    try {
      validateErrorHandlingConfiguration();
    } catch (HopException e) {
      logError(e.getMessage());
      return false;
    }
    data.failuresByCode.clear();
    data.initialized = false;
    data.summaryLogged = false;
    return true;
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      logConverterSummary();
      setOutputDone();
      return false;
    }
    if (!data.initialized) {
      initializeFromInputRowMeta();
    }
    data.inputRows++;
    ConversionResult result;
    try {
      result = data.runtime.convert(row);
    } catch (GeometryCodecException e) {
      handleDataError(row, e);
      return true;
    } catch (ConverterIntegrationException e) {
      throw new HopException("Geometry Converter integration failure", e);
    }
    putRow(data.outputRowMeta, result.row());
    data.normalRows++;
    if (result.allTargetValuesNull()) {
      data.nullOutputRows++;
    }
    if (result.sridOmitted()) {
      data.sridOmittedRows++;
    }
    return true;
  }

  private void initializeFromInputRowMeta() throws HopException {
    IRowMeta inputRowMeta = getInputRowMeta();
    if (inputRowMeta == null) {
      throw new HopException("Geometry Converter input row metadata is unavailable");
    }
    try {
      data.plan =
          GeometryConverterSchema.compile(
              inputRowMeta, data.settings, getTransformName());
    } catch (ConverterConfigurationException e) {
      throw new HopException("Geometry Converter schema compilation failed", e);
    }
    data.outputRowMeta = data.plan.outputRowMeta();
    data.runtime =
        new GeometryConverterRuntime(
            data.plan, new GeometryCodec(GeometryReadLimits.defaults()));
    data.initialized = true;
  }

  private void validateErrorHandlingConfiguration() throws HopException {
    TransformMeta transformMeta = getTransformMeta();
    TransformErrorMeta errorMeta =
        transformMeta == null ? null : transformMeta.getTransformErrorMeta();
    if (errorMeta == null || !errorMeta.isEnabled() || errorMeta.getTargetTransform() == null) {
      throw new HopException(
          "Geometry Converter requires an enabled Hop error connection with a target transform"
              + " (error mode " + data.settings.errorMode() + ")");
    }
  }

  private void handleDataError(Object[] original, GeometryCodecException error)
      throws HopException {
    data.errorRows++;
    data.failuresByCode.merge(error.code(), 1L, Long::sum);
    String fieldName = fieldNameForError();
    String description =
        "Geometry conversion failed for field '"
            + fieldName
            + "' ("
            + error.code()
            + "): "
            + error.getMessage();
    switch (data.settings.errorMode()) {
      case FAIL -> throw new HopException(description, error);
      case RETURN_NULL -> {
        Object[] nullRow = data.runtime.nullOutputs(original);
        putRow(data.outputRowMeta, nullRow);
        data.normalRows++;
        data.nullOutputRows++;
      }
      case ROUTE_TO_ERROR -> {
        try {
          putError(
              getInputRowMeta(),
              original,
              1,
              description,
              fieldName,
              error.code().name());
        } catch (HopTransformException e) {
          throw new HopException("Unable to route Geometry Converter error row", e);
        }
      }
    }
  }

  private String fieldNameForError() {
    if (data.settings.inputFormat()
        == ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat.COORDINATES) {
      if (data.plan != null && data.plan.inputXIndex() >= 0) {
        return data.plan.inputRowMeta().getValueMeta(data.plan.inputXIndex()).getName();
      }
      return "coordinates";
    }
    if (data.plan != null && data.plan.singleInputIndex() >= 0) {
      return data.plan.inputRowMeta().getValueMeta(data.plan.singleInputIndex()).getName();
    }
    return data.settings.inputFieldName() == null ? "geometry" : data.settings.inputFieldName();
  }

  private void logConverterSummary() {
    if (data.summaryLogged) {
      return;
    }
    data.summaryLogged = true;
    StringBuilder summary = new StringBuilder("Geometry Converter summary: input=")
        .append(data.inputRows)
        .append(", normal=")
        .append(data.normalRows)
        .append(", errors=")
        .append(data.errorRows)
        .append(", nullOutputs=")
        .append(data.nullOutputRows)
        .append(", sridOmitted=")
        .append(data.sridOmittedRows);
    if (!data.failuresByCode.isEmpty()) {
      summary.append(", failures=");
      boolean first = true;
      for (Map.Entry<GeometryCodecErrorCode, Long> entry : data.failuresByCode.entrySet()) {
        if (!first) {
          summary.append(',');
        }
        summary.append(entry.getKey()).append(':').append(entry.getValue());
        first = false;
      }
    }
    logBasic(summary.toString());
  }

  @Override
  public void dispose() {
    logConverterSummary();
    data.settings = null;
    data.plan = null;
    data.runtime = null;
    data.outputRowMeta = null;
    super.dispose();
  }
}
