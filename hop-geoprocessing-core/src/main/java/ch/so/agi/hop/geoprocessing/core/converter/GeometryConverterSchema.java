package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.CoordinateLayout;
import com.atolcd.hop.gis.geometry.codec.WkbWriteOptions;
import com.atolcd.hop.gis.geometry.codec.WktWriteOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;

/** Builds the single deterministic output schema and conversion plan. */
public final class GeometryConverterSchema {
  private GeometryConverterSchema() {}

  public static CompiledConversionPlan compile(
      IRowMeta input, GeometryConverterSettings settings, String origin)
      throws ConverterConfigurationException {
    GeometryConverterValidator.requireValid(settings, input);

    IRowMeta inputRowMeta = input.clone();
    IRowMeta outputRowMeta = input.clone();

    int singleInputIndex = -1;
    int inputXIndex = -1;
    int inputYIndex = -1;
    int inputZIndex = -1;
    int inputMIndex = -1;
    CoordinateLayout coordinateLayout = null;
    if (settings.inputFormat() == ConverterInputFormat.COORDINATES) {
      inputXIndex = requireIndex(inputRowMeta, settings.inputXFieldName(), "input_x_field");
      inputYIndex = requireIndex(inputRowMeta, settings.inputYFieldName(), "input_y_field");
      inputZIndex =
          settings.inputZFieldName() == null || settings.inputZFieldName().isBlank()
              ? -1
              : requireIndex(inputRowMeta, settings.inputZFieldName(), "input_z_field");
      inputMIndex =
          settings.inputMFieldName() == null || settings.inputMFieldName().isBlank()
              ? -1
              : requireIndex(inputRowMeta, settings.inputMFieldName(), "input_m_field");
      coordinateLayout = CoordinateLayout.of(inputZIndex >= 0, inputMIndex >= 0);
    } else {
      singleInputIndex =
          requireIndex(inputRowMeta, settings.inputFieldName(), "input_field");
    }

    List<OutputBinding> bindings = new ArrayList<>();
    Set<String> targetKeys = new java.util.LinkedHashSet<>();
    if (settings.outputFormat() == ConverterOutputFormat.COORDINATES) {
      addCoordinateBinding(
          outputRowMeta, bindings, targetKeys, OutputRole.X, settings.outputXFieldName(), origin);
      addCoordinateBinding(
          outputRowMeta, bindings, targetKeys, OutputRole.Y, settings.outputYFieldName(), origin);
      addCoordinateBinding(
          outputRowMeta, bindings, targetKeys, OutputRole.Z, settings.outputZFieldName(), origin);
      addCoordinateBinding(
          outputRowMeta, bindings, targetKeys, OutputRole.M, settings.outputMFieldName(), origin);
    } else {
      OutputRole role =
          settings.outputFormat() == ConverterOutputFormat.GEOMETRY
              ? OutputRole.GEOMETRY
              : OutputRole.ENCODED;
      addBinding(
          outputRowMeta,
          bindings,
          targetKeys,
          role,
          settings.outputFieldName(),
          createValueMeta(settings.outputFormat(), settings.outputFieldName(), origin));
    }

    WktWriteOptions wktOptions =
        switch (settings.outputFormat()) {
          case WKT -> WktWriteOptions.wkt();
          case EWKT -> WktWriteOptions.ewkt();
          default -> null;
        };
    WkbWriteOptions wkbOptions =
        switch (settings.outputFormat()) {
          case WKB_BYTES, WKB_HEX ->
              WkbWriteOptions.iso(settings.byteOrder());
          case EWKB_BYTES, EWKB_HEX -> WkbWriteOptions.ewkb(settings.byteOrder());
          default -> null;
        };

    return new CompiledConversionPlan(
        inputRowMeta,
        outputRowMeta,
        settings,
        singleInputIndex,
        inputXIndex,
        inputYIndex,
        inputZIndex,
        inputMIndex,
        coordinateLayout,
        bindings,
        wktOptions,
        wkbOptions);
  }

  private static void addCoordinateBinding(
      IRowMeta outputRowMeta,
      List<OutputBinding> bindings,
      Set<String> targetKeys,
      OutputRole role,
      String fieldName,
      String origin)
      throws ConverterConfigurationException {
    if (fieldName == null || fieldName.isBlank()) {
      return;
    }
    addBinding(
        outputRowMeta,
        bindings,
        targetKeys,
        role,
        fieldName,
        createValueMeta(ConverterOutputFormat.COORDINATES, fieldName, origin));
  }

  private static void addBinding(
      IRowMeta outputRowMeta,
      List<OutputBinding> bindings,
      Set<String> targetKeys,
      OutputRole role,
      String fieldName,
      IValueMeta valueMeta)
      throws ConverterConfigurationException {
    String key = fieldName.toLowerCase(Locale.ROOT);
    if (!targetKeys.add(key)) {
      throw new ConverterConfigurationException(
          List.of(
              ConfigurationIssue.error(
                  ConfigurationIssue.GC_DUPLICATE_OUTPUT_FIELD,
                  "output_field",
                  fieldName,
                  "The target field name is assigned more than once: " + fieldName)));
    }
    int existing = outputRowMeta.indexOfValue(fieldName);
    int index;
    if (existing >= 0) {
      index = existing;
      outputRowMeta.setValueMeta(index, valueMeta);
    } else {
      index = outputRowMeta.size();
      outputRowMeta.addValueMeta(valueMeta);
    }
    bindings.add(new OutputBinding(role, fieldName, index, valueMeta));
  }

  private static IValueMeta createValueMeta(
      ConverterOutputFormat format, String fieldName, String origin) {
    IValueMeta valueMeta =
        switch (format) {
          case GEOMETRY -> new ValueMetaGeometry(fieldName);
          case WKT, EWKT, WKB_HEX, EWKB_HEX -> new ValueMetaString(fieldName);
          case WKB_BYTES, EWKB_BYTES -> new ValueMetaBinary(fieldName);
          case COORDINATES -> new ValueMetaNumber(fieldName);
        };
    valueMeta.setOrigin(origin == null ? "" : origin);
    valueMeta.setStorageType(IValueMeta.STORAGE_TYPE_NORMAL);
    return valueMeta;
  }

  private static int requireIndex(IRowMeta rowMeta, String fieldName, String property)
      throws ConverterConfigurationException {
    int index = fieldName == null ? -1 : rowMeta.indexOfValue(fieldName);
    if (index < 0) {
      throw new ConverterConfigurationException(
          List.of(
              ConfigurationIssue.error(
                  ConfigurationIssue.GC_MISSING_INPUT_FIELD,
                  property,
                  fieldName,
                  "Configured input field was not found: " + fieldName)));
    }
    return index;
  }
}
