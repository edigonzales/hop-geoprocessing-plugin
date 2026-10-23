package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.CoordinateLayout;
import com.atolcd.hop.gis.geometry.codec.GeoJsonContext;
import com.atolcd.hop.gis.geometry.codec.GeoJsonReadOptions;
import com.atolcd.hop.gis.geometry.codec.GeoJsonReadResult;
import com.atolcd.hop.gis.geometry.codec.GeoJsonWriteOptions;
import com.atolcd.hop.gis.geometry.codec.GeometryCodec;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecErrorCode;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import com.atolcd.hop.gis.geometry.codec.GeometrySridSupport;
import com.atolcd.hop.gis.geometry.codec.PointCoordinateSupport;
import com.atolcd.hop.gis.geometry.codec.PointCoordinates;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

/** Row-wise conversion engine independent of Hop transform lifecycle and SWT. */
public final class GeometryConverterRuntime {
  private final CompiledConversionPlan plan;
  private final GeometryCodec codec;
  private final GeometryFactory geometryFactory;

  public GeometryConverterRuntime(CompiledConversionPlan plan, GeometryCodec codec) {
    this.plan = java.util.Objects.requireNonNull(plan, "plan");
    this.codec = java.util.Objects.requireNonNull(codec, "codec");
    this.geometryFactory =
        new GeometryFactory(
            new PrecisionModel(), 0, PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
  }

  public ConversionResult convert(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    requireRow(input);
    ContextInput suppliedContext = readContext(input);
    GeoJsonContext context = suppliedContext.value();
    Geometry geometry;
    if (plan.settings().inputFormat() == ConverterInputFormat.GEOJSON) {
      GeoJsonReadResult decoded = readGeoJson(input, context);
      geometry = decoded.geometry();
      context = decoded.context();
    } else {
      geometry = readInput(input);
    }
    if (geometry != null) {
      int sourceSrid = GeometrySridSupport.resolveConsistentSrid(geometry);
      geometry = applySridPolicy(geometry);
      context = assignedContext(context, sourceSrid, geometry);
    }
    if (plan.settings().inputFormat() == ConverterInputFormat.GEOJSON
        && plan.settings().outputFormat() != ConverterOutputFormat.GEOJSON
        && (plan.settings().geoJsonOutputContextField() == null
            || plan.settings().geoJsonOutputContextField().isBlank())
        && (context.coordRefSys() instanceof java.util.List<?>
            || context.measures().containsKey("unit")
            || context.measures().containsKey("description"))) {
      throw new GeometryCodecException(
          GeometryCodecErrorCode.GC_TYPE_MISMATCH,
          "Select an output context field to preserve compound CRS or measure metadata");
    }
    Object[] targetValues = calculateTargetValues(geometry, context, suppliedContext.supplied());
    Object[] row = writeOutputCopy(input, targetValues);
    return new ConversionResult(row, allTargetValuesNull(targetValues), sridOmitted(geometry));
  }

  public Object[] nullOutputs(Object[] input) throws ConverterIntegrationException {
    Object[] row = copyInput(input);
    for (OutputBinding binding : plan.outputBindings()) {
      row[binding.index()] = null;
    }
    return row;
  }

  Geometry readInput(Object[] input) throws GeometryCodecException, ConverterIntegrationException {
    requireRow(input);
    return switch (plan.settings().inputFormat()) {
      case GEOMETRY -> readNative(input);
      case GEOJSON -> readGeoJson(input, readContext(input).value()).geometry();
      case WKT_EWKT -> readText(input);
      case WKB_BYTES -> readBinary(input);
      case WKB_HEX -> readHex(input);
      case COORDINATES -> readCoordinates(input);
    };
  }

  Geometry applySridPolicy(Geometry geometry) throws GeometryCodecException {
    if (geometry == null) {
      return null;
    }
    int effectiveSrid = GeometrySridSupport.resolveConsistentSrid(geometry);
    ConverterSridMode mode = plan.settings().sridMode();
    return switch (mode) {
      case KEEP -> geometry;
      case SET_IF_MISSING ->
          effectiveSrid == 0
              ? GeometrySridSupport.copyWithSrid(geometry, requiredSrid())
              : geometry;
      case REPLACE -> GeometrySridSupport.copyWithSrid(geometry, requiredSrid());
      case CLEAR -> effectiveSrid == 0 ? geometry : GeometrySridSupport.copyWithSrid(geometry, 0);
    };
  }

  Object[] calculateTargetValues(Geometry geometry) throws GeometryCodecException {
    return calculateTargetValues(geometry, GeoJsonContext.empty(), false);
  }

  private GeoJsonContext assignedContext(GeoJsonContext context, int sourceSrid, Geometry geometry)
      throws GeometryCodecException {
    int assignedSrid = GeometrySridSupport.resolveConsistentSrid(geometry);
    if (plan.settings().sridMode() == ConverterSridMode.CLEAR) {
      return new GeoJsonContext(context.profile(), null, context.measures());
    }
    if (sourceSrid == assignedSrid) {
      return context;
    }
    Object reference = "EPSG:" + assignedSrid;
    if (context.coordRefSys() instanceof List<?> compound && !compound.isEmpty()) {
      var components = new java.util.ArrayList<Object>(compound);
      components.set(0, reference);
      reference = components;
    }
    return new GeoJsonContext(context.profile(), reference, context.measures());
  }

  private Object[] calculateTargetValues(
      Geometry geometry, GeoJsonContext context, boolean suppliedContext)
      throws GeometryCodecException {
    List<OutputBinding> bindings = plan.outputBindings();
    Object[] values = new Object[bindings.size()];
    if (geometry != null)
      switch (plan.settings().outputFormat()) {
        case GEOMETRY -> values[0] = codec.copyExact(geometry);
        case GEOJSON ->
            values[0] =
                codec.writeGeoJson(
                    geometry,
                    new GeoJsonWriteOptions(
                        plan.settings().geoJsonOutputProfile(),
                        context,
                        plan.settings().geoJsonDropZ(),
                        plan.settings().geoJsonDropM(),
                        plan.settings().geoJsonEmptyToNull()));
        case WKT, EWKT -> values[0] = codec.writeText(geometry, plan.wktWriteOptions());
        case WKB_BYTES, EWKB_BYTES ->
            values[0] = codec.writeBinary(geometry, plan.wkbWriteOptions());
        case WKB_HEX, EWKB_HEX -> values[0] = codec.writeHex(geometry, plan.wkbWriteOptions());
        case COORDINATES -> writeCoordinates(geometry, bindings, values);
      }
    for (int i = 0; i < bindings.size(); i++) {
      if (bindings.get(i).role() == OutputRole.CONTEXT && (geometry != null || suppliedContext)) {
        GeoJsonContext outputContext = context;
        if (plan.settings().outputFormat() == ConverterOutputFormat.GEOJSON
            && values[0] != null
            && !"null".equals(values[0])) {
          outputContext =
              codec
                  .readGeoJson(
                      (String) values[0],
                      new GeoJsonReadOptions(
                          plan.settings().geoJsonOutputProfile(), GeoJsonContext.empty()))
                  .context();
        }
        values[i] = outputContext.toJson();
      }
    }
    return values;
  }

  Object[] writeOutputCopy(Object[] input, Object[] targetValues)
      throws ConverterIntegrationException {
    if (targetValues.length != plan.outputBindings().size()) {
      throw new ConverterIntegrationException(
          "Compiled conversion plan has inconsistent target values: "
              + targetValues.length
              + " values for "
              + plan.outputBindings().size()
              + " bindings");
    }
    Object[] row = copyInput(input);
    List<OutputBinding> bindings = plan.outputBindings();
    for (int i = 0; i < bindings.size(); i++) {
      row[bindings.get(i).index()] = targetValues[i];
    }
    return row;
  }

  private void writeCoordinates(Geometry geometry, List<OutputBinding> bindings, Object[] values)
      throws GeometryCodecException {
    PointCoordinates coordinates = PointCoordinateSupport.extract(geometry);
    for (int i = 0; i < bindings.size(); i++) {
      values[i] =
          switch (bindings.get(i).role()) {
            case X -> coordinates.x();
            case Y -> coordinates.y();
            case Z -> coordinates.z();
            case M -> coordinates.m();
            case CONTEXT -> null;
            case GEOMETRY, ENCODED ->
                throw new GeometryCodecException(
                    GeometryCodecErrorCode.GC_TYPE_MISMATCH,
                    "Coordinate output contains a non-coordinate binding");
          };
    }
  }

  private Geometry readNative(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    int index = plan.singleInputIndex();
    Object value = valueAt(input, index, "input_field");
    if (value == null) {
      return null;
    }
    IValueMeta valueMeta = plan.inputRowMeta().getValueMeta(index);
    if (valueMeta.getType() != ValueMetaGeometry.TYPE_GEOMETRY) {
      throw new ConverterIntegrationException(
          "Native Geometry input field does not carry the shared Geometry value type: "
              + valueMeta.getName());
    }
    if (!(value instanceof Geometry geometry)) {
      throw new ConverterIntegrationException(
          "Native Geometry value for field '"
              + valueMeta.getName()
              + "' is not an instance of the shared Geometry runtime (found "
              + value.getClass().getName()
              + ")");
    }
    return geometry;
  }

  private record ContextInput(GeoJsonContext value, boolean supplied) {}

  private ContextInput readContext(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    String field = plan.settings().geoJsonInputContextField();
    if (!plan.settings().usesGeoJson() || field == null || field.isBlank()) {
      return new ContextInput(GeoJsonContext.empty(), false);
    }
    int index = plan.inputRowMeta().indexOfValue(field);
    String json = stringAt(input, index, "geojson_input_context_field");
    return new ContextInput(
        json == null ? GeoJsonContext.empty() : GeoJsonContext.fromJson(json), json != null);
  }

  private GeoJsonReadResult readGeoJson(Object[] input, GeoJsonContext context)
      throws GeometryCodecException, ConverterIntegrationException {
    String json = stringAt(input, plan.singleInputIndex(), "input_field");
    if (json == null) {
      return new GeoJsonReadResult(null, context);
    }
    return codec.readGeoJson(
        json, new GeoJsonReadOptions(plan.settings().geoJsonInputProfile(), context));
  }

  private String stringAt(Object[] input, int index, String property)
      throws GeometryCodecException, ConverterIntegrationException {
    Object value = valueAt(input, index, property);
    if (value == null) {
      return null;
    }
    try {
      return plan.inputRowMeta().getValueMeta(index).getString(value);
    } catch (HopValueException | ClassCastException e) {
      throw typeMismatch(index, property, e);
    }
  }

  private Geometry readText(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    int index = plan.singleInputIndex();
    Object value = valueAt(input, index, "input_field");
    if (value == null) {
      return null;
    }
    try {
      String text = plan.inputRowMeta().getValueMeta(index).getString(value);
      return codec.readText(text);
    } catch (HopValueException | ClassCastException e) {
      throw typeMismatch(index, "input_field", e);
    }
  }

  private Geometry readBinary(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    int index = plan.singleInputIndex();
    Object value = valueAt(input, index, "input_field");
    if (value == null) {
      return null;
    }
    try {
      byte[] binary = plan.inputRowMeta().getValueMeta(index).getBinary(value);
      return codec.readBinary(binary);
    } catch (HopValueException | ClassCastException e) {
      throw typeMismatch(index, "input_field", e);
    }
  }

  private Geometry readHex(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    int index = plan.singleInputIndex();
    Object value = valueAt(input, index, "input_field");
    if (value == null) {
      return null;
    }
    try {
      String text = plan.inputRowMeta().getValueMeta(index).getString(value);
      return codec.readHex(text);
    } catch (HopValueException | ClassCastException e) {
      throw typeMismatch(index, "input_field", e);
    }
  }

  private Geometry readCoordinates(Object[] input)
      throws GeometryCodecException, ConverterIntegrationException {
    Double x = numberAt(input, plan.inputXIndex(), "input_x_field");
    Double y = numberAt(input, plan.inputYIndex(), "input_y_field");
    Double z = plan.inputZIndex() < 0 ? null : numberAt(input, plan.inputZIndex(), "input_z_field");
    Double m = plan.inputMIndex() < 0 ? null : numberAt(input, plan.inputMIndex(), "input_m_field");
    CoordinateLayout layout = plan.coordinateLayout();
    PointCoordinates coordinates = new PointCoordinates(x, y, z, m, layout);
    return PointCoordinateSupport.create(coordinates, 0, geometryFactory);
  }

  private Double numberAt(Object[] input, int index, String property)
      throws GeometryCodecException, ConverterIntegrationException {
    Object value = valueAt(input, index, property);
    if (value == null) {
      return null;
    }
    try {
      return plan.inputRowMeta().getValueMeta(index).getNumber(value);
    } catch (HopValueException | ClassCastException e) {
      throw typeMismatch(index, property, e);
    }
  }

  private Object valueAt(Object[] input, int index, String property)
      throws ConverterIntegrationException {
    if (index < 0 || index >= plan.inputRowMeta().size()) {
      throw new ConverterIntegrationException(
          "Compiled plan refers to an invalid input field for " + property + ": " + index);
    }
    if (input == null) {
      throw new ConverterIntegrationException("Input row must not be null");
    }
    if (index >= input.length) {
      throw new ConverterIntegrationException(
          "Input row is shorter than the compiled input schema for " + property);
    }
    return input[index];
  }

  private void requireRow(Object[] input) throws ConverterIntegrationException {
    if (input == null) {
      throw new ConverterIntegrationException("Input row must not be null");
    }
    if (input.length < plan.inputRowMeta().size()) {
      throw new ConverterIntegrationException(
          "Input row length "
              + input.length
              + " is shorter than the compiled input schema "
              + plan.inputRowMeta().size());
    }
  }

  private Object[] copyInput(Object[] input) throws ConverterIntegrationException {
    if (input == null) {
      throw new ConverterIntegrationException("Input row must not be null");
    }
    return Arrays.copyOf(input, plan.outputRowMeta().size());
  }

  private GeometryCodecException typeMismatch(int index, String property, Exception cause) {
    String field = plan.inputRowMeta().getValueMeta(index).getName();
    return new GeometryCodecException(
        GeometryCodecErrorCode.GC_TYPE_MISMATCH,
        "Unable to read " + property + " field '" + field + "'",
        -1,
        cause);
  }

  private int requiredSrid() throws GeometryCodecException {
    Integer value = plan.settings().srid();
    if (value == null || value <= 0) {
      throw new GeometryCodecException(
          GeometryCodecErrorCode.GC_INVALID_SRID,
          "A positive SRID is required for mode " + plan.settings().sridMode());
    }
    return value;
  }

  private boolean sridOmitted(Geometry geometry) throws GeometryCodecException {
    if (geometry == null || GeometrySridSupport.resolveConsistentSrid(geometry) == 0) {
      return false;
    }
    return switch (plan.settings().outputFormat()) {
      case WKT, WKB_BYTES, WKB_HEX, COORDINATES -> true;
      case GEOMETRY, EWKT, EWKB_BYTES, EWKB_HEX, GEOJSON -> false;
    };
  }

  private static boolean allTargetValuesNull(Object[] values) {
    for (Object value : values) {
      if (value != null) {
        return false;
      }
    }
    return true;
  }
}
