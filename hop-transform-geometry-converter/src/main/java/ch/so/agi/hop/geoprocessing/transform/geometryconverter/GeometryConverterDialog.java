package ch.so.agi.hop.geoprocessing.transform.geometryconverter;

import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.converter.ConfigurationIssue;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterErrorMode;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterInputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterOutputFormat;
import ch.so.agi.hop.geoprocessing.core.converter.ConverterSridMode;
import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import com.atolcd.hop.gis.geometry.codec.GeometryByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** One SWT dialog for format conversion and point-coordinate field mapping. */
public class GeometryConverterDialog extends BaseTransformDialog {
  private final GeometryConverterMeta input;
  private GeometryConverterDialogModel model;

  private Text wTransformName;
  private Combo wInputFormat;
  private Combo wInputField;
  private Combo wInputX;
  private Combo wInputY;
  private Combo wInputZ;
  private Combo wInputM;
  private Combo wOutputFormat;
  private Combo wOutputField;
  private Combo wOutputX;
  private Combo wOutputY;
  private Combo wOutputZ;
  private Combo wOutputM;
  private Combo wByteOrder;
  private Combo wSridMode;
  private TextVar wSridValue;
  private Combo wErrorMode;
  private Button wOverwrite;
  private Combo wGeoJsonInputProfile;
  private Combo wGeoJsonOutputProfile;
  private Combo wGeoJsonInputContext;
  private Combo wGeoJsonOutputContext;
  private Button wGeoJsonDropZ;
  private Button wGeoJsonDropM;
  private Button wGeoJsonEmptyToNull;
  private ScrolledComposite scroller;
  private Label wStatus;
  private Composite content;
  private ConverterOutputFormat previousOutputFormat;

  public GeometryConverterDialog(
      Shell parent,
      IVariables variables,
      GeometryConverterMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    shell.setMinimumSize(760, 620);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("Geometry Converter");
    changed = input.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);
    int margin = PropsUi.getMargin();

    Label wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Transform name");
    PropsUi.setLook(wlTransformName);
    FormData fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTransformName);
    FormData fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    wTransformName.setLayoutData(fdTransformName);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    scroller = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    scroller.setExpandHorizontal(true);
    scroller.setExpandVertical(true);
    content = new Composite(scroller, SWT.NONE);
    scroller.setContent(content);
    PropsUi.setLook(content);
    GridLayout gridLayout = new GridLayout(2, false);
    gridLayout.marginWidth = 0;
    gridLayout.marginHeight = 0;
    gridLayout.horizontalSpacing = margin;
    gridLayout.verticalSpacing = margin;
    content.setLayout(gridLayout);
    FormData fdContent = new FormData();
    fdContent.left = new FormAttachment(0, 0);
    fdContent.right = new FormAttachment(100, 0);
    fdContent.top = new FormAttachment(wTransformName, margin * 2);
    fdContent.bottom = new FormAttachment(wOk, -margin * 2);
    scroller.setLayoutData(fdContent);

    section("INPUT");
    wInputFormat = addCombo("Representation", false);
    for (ConverterInputFormat format : ConverterInputFormat.values()) {
      wInputFormat.add(format.name());
    }
    wInputField = addCombo("Field", true);
    wGeoJsonInputProfile = addCombo("GeoJSON input profile", false);
    for (GeoJsonProfile profile : GeoJsonProfile.values()) {
      wGeoJsonInputProfile.add(profile.name());
    }
    wGeoJsonInputContext = addCombo("Input context field (optional)", true);
    wInputX = addCombo("X field", true);
    wInputY = addCombo("Y field", true);
    wInputZ = addCombo("Z field (optional)", true);
    wInputM = addCombo("M field (optional)", true);

    section("OUTPUT");
    wOutputFormat = addCombo("Representation", false);
    for (ConverterOutputFormat format : ConverterOutputFormat.values()) {
      wOutputFormat.add(format.name());
    }
    wOutputField = addCombo("Field", true);
    wGeoJsonOutputProfile = addCombo("GeoJSON output profile", false);
    for (GeoJsonProfile profile : GeoJsonProfile.values()) {
      if (profile != GeoJsonProfile.AUTO) {
        wGeoJsonOutputProfile.add(profile.name());
      }
    }
    wGeoJsonOutputContext = addCombo("Output context field (optional)", true);
    wGeoJsonDropZ = addCheck("Drop Z explicitly");
    wGeoJsonDropM = addCheck("Drop M explicitly");
    wGeoJsonEmptyToNull = addCheck("Replace unrepresentable EMPTY with NULL");
    wOutputX = addCombo("X field", true);
    wOutputY = addCombo("Y field", true);
    wOutputZ = addCombo("Z field (optional)", true);
    wOutputM = addCombo("M field (optional)", true);
    wOverwrite = addCheck("Overwrite existing fields");
    wByteOrder = addCombo("Byte order", false);
    for (GeometryByteOrder order : GeometryByteOrder.values()) {
      wByteOrder.add(order.name());
    }

    section("SRID");
    wSridMode = addCombo("Handling", false);
    for (ConverterSridMode mode : ConverterSridMode.values()) {
      wSridMode.add(mode.name());
    }
    wSridValue = addTextVar("Value");

    section("ERRORS");
    wErrorMode = addCombo("On data error", false);
    for (ConverterErrorMode mode : ConverterErrorMode.values()) {
      wErrorMode.add(mode.name());
    }
    wStatus = addInfoLabel("");

    attachListeners();
    loadFieldChoices();
    getData();
    refreshVisibility();

    wOk.addListener(SWT.Selection, event -> ok());
    wCancel.addListener(SWT.Selection, event -> cancel());
    BaseDialog.defaultShellHandling(shell, value -> ok(), value -> cancel());
    return transformName;
  }

  private void attachListeners() {
    wTransformName.addModifyListener(event -> input.setChanged());
    wInputFormat.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
          loadFieldChoices();
          validateDraft();
        });
    wOutputFormat.addModifyListener(
        event -> {
          input.setChanged();
          ConverterOutputFormat selected =
              defaultEnum(
                  wOutputFormat.getText(),
                  ConverterOutputFormat.class,
                  ConverterOutputFormat.GEOMETRY);
          ConverterOutputFormat old =
              previousOutputFormat == null ? selected : previousOutputFormat;
          previousOutputFormat = selected;
          if (model != null
              && (wOutputField.getText().isBlank()
                  || wOutputField.getText().equals(model.defaultOutputName(old)))) {
            wOutputField.setText(model.defaultOutputName(selected));
          }
          refreshVisibility();
          validateDraft();
        });
    for (Combo combo :
        List.of(
            wInputField,
            wInputX,
            wInputY,
            wInputZ,
            wInputM,
            wOutputField,
            wOutputX,
            wOutputY,
            wOutputZ,
            wOutputM,
            wSridMode,
            wErrorMode,
            wGeoJsonInputProfile,
            wGeoJsonOutputProfile,
            wGeoJsonInputContext,
            wGeoJsonOutputContext)) {
      combo.addModifyListener(
          event -> {
            input.setChanged();
            refreshVisibility();
            validateDraft();
          });
    }
    wSridValue.addModifyListener(
        event -> {
          input.setChanged();
          validateDraft();
        });
    wByteOrder.addModifyListener(
        event -> {
          input.setChanged();
          validateDraft();
        });
    for (Button option : List.of(wGeoJsonDropZ, wGeoJsonDropM, wGeoJsonEmptyToNull)) {
      option.addListener(
          SWT.Selection,
          event -> {
            input.setChanged();
            validateDraft();
          });
    }
    wOverwrite.addListener(
        SWT.Selection,
        event -> {
          input.setChanged();
          validateDraft();
        });
  }

  private void loadFieldChoices() {
    List<String> messages = new ArrayList<>();
    IRowMeta prev = null;
    try {
      prev = pipelineMeta.getPrevTransformFields(variables, transformName);
    } catch (Exception e) {
      messages.add("Unable to inspect upstream fields: " + rootCauseMessage(e));
    }
    if (prev == null) {
      setFieldStatus(messages);
      return;
    }
    List<String> geometryFields = RowMetaSupport.geometryFieldNames(prev);
    List<String> numericFields = RowMetaSupport.numericFieldNames(prev);
    List<String> stringFields = stringFieldNames(prev);
    List<String> binaryFields = binaryFieldNames(prev);
    ConverterInputFormat inputFormat =
        defaultEnum(
            wInputFormat.getText(), ConverterInputFormat.class, ConverterInputFormat.WKT_EWKT);
    List<String> inputCandidates =
        switch (inputFormat) {
          case GEOMETRY -> geometryFields;
          case WKT_EWKT, WKB_HEX, GEOJSON -> stringFields;
          case WKB_BYTES -> binaryFields;
          case COORDINATES -> numericFields;
        };
    applyChoices(wInputField, inputCandidates);
    applyChoices(wInputX, numericFields);
    applyChoices(wInputY, numericFields);
    applyChoices(wInputZ, numericFields);
    applyChoices(wInputM, numericFields);
    applyChoices(wGeoJsonInputContext, stringFields);
    applyChoices(wGeoJsonOutputContext, stringFields);
    applyChoices(wOutputField, RowMetaSupport.fieldNames(prev));
    applyChoices(wOutputX, RowMetaSupport.fieldNames(prev));
    applyChoices(wOutputY, RowMetaSupport.fieldNames(prev));
    applyChoices(wOutputZ, RowMetaSupport.fieldNames(prev));
    applyChoices(wOutputM, RowMetaSupport.fieldNames(prev));
    if (inputFormat != ConverterInputFormat.COORDINATES
        && wInputField.getText().isBlank()
        && inputCandidates.size() == 1) {
      wInputField.setText(inputCandidates.get(0));
    }
    if (inputFormat == ConverterInputFormat.COORDINATES) {
      if (wInputX.getText().isBlank() && numericFields.size() == 1) {
        wInputX.setText(numericFields.get(0));
      }
      if (wInputY.getText().isBlank() && numericFields.size() >= 2) {
        wInputY.setText(numericFields.get(1));
      }
    }
    setFieldStatus(messages);
  }

  private void getData() {
    model = GeometryConverterDialogModel.from(input);
    wTransformName.setText(transformName == null ? "" : transformName);
    wInputFormat.setText(model.inputFormat().name());
    wInputField.setText(defaultText(model.inputField()));
    wInputX.setText(defaultText(model.inputX()));
    wInputY.setText(defaultText(model.inputY()));
    wInputZ.setText(defaultText(model.inputZ()));
    wInputM.setText(defaultText(model.inputM()));
    previousOutputFormat = model.outputFormat();
    wOutputFormat.setText(model.outputFormat().name());
    wOutputField.setText(defaultText(model.outputField()));
    wOutputX.setText(defaultText(model.outputX()));
    wOutputY.setText(defaultText(model.outputY()));
    wOutputZ.setText(defaultText(model.outputZ()));
    wOutputM.setText(defaultText(model.outputM()));
    wByteOrder.setText(model.byteOrder().name());
    wSridMode.setText(model.sridMode().name());
    wSridValue.setText(defaultText(model.sridValue()));
    wOverwrite.setSelection(model.overwriteExistingFields());
    wErrorMode.setText(model.errorMode().name());
    wGeoJsonInputProfile.setText(model.geoJsonInputProfile().name());
    wGeoJsonOutputProfile.setText(model.geoJsonOutputProfile().name());
    wGeoJsonInputContext.setText(defaultText(model.geoJsonInputContextField()));
    wGeoJsonOutputContext.setText(defaultText(model.geoJsonOutputContextField()));
    wGeoJsonDropZ.setSelection(model.geoJsonDropZ());
    wGeoJsonDropM.setSelection(model.geoJsonDropM());
    wGeoJsonEmptyToNull.setSelection(model.geoJsonEmptyToNull());
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void readDraft() {
    model.inputFormat(
        defaultEnum(
            wInputFormat.getText(), ConverterInputFormat.class, ConverterInputFormat.WKT_EWKT));
    model.inputField(wInputField.getText());
    model.inputX(wInputX.getText());
    model.inputY(wInputY.getText());
    model.inputZ(wInputZ.getText());
    model.inputM(wInputM.getText());
    model.outputFormat(
        defaultEnum(
            wOutputFormat.getText(), ConverterOutputFormat.class, ConverterOutputFormat.GEOMETRY));
    model.outputField(wOutputField.getText());
    model.outputX(wOutputX.getText());
    model.outputY(wOutputY.getText());
    model.outputZ(wOutputZ.getText());
    model.outputM(wOutputM.getText());
    model.byteOrder(
        defaultEnum(wByteOrder.getText(), GeometryByteOrder.class, GeometryByteOrder.BIG_ENDIAN));
    model.sridMode(
        defaultEnum(wSridMode.getText(), ConverterSridMode.class, ConverterSridMode.KEEP));
    model.sridValue(wSridValue.getText());
    model.overwriteExistingFields(wOverwrite.getSelection());
    model.errorMode(
        defaultEnum(wErrorMode.getText(), ConverterErrorMode.class, ConverterErrorMode.FAIL));
    model.geoJsonInputProfile(
        defaultEnum(wGeoJsonInputProfile.getText(), GeoJsonProfile.class, GeoJsonProfile.AUTO));
    model.geoJsonOutputProfile(
        defaultEnum(wGeoJsonOutputProfile.getText(), GeoJsonProfile.class, GeoJsonProfile.RFC7946));
    model.geoJsonInputContextField(wGeoJsonInputContext.getText());
    model.geoJsonOutputContextField(wGeoJsonOutputContext.getText());
    model.geoJsonDropZ(wGeoJsonDropZ.getSelection());
    model.geoJsonDropM(wGeoJsonDropM.getSelection());
    model.geoJsonEmptyToNull(wGeoJsonEmptyToNull.getSelection());
  }

  private void refreshVisibility() {
    ConverterInputFormat inputFormat =
        defaultEnum(
            wInputFormat.getText(), ConverterInputFormat.class, ConverterInputFormat.WKT_EWKT);
    ConverterOutputFormat outputFormat =
        defaultEnum(
            wOutputFormat.getText(), ConverterOutputFormat.class, ConverterOutputFormat.GEOMETRY);
    boolean inputCoordinates = inputFormat == ConverterInputFormat.COORDINATES;
    boolean outputCoordinates = outputFormat == ConverterOutputFormat.COORDINATES;
    boolean binary =
        outputFormat == ConverterOutputFormat.WKB_BYTES
            || outputFormat == ConverterOutputFormat.EWKB_BYTES
            || outputFormat == ConverterOutputFormat.WKB_HEX
            || outputFormat == ConverterOutputFormat.EWKB_HEX;
    toggle(wInputField, !inputCoordinates);
    toggle(wInputX, inputCoordinates);
    toggle(wInputY, inputCoordinates);
    toggle(wInputZ, inputCoordinates);
    toggle(wInputM, inputCoordinates);
    toggle(wOutputField, !outputCoordinates);
    toggle(wOutputX, outputCoordinates);
    toggle(wOutputY, outputCoordinates);
    toggle(wOutputZ, outputCoordinates);
    toggle(wOutputM, outputCoordinates);
    toggle(wByteOrder, binary);
    boolean jsonInput = inputFormat == ConverterInputFormat.GEOJSON;
    boolean jsonOutput = outputFormat == ConverterOutputFormat.GEOJSON;
    toggle(wGeoJsonInputProfile, jsonInput);
    toggle(wGeoJsonOutputProfile, jsonOutput);
    toggle(wGeoJsonInputContext, jsonInput || jsonOutput);
    toggle(wGeoJsonOutputContext, jsonInput || jsonOutput);
    toggle(wGeoJsonDropZ, jsonOutput);
    toggle(wGeoJsonDropM, jsonOutput);
    toggle(wGeoJsonEmptyToNull, jsonOutput);
    boolean sridActive =
        defaultEnum(wSridMode.getText(), ConverterSridMode.class, ConverterSridMode.KEEP)
                == ConverterSridMode.SET_IF_MISSING
            || defaultEnum(wSridMode.getText(), ConverterSridMode.class, ConverterSridMode.KEEP)
                == ConverterSridMode.REPLACE;
    toggle(wSridValue, sridActive);
    content.layout(true, true);
    scroller.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
  }

  private void validateDraft() {
    if (model == null) {
      return;
    }
    readDraft();
    List<ConfigurationIssue> issues = model.tryValidate(variables);
    if (issues.isEmpty()) {
      setFieldStatus(List.of("Configuration is valid."));
    } else {
      setFieldStatus(issues.stream().map(ConfigurationIssue::describe).toList());
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    readDraft();
    List<ConfigurationIssue> issues = model.tryValidate(variables);
    boolean onlyDeferred =
        !issues.isEmpty()
            && issues.stream()
                .allMatch(issue -> ConfigurationIssue.GC_UNRESOLVED_VARIABLE.equals(issue.code()));
    if (!issues.isEmpty() && !onlyDeferred) {
      showValidationWarning(
          issues.stream()
              .map(ConfigurationIssue::describe)
              .reduce((a, b) -> a + "\n" + b)
              .orElse(""));
      return;
    }
    if (onlyDeferred) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText("Deferred variable");
      box.setMessage(
          "The configuration uses variables that are only resolved at runtime.\n"
              + "The schema cannot be fully checked until the pipeline starts.");
      box.open();
    }
    transformName = wTransformName.getText();
    input.copyFrom(model.toMeta());
    input.setChanged(true);
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private void section(String title) {
    Label label = new Label(content, SWT.LEFT);
    label.setText(title);
    PropsUi.setLook(label);
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    label.setLayoutData(data);
  }

  private Combo addCombo(String labelText, boolean editable) {
    Label label = addLabel(labelText);
    Combo combo =
        new Combo(content, (editable ? SWT.DROP_DOWN : SWT.DROP_DOWN | SWT.READ_ONLY) | SWT.BORDER);
    combo.setLayoutData(defaultGridData());
    combo.setData("label", label);
    PropsUi.setLook(combo);
    return combo;
  }

  private TextVar addTextVar(String labelText) {
    Label label = addLabel(labelText);
    TextVar text = new TextVar(variables, content, SWT.SINGLE | SWT.BORDER);
    text.setLayoutData(defaultGridData());
    text.setData("label", label);
    PropsUi.setLook(text);
    return text;
  }

  private Button addCheck(String labelText) {
    Label label = addLabel(labelText);
    Button button = new Button(content, SWT.CHECK);
    button.setLayoutData(defaultGridData());
    button.setData("label", label);
    PropsUi.setLook(button);
    return button;
  }

  private Label addInfoLabel(String text) {
    Label label = new Label(content, SWT.WRAP);
    label.setText(text);
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    PropsUi.setLook(label);
    return label;
  }

  private Label addLabel(String text) {
    Label label = new Label(content, SWT.RIGHT);
    label.setText(text);
    label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    PropsUi.setLook(label);
    return label;
  }

  private GridData defaultGridData() {
    return new GridData(SWT.FILL, SWT.CENTER, true, false);
  }

  private void applyChoices(Combo combo, List<String> choices) {
    String text = combo.getText();
    combo.setItems(choices.toArray(String[]::new));
    if (!text.isBlank()) {
      combo.setText(text);
    }
  }

  private void toggle(Control control, boolean visible) {
    GridData data = (GridData) control.getLayoutData();
    data.exclude = !visible;
    control.setVisible(visible);
    Object label = control.getData("label");
    if (label instanceof Control labelControl) {
      GridData labelData = (GridData) labelControl.getLayoutData();
      labelData.exclude = !visible;
      labelControl.setVisible(visible);
    }
  }

  private void setFieldStatus(List<String> messages) {
    String text = String.join("\n", messages);
    wStatus.setText(text);
    GridData data = (GridData) wStatus.getLayoutData();
    data.exclude = text.isBlank();
    wStatus.setVisible(!text.isBlank());
    content.layout(true, true);
    scroller.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
  }

  private void showValidationWarning(String message) {
    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    box.setText("Invalid configuration");
    box.setMessage(message);
    box.open();
  }

  private String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null || current.getMessage().isBlank()
        ? current.getClass().getName()
        : current.getClass().getSimpleName() + ": " + current.getMessage();
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private static <E extends Enum<E>> E defaultEnum(String value, Class<E> type, E fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  private static List<String> stringFieldNames(IRowMeta rowMeta) {
    List<String> names = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta.getType() == IValueMeta.TYPE_STRING) {
        names.add(valueMeta.getName());
      }
    }
    return names;
  }

  private static List<String> binaryFieldNames(IRowMeta rowMeta) {
    List<String> names = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta.getType() == IValueMeta.TYPE_BINARY) {
        names.add(valueMeta.getName());
      }
    }
    return names;
  }
}
