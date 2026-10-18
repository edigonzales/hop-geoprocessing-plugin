package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.gis.geometry.codec.CoordinateLayout;
import com.atolcd.hop.gis.geometry.codec.WkbWriteOptions;
import com.atolcd.hop.gis.geometry.codec.WktWriteOptions;
import java.util.List;
import java.util.Objects;
import org.apache.hop.core.row.IRowMeta;

/** Immutable conversion plan shared by {@code getFields}, metadata and the runtime. */
public final class CompiledConversionPlan {
  private final IRowMeta inputRowMeta;
  private final IRowMeta outputRowMeta;
  private final GeometryConverterSettings settings;
  private final int singleInputIndex;
  private final int inputXIndex;
  private final int inputYIndex;
  private final int inputZIndex;
  private final int inputMIndex;
  private final CoordinateLayout coordinateLayout;
  private final List<OutputBinding> outputBindings;
  private final WktWriteOptions wktWriteOptions;
  private final WkbWriteOptions wkbWriteOptions;

  CompiledConversionPlan(
      IRowMeta inputRowMeta,
      IRowMeta outputRowMeta,
      GeometryConverterSettings settings,
      int singleInputIndex,
      int inputXIndex,
      int inputYIndex,
      int inputZIndex,
      int inputMIndex,
      CoordinateLayout coordinateLayout,
      List<OutputBinding> outputBindings,
      WktWriteOptions wktWriteOptions,
      WkbWriteOptions wkbWriteOptions) {
    this.inputRowMeta = Objects.requireNonNull(inputRowMeta, "inputRowMeta").clone();
    this.outputRowMeta = Objects.requireNonNull(outputRowMeta, "outputRowMeta").clone();
    this.settings = Objects.requireNonNull(settings, "settings");
    this.singleInputIndex = singleInputIndex;
    this.inputXIndex = inputXIndex;
    this.inputYIndex = inputYIndex;
    this.inputZIndex = inputZIndex;
    this.inputMIndex = inputMIndex;
    this.coordinateLayout = coordinateLayout;
    this.outputBindings = List.copyOf(outputBindings);
    this.wktWriteOptions = wktWriteOptions;
    this.wkbWriteOptions = wkbWriteOptions;
  }

  public IRowMeta inputRowMeta() {
    return inputRowMeta;
  }

  public IRowMeta outputRowMeta() {
    return outputRowMeta;
  }

  public GeometryConverterSettings settings() {
    return settings;
  }

  public int singleInputIndex() {
    return singleInputIndex;
  }

  public int inputXIndex() {
    return inputXIndex;
  }

  public int inputYIndex() {
    return inputYIndex;
  }

  public int inputZIndex() {
    return inputZIndex;
  }

  public int inputMIndex() {
    return inputMIndex;
  }

  public CoordinateLayout coordinateLayout() {
    return coordinateLayout;
  }

  public List<OutputBinding> outputBindings() {
    return outputBindings;
  }

  public WktWriteOptions wktWriteOptions() {
    return wktWriteOptions;
  }

  public WkbWriteOptions wkbWriteOptions() {
    return wkbWriteOptions;
  }
}
