package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

public class LayerAggregateExecutor {

  public Geometry execute(String operationId, List<Geometry> geometries) throws HopException {
    List<Geometry> nonEmptyGeometries =
        geometries.stream().map(GeometryFieldValueHelper::normalize).filter(geometry -> geometry != null).toList();
    if (nonEmptyGeometries.isEmpty()) {
      return null;
    }

    Geometry firstGeometry = nonEmptyGeometries.get(0);
    Geometry result =
        switch (operationId) {
          case "collect" -> firstGeometry.getFactory().buildGeometry(new ArrayList<>(nonEmptyGeometries));
          case "dissolve", "unary_union" -> UnaryUnionOp.union(nonEmptyGeometries);
          default -> throw new HopException("Unsupported layer aggregate operation: " + operationId);
        };
    return GeometryFieldValueHelper.preserveSrid(firstGeometry, result);
  }
}
