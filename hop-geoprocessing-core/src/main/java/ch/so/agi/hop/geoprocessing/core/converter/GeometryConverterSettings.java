package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;

/** Immutable, variable-resolved settings shared by metadata, schema and runtime. */
public record GeometryConverterSettings(
    int configurationVersion,
    ConverterInputFormat inputFormat,
    String inputFieldName,
    String inputXFieldName,
    String inputYFieldName,
    String inputZFieldName,
    String inputMFieldName,
    ConverterOutputFormat outputFormat,
    String outputFieldName,
    String outputXFieldName,
    String outputYFieldName,
    String outputZFieldName,
    String outputMFieldName,
    GeometryByteOrder byteOrder,
    ConverterSridMode sridMode,
    Integer srid,
    boolean overwriteExistingFields,
    ConverterErrorMode errorMode,
    GeoJsonProfile geoJsonInputProfile,
    GeoJsonProfile geoJsonOutputProfile,
    String geoJsonInputContextField,
    String geoJsonOutputContextField,
    boolean geoJsonDropZ,
    boolean geoJsonDropM,
    boolean geoJsonEmptyToNull) {
  /** Preserves the original constructor and strict defaults for existing callers. */
  public GeometryConverterSettings(
      int configurationVersion,
      ConverterInputFormat inputFormat,
      String inputFieldName,
      String inputXFieldName,
      String inputYFieldName,
      String inputZFieldName,
      String inputMFieldName,
      ConverterOutputFormat outputFormat,
      String outputFieldName,
      String outputXFieldName,
      String outputYFieldName,
      String outputZFieldName,
      String outputMFieldName,
      GeometryByteOrder byteOrder,
      ConverterSridMode sridMode,
      Integer srid,
      boolean overwriteExistingFields,
      ConverterErrorMode errorMode) {
    this(
        configurationVersion,
        inputFormat,
        inputFieldName,
        inputXFieldName,
        inputYFieldName,
        inputZFieldName,
        inputMFieldName,
        outputFormat,
        outputFieldName,
        outputXFieldName,
        outputYFieldName,
        outputZFieldName,
        outputMFieldName,
        byteOrder,
        sridMode,
        srid,
        overwriteExistingFields,
        errorMode,
        GeoJsonProfile.AUTO,
        GeoJsonProfile.RFC7946,
        null,
        null,
        false,
        false,
        false);
  }

  public boolean usesGeoJson() {
    return inputFormat == ConverterInputFormat.GEOJSON
        || outputFormat == ConverterOutputFormat.GEOJSON;
  }
}
