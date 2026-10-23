package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import ch.so.agi.hop.geoprocessing.core.converter.ConfigurationIssue;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterConfigurationException;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import ch.so.agi.hop.geoprocessing.core.converter.GeometryConverterSettings;
import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

/**
 * Headless, testable dialog draft. The SWT dialog only moves strings between this model and
 * widgets.
 */
final class GeometryConverterDialogModel {
  private final GeometryConverterMeta meta;

  private GeometryConverterDialogModel(GeometryConverterMeta meta) {
    this.meta = (GeometryConverterMeta) meta.clone();
  }

  static GeometryConverterDialogModel from(GeometryConverterMeta source) {
    return new GeometryConverterDialogModel(source);
  }

  GeometryConverterMeta toMeta() {
    return (GeometryConverterMeta) meta.clone();
  }

  GeometryConverterSettings toSettings(IVariables variables)
      throws ConverterConfigurationException {
    return meta.toSettings(variables);
  }

  List<ConfigurationIssue> tryValidate(IVariables variables) {
    try {
      meta.toSettings(variables);
      return List.of();
    } catch (ConverterConfigurationException e) {
      return e.issues().isEmpty()
          ? List.of(
              ConfigurationIssue.error(
                  ConfigurationIssue.GC_CONFIG_INVALID, "settings", null, e.getMessage()))
          : e.issues();
    }
  }

  boolean canCommitWithDeferredVariables(IVariables variables) {
    List<ConfigurationIssue> issues = tryValidate(variables);
    return issues.stream()
        .allMatch(issue -> ConfigurationIssue.GC_UNRESOLVED_VARIABLE.equals(issue.code()));
  }

  boolean hasOnlyDeferredVariableIssues(IVariables variables) {
    List<ConfigurationIssue> issues = tryValidate(variables);
    return !issues.isEmpty()
        && issues.stream()
            .allMatch(issue -> ConfigurationIssue.GC_UNRESOLVED_VARIABLE.equals(issue.code()));
  }

  ConverterInputFormat inputFormat() {
    return meta.getInputFormat();
  }

  void inputFormat(ConverterInputFormat format) {
    meta.setInputFormat(format);
  }

  ConverterOutputFormat outputFormat() {
    return meta.getOutputFormat();
  }

  void outputFormat(ConverterOutputFormat format) {
    meta.setOutputFormat(format);
  }

  String inputField() {
    return meta.getInputFieldName();
  }

  void inputField(String value) {
    meta.setInputFieldName(value);
  }

  String inputX() {
    return meta.getInputXFieldName();
  }

  void inputX(String value) {
    meta.setInputXFieldName(value);
  }

  String inputY() {
    return meta.getInputYFieldName();
  }

  void inputY(String value) {
    meta.setInputYFieldName(value);
  }

  String inputZ() {
    return meta.getInputZFieldName();
  }

  void inputZ(String value) {
    meta.setInputZFieldName(value);
  }

  String inputM() {
    return meta.getInputMFieldName();
  }

  void inputM(String value) {
    meta.setInputMFieldName(value);
  }

  String outputField() {
    return meta.getOutputFieldName();
  }

  void outputField(String value) {
    meta.setOutputFieldName(value);
  }

  String outputX() {
    return meta.getOutputXFieldName();
  }

  void outputX(String value) {
    meta.setOutputXFieldName(value);
  }

  String outputY() {
    return meta.getOutputYFieldName();
  }

  void outputY(String value) {
    meta.setOutputYFieldName(value);
  }

  String outputZ() {
    return meta.getOutputZFieldName();
  }

  void outputZ(String value) {
    meta.setOutputZFieldName(value);
  }

  String outputM() {
    return meta.getOutputMFieldName();
  }

  void outputM(String value) {
    meta.setOutputMFieldName(value);
  }

  GeometryByteOrder byteOrder() {
    return meta.getByteOrder();
  }

  void byteOrder(GeometryByteOrder value) {
    meta.setByteOrder(value);
  }

  ConverterSridMode sridMode() {
    return meta.getSridMode();
  }

  void sridMode(ConverterSridMode value) {
    meta.setSridMode(value);
  }

  String sridValue() {
    return meta.getSridValue();
  }

  void sridValue(String value) {
    meta.setSridValue(value);
  }

  boolean overwriteExistingFields() {
    return meta.isOverwriteExistingFields();
  }

  void overwriteExistingFields(boolean value) {
    meta.setOverwriteExistingFields(value);
  }

  ConverterErrorMode errorMode() {
    return meta.getErrorMode();
  }

  void errorMode(ConverterErrorMode value) {
    meta.setErrorMode(value);
  }

  GeoJsonProfile geoJsonInputProfile() {
    return meta.getGeoJsonInputProfile();
  }

  void geoJsonInputProfile(GeoJsonProfile value) {
    meta.setGeoJsonInputProfile(value);
  }

  GeoJsonProfile geoJsonOutputProfile() {
    return meta.getGeoJsonOutputProfile();
  }

  void geoJsonOutputProfile(GeoJsonProfile value) {
    meta.setGeoJsonOutputProfile(value);
  }

  String geoJsonInputContextField() {
    return meta.getGeoJsonInputContextField();
  }

  void geoJsonInputContextField(String value) {
    meta.setGeoJsonInputContextField(value);
  }

  String geoJsonOutputContextField() {
    return meta.getGeoJsonOutputContextField();
  }

  void geoJsonOutputContextField(String value) {
    meta.setGeoJsonOutputContextField(value);
  }

  boolean geoJsonDropZ() {
    return meta.isGeoJsonDropZ();
  }

  void geoJsonDropZ(boolean value) {
    meta.setGeoJsonDropZ(value);
  }

  boolean geoJsonDropM() {
    return meta.isGeoJsonDropM();
  }

  void geoJsonDropM(boolean value) {
    meta.setGeoJsonDropM(value);
  }

  boolean geoJsonEmptyToNull() {
    return meta.isGeoJsonEmptyToNull();
  }

  void geoJsonEmptyToNull(boolean value) {
    meta.setGeoJsonEmptyToNull(value);
  }

  boolean inputUsesCoordinates() {
    return inputFormat() == ConverterInputFormat.COORDINATES;
  }

  boolean outputUsesCoordinates() {
    return outputFormat() == ConverterOutputFormat.COORDINATES;
  }

  boolean binaryOutput() {
    return outputFormat() == ConverterOutputFormat.WKB_BYTES
        || outputFormat() == ConverterOutputFormat.EWKB_BYTES
        || outputFormat() == ConverterOutputFormat.WKB_HEX
        || outputFormat() == ConverterOutputFormat.EWKB_HEX;
  }

  boolean sridValueActive() {
    return sridMode() == ConverterSridMode.SET_IF_MISSING
        || sridMode() == ConverterSridMode.REPLACE;
  }

  String defaultOutputName(ConverterOutputFormat format) {
    return switch (format) {
      case GEOMETRY -> "geometry";
      case GEOJSON -> "geometry_json";
      case WKT -> "geometry_wkt";
      case EWKT -> "geometry_ewkt";
      case WKB_BYTES -> "geometry_wkb";
      case EWKB_BYTES -> "geometry_ewkb";
      case WKB_HEX -> "geometry_wkb_hex";
      case EWKB_HEX -> "geometry_ewkb_hex";
      case COORDINATES -> "geometry";
    };
  }

  String defaultOutputName(ConverterInputFormat format) {
    return switch (format) {
      case GEOMETRY, WKT_EWKT, WKB_BYTES, WKB_HEX, GEOJSON -> "geometry";
      case COORDINATES -> "geometry";
    };
  }
}
