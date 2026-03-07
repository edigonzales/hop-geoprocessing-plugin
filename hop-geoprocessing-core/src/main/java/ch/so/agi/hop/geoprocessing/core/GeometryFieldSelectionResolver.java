package ch.so.agi.hop.geoprocessing.core;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;

public class GeometryFieldSelectionResolver {

  private final GeometryFieldDetector detector;

  public GeometryFieldSelectionResolver() {
    this(new GeometryFieldDetector());
  }

  GeometryFieldSelectionResolver(GeometryFieldDetector detector) {
    this.detector = detector;
  }

  public GeometryFieldSelection resolve(IRowMeta rowMeta, String preferredField) {
    List<GeometryFieldCandidate> candidates = detector.detectCandidates(rowMeta);
    List<String> fieldNames =
        candidates.stream().map(GeometryFieldCandidate::fieldName).collect(Collectors.toList());

    if (fieldNames.isEmpty()) {
      return new GeometryFieldSelection(List.of(), "", "");
    }

    if (preferredField != null && fieldNames.contains(preferredField)) {
      return new GeometryFieldSelection(fieldNames, preferredField, "");
    }

    String fallbackField = detector.chooseDefaultField(candidates);
    if (preferredField != null && !preferredField.isBlank() && fallbackField != null) {
      return new GeometryFieldSelection(
          fieldNames,
          fallbackField,
          "Geometry field auto-adjusted to '"
              + fallbackField
              + "' because '"
              + preferredField
              + "' is not available.");
    }

    return new GeometryFieldSelection(fieldNames, fallbackField, "");
  }

  public int requireFieldIndex(IRowMeta rowMeta, String preferredField, String fieldLabel)
      throws HopTransformException {
    GeometryFieldSelection selection = resolve(rowMeta, preferredField);
    if (!selection.hasSelection()) {
      throw new HopTransformException(fieldLabel + " could not be resolved from the available input fields");
    }

    int fieldIndex = rowMeta.indexOfValue(selection.selectedField());
    if (fieldIndex < 0) {
      throw new HopTransformException(
          fieldLabel + " was resolved to '" + selection.selectedField() + "' but is not present in the input row");
    }
    return fieldIndex;
  }
}
