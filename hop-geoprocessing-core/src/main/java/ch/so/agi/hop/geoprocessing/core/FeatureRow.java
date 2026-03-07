package ch.so.agi.hop.geoprocessing.core;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

public record FeatureRow(Object[] rowData, Geometry geometry, Envelope envelope, Integer srid, long sourceId) {

  public static FeatureRow fromRow(
      IRowMeta rowMeta, int geometryFieldIndex, Object[] rowData, long sourceId) throws HopException {
    Geometry geometry = GeometryFieldValueHelper.readGeometry(rowMeta, geometryFieldIndex, rowData);
    return new FeatureRow(
        rowData.clone(),
        geometry,
        geometry == null ? new Envelope() : geometry.getEnvelopeInternal(),
        GeometryFieldValueHelper.sridOf(geometry),
        sourceId);
  }
}
