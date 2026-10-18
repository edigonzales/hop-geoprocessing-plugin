package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import ch.so.agi.hop.geoprocessing.core.converter.CompiledConversionPlan;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterRuntime;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSettings;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecErrorCode;
import java.util.EnumMap;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

/** Per-copy converter state; never retains rows or geometries across calls. */
public class GeometryConverterData extends BaseTransformData implements ITransformData {
  GeometryConverterSettings settings;
  CompiledConversionPlan plan;
  GeometryConverterRuntime runtime;
  IRowMeta outputRowMeta;
  boolean initialized;
  boolean summaryLogged;
  long inputRows;
  long normalRows;
  long errorRows;
  long nullOutputRows;
  long sridOmittedRows;
  final EnumMap<GeometryCodecErrorCode, Long> failuresByCode =
      new EnumMap<>(GeometryCodecErrorCode.class);
}
