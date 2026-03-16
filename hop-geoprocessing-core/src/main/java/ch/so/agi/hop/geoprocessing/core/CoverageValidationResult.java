package ch.so.agi.hop.geoprocessing.core;

import org.locationtech.jts.geom.Geometry;

public record CoverageValidationResult(boolean valid, Geometry errorGeometry) {}
