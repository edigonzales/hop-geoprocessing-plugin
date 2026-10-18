package ch.so.agi.hop.geoprocessing.core.converter;

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
    ConverterErrorMode errorMode) {}
