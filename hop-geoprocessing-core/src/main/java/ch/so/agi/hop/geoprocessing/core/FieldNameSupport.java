package ch.so.agi.hop.geoprocessing.core;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class FieldNameSupport {

  private FieldNameSupport() {}

  public static String uniqueName(String candidate, Set<String> existingLowerCase) {
    String base = candidate == null || candidate.isBlank() ? "field" : candidate.trim();
    String unique = base;
    int counter = 2;
    while (existingLowerCase.contains(unique.toLowerCase(Locale.ROOT))) {
      unique = base + "_" + counter++;
    }
    existingLowerCase.add(unique.toLowerCase(Locale.ROOT));
    return unique;
  }

  public static Set<String> toLowerCaseSet(Iterable<String> fieldNames) {
    Set<String> values = new LinkedHashSet<>();
    for (String fieldName : fieldNames) {
      values.add(fieldName.toLowerCase(Locale.ROOT));
    }
    return values;
  }
}
