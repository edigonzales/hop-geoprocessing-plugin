package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.List;

public final class TextListSupport {

  private TextListSupport() {}

  public static List<String> splitCsvOrSemicolon(String value) {
    List<String> values = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return values;
    }
    for (String part : value.split("[,;]")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        values.add(trimmed);
      }
    }
    return values;
  }
}
