package ch.so.agi.hop.geoprocessing.core.converter;

/** Result of one converted input row. */
public record ConversionResult(
    Object[] row, boolean allTargetValuesNull, boolean sridOmitted) {}
