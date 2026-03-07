package ch.so.agi.hop.geoprocessing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;

public final class RowMetaSupport {

  private static final GeometryFieldDetector GEOMETRY_FIELD_DETECTOR = new GeometryFieldDetector();
  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver(GEOMETRY_FIELD_DETECTOR);

  private RowMetaSupport() {}

  public static List<String> geometryFieldNames(IRowMeta rowMeta) {
    if (rowMeta == null) {
      return Collections.emptyList();
    }
    return GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(rowMeta, "").fieldNames();
  }

  public static List<String> numericFieldNames(IRowMeta rowMeta) {
    if (rowMeta == null) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta.isNumber()) {
        names.add(valueMeta.getName());
      }
    }
    return names;
  }

  public static List<String> fieldNames(IRowMeta rowMeta) {
    if (rowMeta == null) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      names.add(valueMeta.getName());
    }
    return names;
  }

  public static boolean isGeometryField(IValueMeta valueMeta) {
    return GEOMETRY_FIELD_DETECTOR.isGeometryValueMeta(valueMeta);
  }

  public static List<GeometryFieldCandidate> geometryFieldCandidates(IRowMeta rowMeta) {
    return GEOMETRY_FIELD_DETECTOR.detectCandidates(rowMeta);
  }

  public static GeometryFieldSelection resolveGeometryField(IRowMeta rowMeta, String preferredField) {
    return GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(rowMeta, preferredField);
  }
}
