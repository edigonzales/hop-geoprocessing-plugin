package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public final class TransformStreamRouting {

  private TransformStreamRouting() {}

  public static List<TransformMeta> infoSources(PipelineMeta pipeline, TransformMeta target) {
    return incomingHops(pipeline, target).stream()
        .filter(hop -> !hop.isErrorHop())
        .map(PipelineHopMeta::getFromTransform)
        .filter(source -> pipeline.findTransform(source.getName()) == source
            && !source.isSendingErrorRowsToTransform(target))
        .distinct()
        .toList();
  }

  public static IRowMeta primaryFields(
      PipelineMeta pipeline, IVariables variables, TransformMeta target, TransformMeta secondary)
      throws HopTransformException {
    IRowMeta primaryFields = new RowMeta();
    for (PipelineHopMeta hop : incomingHops(pipeline, target)) {
      TransformMeta primary = hop.getFromTransform();
      if (primary == secondary) {
        continue;
      }
      IRowMeta fields = pipeline.getTransformFields(variables, primary, target, null);
      for (IValueMeta field : fields.getValueMetaList()) {
        if (primaryFields.searchValueMeta(field.getName()) == null) {
          primaryFields.addValueMeta(field);
        }
      }
    }
    return primaryFields;
  }

  private static List<PipelineHopMeta> incomingHops(PipelineMeta pipeline, TransformMeta target) {
    return pipeline.getPipelineHops().stream()
        .filter(hop -> target != null && hop.getToTransform() == target && hop.isEnabled())
        .filter(hop -> hop.getFromTransform() != null)
        .toList();
  }

  public static List<IRowSet> primaryInputs(List<IRowSet> inputs, String infoTransformName)
      throws HopTransformException {
    List<IRowSet> primary = inputs.stream()
        .filter(rowSet -> !infoTransformName.equalsIgnoreCase(rowSet.getOriginTransformName()))
        .toList();
    List<String> origins = primary.stream()
        .map(IRowSet::getOriginTransformName)
        .distinct()
        .toList();
    if (origins.size() != 1) {
      throw new HopTransformException(
          "Expected exactly one primary input transform, found " + origins.size() + ": " + origins);
    }
    if (inputs.size() - primary.size() != 1) {
      throw new HopTransformException(
          "Expected exactly one secondary INFO row set from " + infoTransformName
              + ", found " + (inputs.size() - primary.size()));
    }
    return primary;
  }

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
