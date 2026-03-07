package ch.so.agi.hop.geoprocessing.core;

public record ParameterDescriptor(
    ParameterId id, String label, ParameterType type, boolean required) {}
