package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.geoprocessing.core.FeatureRow;
import ch.so.agi.hop.geoprocessing.core.FieldNameSupport;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;

class SecondaryRowJoinBuilder {

  private record FieldMapping(int sourceIndex, int targetIndex) {}

  private final RowMeta outputRowMeta;
  private final List<FieldMapping> mappings;

  SecondaryRowJoinBuilder(IRowMeta primaryRowMeta, IRowMeta secondaryRowMeta, String fieldPrefix) {
    outputRowMeta = (RowMeta) primaryRowMeta.clone();
    mappings = new ArrayList<>();
    Set<String> existingNames = FieldNameSupport.toLowerCaseSet(RowMetaSupport.fieldNames(outputRowMeta));
    String effectivePrefix = fieldPrefix == null || fieldPrefix.isBlank() ? "b_" : fieldPrefix;

    if (secondaryRowMeta != null) {
      for (int index = 0; index < secondaryRowMeta.size(); index++) {
        IValueMeta sourceMeta = secondaryRowMeta.getValueMeta(index);
        String uniqueName = FieldNameSupport.uniqueName(effectivePrefix + sourceMeta.getName(), existingNames);
        IValueMeta targetMeta = (IValueMeta) sourceMeta.clone();
        targetMeta.setName(uniqueName);
        outputRowMeta.addValueMeta(targetMeta);
        mappings.add(new FieldMapping(index, outputRowMeta.size() - 1));
      }
    }
  }

  IRowMeta createOutputRowMeta() {
    return (IRowMeta) outputRowMeta.clone();
  }

  Object[] buildRow(Object[] primaryRow, FeatureRow secondaryFeature) {
    Object[] outputRow = RowDataUtil.allocateRowData(outputRowMeta.size());
    System.arraycopy(primaryRow, 0, outputRow, 0, primaryRow.length);
    if (secondaryFeature != null) {
      for (FieldMapping mapping : mappings) {
        outputRow[mapping.targetIndex()] = secondaryFeature.rowData()[mapping.sourceIndex()];
      }
    }
    return outputRow;
  }
}
