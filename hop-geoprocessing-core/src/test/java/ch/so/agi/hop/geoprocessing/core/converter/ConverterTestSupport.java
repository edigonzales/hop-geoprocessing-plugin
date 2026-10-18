package ch.so.agi.hop.geoprocessing.core.converter;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import com.atolcd.hop.gis.geometry.codec.GeometryCodec;
import com.atolcd.hop.gis.geometry.codec.GeometryReadLimits;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;

final class ConverterTestSupport {
  static final GeometryCodec CODEC = new GeometryCodec(GeometryReadLimits.defaults());

  private ConverterTestSupport() {}

  static SettingsBuilder settings(
      ConverterInputFormat inputFormat, ConverterOutputFormat outputFormat) {
    return new SettingsBuilder(inputFormat, outputFormat);
  }

  static CompiledConversionPlan compile(IRowMeta input, GeometryConverterSettings settings)
      throws ConverterConfigurationException {
    return GeometryConverterSchema.compile(input, settings, "test");
  }

  static GeometryConverterRuntime runtime(CompiledConversionPlan plan) {
    return new GeometryConverterRuntime(plan, CODEC);
  }

  static RowMeta rowMeta() {
    return new RowMeta();
  }

  static ValueMetaGeometry geometry(String name) {
    return new ValueMetaGeometry(name);
  }

  static ValueMetaString string(String name) {
    return new ValueMetaString(name);
  }

  static ValueMetaBinary binary(String name) {
    return new ValueMetaBinary(name);
  }

  static ValueMetaNumber number(String name) {
    return new ValueMetaNumber(name);
  }

  static ValueMetaInteger integer(String name) {
    return new ValueMetaInteger(name);
  }

  static final class SettingsBuilder {
    private final ConverterInputFormat inputFormat;
    private final ConverterOutputFormat outputFormat;
    private int configurationVersion = 1;
    private String inputFieldName = "geom";
    private String inputXFieldName = "x";
    private String inputYFieldName = "y";
    private String inputZFieldName = "";
    private String inputMFieldName = "";
    private String outputFieldName = "geometry";
    private String outputXFieldName = "x";
    private String outputYFieldName = "y";
    private String outputZFieldName = "";
    private String outputMFieldName = "";
    private GeometryByteOrder byteOrder = GeometryByteOrder.BIG_ENDIAN;
    private ConverterSridMode sridMode = ConverterSridMode.KEEP;
    private Integer srid;
    private boolean overwriteExistingFields;
    private ConverterErrorMode errorMode = ConverterErrorMode.FAIL;

    private SettingsBuilder(
        ConverterInputFormat inputFormat, ConverterOutputFormat outputFormat) {
      this.inputFormat = inputFormat;
      this.outputFormat = outputFormat;
    }

    SettingsBuilder version(int value) {
      this.configurationVersion = value;
      return this;
    }

    SettingsBuilder inputField(String value) {
      this.inputFieldName = value;
      return this;
    }

    SettingsBuilder inputX(String value) {
      this.inputXFieldName = value;
      return this;
    }

    SettingsBuilder inputY(String value) {
      this.inputYFieldName = value;
      return this;
    }

    SettingsBuilder inputZ(String value) {
      this.inputZFieldName = value;
      return this;
    }

    SettingsBuilder inputM(String value) {
      this.inputMFieldName = value;
      return this;
    }

    SettingsBuilder outputField(String value) {
      this.outputFieldName = value;
      return this;
    }

    SettingsBuilder outputX(String value) {
      this.outputXFieldName = value;
      return this;
    }

    SettingsBuilder outputY(String value) {
      this.outputYFieldName = value;
      return this;
    }

    SettingsBuilder outputZ(String value) {
      this.outputZFieldName = value;
      return this;
    }

    SettingsBuilder outputM(String value) {
      this.outputMFieldName = value;
      return this;
    }

    SettingsBuilder byteOrder(GeometryByteOrder value) {
      this.byteOrder = value;
      return this;
    }

    SettingsBuilder sridMode(ConverterSridMode value) {
      this.sridMode = value;
      return this;
    }

    SettingsBuilder srid(Integer value) {
      this.srid = value;
      return this;
    }

    SettingsBuilder overwrite(boolean value) {
      this.overwriteExistingFields = value;
      return this;
    }

    SettingsBuilder errorMode(ConverterErrorMode value) {
      this.errorMode = value;
      return this;
    }

    GeometryConverterSettings build() {
      return new GeometryConverterSettings(
          configurationVersion,
          inputFormat,
          inputFieldName,
          inputXFieldName,
          inputYFieldName,
          inputZFieldName,
          inputMFieldName,
          outputFormat,
          outputFieldName,
          outputXFieldName,
          outputYFieldName,
          outputZFieldName,
          outputMFieldName,
          byteOrder,
          sridMode,
          srid,
          overwriteExistingFields,
          errorMode);
    }
  }
}
