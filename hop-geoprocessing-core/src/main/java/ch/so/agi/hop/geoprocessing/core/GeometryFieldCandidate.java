package ch.so.agi.hop.geoprocessing.core;

public record GeometryFieldCandidate(
    String fieldName, int index, boolean geometryValueMeta, boolean heuristic) {}
