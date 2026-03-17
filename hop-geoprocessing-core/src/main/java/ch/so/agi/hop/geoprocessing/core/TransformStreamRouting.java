package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransform;

public final class TransformStreamRouting {

  private TransformStreamRouting() {}

  public static RowSetTargets partitionRowSets(List<IRowSet> outputRowSets, String targetTransformName) {
    if (targetTransformName == null || targetTransformName.isBlank()) {
      return new RowSetTargets(outputRowSets, List.of());
    }

    List<IRowSet> mainRowSets = new ArrayList<>();
    List<IRowSet> targetRowSets = new ArrayList<>();
    for (IRowSet rowSet : outputRowSets) {
      if (targetTransformName.equalsIgnoreCase(rowSet.getDestinationTransformName())) {
        targetRowSets.add(rowSet);
      } else {
        mainRowSets.add(rowSet);
      }
    }
    return new RowSetTargets(mainRowSets, targetRowSets);
  }

  public static void putRowToRowSets(
      BaseTransform<?, ?> transform, IRowMeta rowMeta, Object[] row, List<IRowSet> rowSets)
      throws HopTransformException {
    if (rowSets == null || rowSets.isEmpty()) {
      return;
    }

    for (int index = 0; index < rowSets.size(); index++) {
      IRowSet rowSet = rowSets.get(index);
      Object[] rowToSend = row;
      if (index < rowSets.size() - 1) {
        try {
          rowToSend = rowMeta.cloneRow(row);
        } catch (HopValueException e) {
          throw new HopTransformException(
              "Unable to clone row while copying rows to multiple target transforms", e);
        }
      }
      transform.putRowTo(rowMeta, rowToSend, rowSet);
    }
  }

  public record RowSetTargets(List<IRowSet> mainRowSets, List<IRowSet> targetRowSets) {
    public RowSetTargets {
      mainRowSets = List.copyOf(mainRowSets);
      targetRowSets = List.copyOf(targetRowSets);
    }
  }
}
