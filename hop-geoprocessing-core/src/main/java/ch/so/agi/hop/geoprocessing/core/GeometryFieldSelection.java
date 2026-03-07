package ch.so.agi.hop.geoprocessing.core;

import java.util.List;

public record GeometryFieldSelection(List<String> fieldNames, String selectedField, String warning) {

  public GeometryFieldSelection {
    fieldNames = fieldNames == null ? List.of() : List.copyOf(fieldNames);
    selectedField = selectedField == null ? "" : selectedField;
    warning = warning == null ? "" : warning;
  }

  public boolean hasSelection() {
    return !selectedField.isBlank();
  }
}
