package ch.so.agi.hop.geoprocessing.core.converter;

import java.util.Objects;
import org.apache.hop.core.row.IValueMeta;

/** One configured target field and its stable position in the output row. */
public record OutputBinding(OutputRole role, String fieldName, int index, IValueMeta valueMeta) {
  public OutputBinding {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(valueMeta, "valueMeta");
    if (index < 0) {
      throw new IllegalArgumentException("Output binding index must not be negative");
    }
  }
}
