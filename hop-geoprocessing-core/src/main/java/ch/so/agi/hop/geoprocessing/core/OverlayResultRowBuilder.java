package ch.so.agi.hop.geoprocessing.core;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;

public class OverlayResultRowBuilder {

  private record FieldMapping(int sourceIndex, int targetIndex) {}

  private final RowMeta outputRowMeta;
  private final List<FieldMapping> secondaryMappings;
  private final int geometryFieldIndex;

  public OverlayResultRowBuilder(
      IRowMeta primaryRowMeta, IRowMeta secondaryRowMeta, String fieldPrefix, String outputFieldName) {
    outputRowMeta = (RowMeta) primaryRowMeta.clone();
    secondaryMappings = new ArrayList<>();
    Set<String> existingFieldNames = FieldNameSupport.toLowerCaseSet(RowMetaSupport.fieldNames(outputRowMeta));

    if (secondaryRowMeta != null) {
      String effectivePrefix = fieldPrefix == null ? "b_" : fieldPrefix;
      for (int index = 0; index < secondaryRowMeta.size(); index++) {
        IValueMeta sourceMeta = secondaryRowMeta.getValueMeta(index);
        String targetName = FieldNameSupport.uniqueName(effectivePrefix + sourceMeta.getName(), existingFieldNames);
        IValueMeta targetMeta = sourceMeta.clone();
        targetMeta.setName(targetName);
        outputRowMeta.addValueMeta(targetMeta);
        secondaryMappings.add(new FieldMapping(index, outputRowMeta.size() - 1));
      }
    }

    outputRowMeta.addValueMeta(new ValueMetaGeometry(outputFieldName));
    geometryFieldIndex = outputRowMeta.size() - 1;
  }

  public IRowMeta createOutputRowMeta() {
    return (IRowMeta) outputRowMeta.clone();
  }

  public int getGeometryFieldIndex() {
    return geometryFieldIndex;
  }

  public Object[] buildRow(Object[] primaryRow, FeatureRow secondaryFeature, org.locationtech.jts.geom.Geometry geometry) {
    Object[] outputRow = RowDataUtil.allocateRowData(outputRowMeta.size());
    System.arraycopy(primaryRow, 0, outputRow, 0, primaryRow.length);

    if (secondaryFeature != null) {
      for (FieldMapping mapping : secondaryMappings) {
        outputRow[mapping.targetIndex()] = secondaryFeature.rowData()[mapping.sourceIndex()];
      }
    }

    outputRow[geometryFieldIndex] = geometry;
    return outputRow;
  }
}
