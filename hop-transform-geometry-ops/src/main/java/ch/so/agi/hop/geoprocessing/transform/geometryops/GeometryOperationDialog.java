package ch.so.agi.hop.geoprocessing.transform.geometryops;

import ch.so.agi.hop.geoprocessing.core.BufferCapStyle;
import ch.so.agi.hop.geoprocessing.core.BufferJoinStyle;
import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.OverlayMode;
import ch.so.agi.hop.geoprocessing.core.ParameterId;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.SWT;
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

public class GeometryOperationDialog extends BaseTransformDialog {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  private final GeometryOperationMeta input;
  private final List<OperationDescriptor> operations;

  private Combo wOperation;
  private Text wExecutionMode;
  private Combo wPrimaryGeometryField;
  private Combo wSecondaryGeometryField;
  private Combo wDistanceMode;
  private TextVar wDistanceValue;
  private Combo wDistanceField;
  private Combo wOverlayMode;
  private TextVar wPrecisionScale;
  private Combo wOutputMode;
  private Text wOutputField;
  private Text wBufferSegments;
  private Combo wBufferCapStyle;
  private Combo wBufferJoinStyle;
  private Button wBufferSingleSided;
  private Label wFieldStatus;
  private Composite content;

  public GeometryOperationDialog(
      Shell parent, IVariables variables, GeometryOperationMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
    this.operations = input.listOperations();
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    shell.setMinimumSize(760, 620);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("Geometry Operation");

    changed = input.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();

    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Name");
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    wTransformName.setLayoutData(fdTransformName);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    content = new Composite(shell, SWT.NONE);
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
    content.setLayoutData(fdContent);

    wOperation = addCombo("Operation");
    for (OperationDescriptor descriptor : operations) {
      wOperation.add(descriptor.group().getLabel() + " - " + descriptor.label());
    }

    wExecutionMode = addReadOnlyText("Execution mode");
    wPrimaryGeometryField = addCombo("Primary geometry field");
    wSecondaryGeometryField = addCombo("Secondary geometry field");
    wDistanceMode = addCombo("Distance mode");
    for (DistanceMode mode : DistanceMode.values()) {
      wDistanceMode.add(mode.name());
    }
    wDistanceValue = addTextVar("Static distance");
    wDistanceField = addCombo("Distance field");
    wOverlayMode = addCombo("Overlay mode");
    for (OverlayMode mode : OverlayMode.values()) {
      wOverlayMode.add(mode.name());
    }
    wPrecisionScale = addTextVar("Precision scale");
    wOutputMode = addCombo("Output mode");
    for (GeometryOutputMode mode : GeometryOutputMode.values()) {
      wOutputMode.add(mode.name());
    }
    wOutputField = addText("Output field");
    wBufferSegments = addText("Buffer segments");
    wBufferCapStyle = addCombo("Buffer cap style");
    for (BufferCapStyle style : BufferCapStyle.values()) {
      wBufferCapStyle.add(style.name());
    }
    wBufferJoinStyle = addCombo("Buffer join style");
    for (BufferJoinStyle style : BufferJoinStyle.values()) {
      wBufferJoinStyle.add(style.name());
    }
    wBufferSingleSided = addCheck("Buffer single sided");
    wFieldStatus = addInfoLabel("");

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
    wOperation.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wPrimaryGeometryField.addModifyListener(event -> input.setChanged());
    wSecondaryGeometryField.addModifyListener(event -> input.setChanged());
    wDistanceMode.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wDistanceValue.addModifyListener(event -> input.setChanged());
    wDistanceField.addModifyListener(event -> input.setChanged());
    wOverlayMode.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wPrecisionScale.addModifyListener(event -> input.setChanged());
    wOutputMode.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wOutputField.addModifyListener(event -> input.setChanged());
    wBufferSegments.addModifyListener(event -> input.setChanged());
    wBufferCapStyle.addModifyListener(event -> input.setChanged());
    wBufferJoinStyle.addModifyListener(event -> input.setChanged());
    wBufferSingleSided.addListener(SWT.Selection, event -> input.setChanged());
  }

  private void loadFieldChoices() {
    List<String> messages = new ArrayList<>();
    wPrimaryGeometryField.removeAll();
    wSecondaryGeometryField.removeAll();
    wDistanceField.removeAll();
    try {
      IRowMeta prev = pipelineMeta.getPrevTransformFields(variables, transformName);
      GeometryFieldSelection primarySelection =
          GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(prev, input.getPrimaryGeometryFieldName());
      GeometryFieldSelection secondarySelection =
          GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(prev, input.getSecondaryGeometryFieldName());
      applySelection(wPrimaryGeometryField, primarySelection);
      applySelection(wSecondaryGeometryField, secondarySelection);
      collectSelectionMessage(messages, primarySelection.warning());
      collectSelectionMessage(messages, secondarySelection.warning());
      if (primarySelection.fieldNames().isEmpty()) {
        messages.add("No geometry field candidates found on the primary input.");
      }
      for (String numericFieldName : RowMetaSupport.numericFieldNames(prev)) {
        wDistanceField.add(numericFieldName);
      }
    } catch (Exception e) {
      messages.add("Unable to inspect upstream fields: " + rootCauseMessage(e));
    }
    setFieldStatus(messages);
  }

  private void getData() {
    wTransformName.setText(transformName == null ? "" : transformName);
    selectOperation(input.getOperationId());
    wDistanceMode.setText((input.getDistanceMode() == null ? DistanceMode.STATIC : input.getDistanceMode()).name());
    wDistanceValue.setText(defaultText(input.getDistanceValue()));
    wDistanceField.setText(defaultText(input.getDistanceFieldName()));
    wOverlayMode.setText(input.getOverlayMode().name());
    wPrecisionScale.setText(defaultText(input.getPrecisionScale()));
    wOutputMode.setText((input.getOutputMode() == null ? GeometryOutputMode.APPEND : input.getOutputMode()).name());
    wOutputField.setText(defaultText(input.getOutputFieldName()));
    wBufferSegments.setText(String.valueOf(input.getBufferSegments() == null ? 8 : input.getBufferSegments()));
    wBufferCapStyle.setText((input.getBufferCapStyle() == null ? BufferCapStyle.ROUND : input.getBufferCapStyle()).name());
    wBufferJoinStyle.setText((input.getBufferJoinStyle() == null ? BufferJoinStyle.ROUND : input.getBufferJoinStyle()).name());
    wBufferSingleSided.setSelection(input.isBufferSingleSided());
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void selectOperation(String operationId) {
    for (int index = 0; index < operations.size(); index++) {
      if (operations.get(index).id().equals(operationId)) {
        wOperation.select(index);
        return;
      }
    }
    if (!operations.isEmpty()) {
      wOperation.select(0);
    }
  }

  private void refreshVisibility() {
    OperationDescriptor descriptor = currentDescriptor();
    wExecutionMode.setText(descriptor.executionMode().getLabel());
    boolean binary = descriptor.arity().name().equals("BINARY");
    boolean needsDistance = descriptor.requires(ParameterId.DISTANCE);
    boolean overlayOperation = descriptor.requires(ParameterId.OVERLAY_MODE);
    boolean extendedBuffer = "buffer_extended".equals(descriptor.id());
    boolean needsPrecisionScale =
        "reduce_precision".equals(descriptor.id())
            || (overlayOperation
                && OverlayMode.valueOf(defaultText(wOverlayMode.getText(), OverlayMode.STANDARD.name()))
                    == OverlayMode.FIXED_PRECISION);
    boolean appendMode = GeometryOutputMode.valueOf(wOutputMode.getText().isBlank() ? GeometryOutputMode.APPEND.name() : wOutputMode.getText())
        == GeometryOutputMode.APPEND;

    toggleControl(wSecondaryGeometryField, binary);
    toggleControl(wDistanceMode, needsDistance);
    toggleControl(wDistanceValue, needsDistance && DistanceMode.valueOf(defaultText(wDistanceMode.getText(), DistanceMode.STATIC.name())) == DistanceMode.STATIC);
    toggleControl(wDistanceField, needsDistance && DistanceMode.valueOf(defaultText(wDistanceMode.getText(), DistanceMode.STATIC.name())) == DistanceMode.FIELD);
    toggleControl(wOverlayMode, overlayOperation);
    toggleControl(wPrecisionScale, needsPrecisionScale);
    toggleControl(wOutputField, appendMode);
    toggleControl(wBufferSegments, extendedBuffer);
    toggleControl(wBufferCapStyle, extendedBuffer);
    toggleControl(wBufferJoinStyle, extendedBuffer);
    toggleControl(wBufferSingleSided, extendedBuffer);
    content.layout(true, true);
  }

  private void toggleControl(Control control, boolean visible) {
    ((GridData) control.getLayoutData()).exclude = !visible;
    control.setVisible(visible);
    Control label = (Control) control.getData("label");
    if (label != null) {
      ((GridData) label.getLayoutData()).exclude = !visible;
      label.setVisible(visible);
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    if (wPrimaryGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a primary geometry field.");
      return;
    }
    if (currentDescriptor().arity().name().equals("BINARY") && wSecondaryGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a secondary geometry field.");
      return;
    }
    if ("reduce_precision".equals(currentDescriptor().id()) && wPrecisionScale.getText().isBlank()) {
      showValidationWarning("Please enter a precision scale.");
      return;
    }
    if (currentDescriptor().requires(ParameterId.OVERLAY_MODE)
        && OverlayMode.valueOf(defaultText(wOverlayMode.getText(), OverlayMode.STANDARD.name()))
            == OverlayMode.FIXED_PRECISION
        && wPrecisionScale.getText().isBlank()) {
      showValidationWarning("Please enter a precision scale for FIXED_PRECISION overlay mode.");
      return;
    }
    transformName = wTransformName.getText();
    OperationDescriptor descriptor = currentDescriptor();
    input.setOperationId(descriptor.id());
    input.setPrimaryGeometryFieldName(wPrimaryGeometryField.getText());
    input.setSecondaryGeometryFieldName(wSecondaryGeometryField.getText());
    input.setDistanceMode(DistanceMode.valueOf(defaultText(wDistanceMode.getText(), DistanceMode.STATIC.name())));
    input.setDistanceValue(wDistanceValue.getText());
    input.setDistanceFieldName(wDistanceField.getText());
    input.setOverlayMode(OverlayMode.valueOf(defaultText(wOverlayMode.getText(), OverlayMode.STANDARD.name())));
    input.setPrecisionScale(wPrecisionScale.getText());
    input.setOutputMode(GeometryOutputMode.valueOf(defaultText(wOutputMode.getText(), GeometryOutputMode.APPEND.name())));
    input.setOutputFieldName(wOutputField.getText());
    input.setBufferSegments(parseInteger(wBufferSegments.getText(), 8));
    input.setBufferCapStyle(BufferCapStyle.valueOf(defaultText(wBufferCapStyle.getText(), BufferCapStyle.ROUND.name())));
    input.setBufferJoinStyle(BufferJoinStyle.valueOf(defaultText(wBufferJoinStyle.getText(), BufferJoinStyle.ROUND.name())));
    input.setBufferSingleSided(wBufferSingleSided.getSelection());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private OperationDescriptor currentDescriptor() {
    int selectionIndex = Math.max(0, wOperation.getSelectionIndex());
    return operations.get(selectionIndex);
  }

  private Combo addCombo(String labelText) {
    Label label = addLabel(labelText);
    Combo combo = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY | SWT.BORDER);
    combo.setLayoutData(defaultGridData());
    combo.setData("label", label);
    PropsUi.setLook(combo);
    return combo;
  }

  private Text addReadOnlyText(String labelText) {
    Label label = addLabel(labelText);
    Text text = new Text(content, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
    text.setLayoutData(defaultGridData());
    text.setData("label", label);
    PropsUi.setLook(text);
    return text;
  }

  private TextVar addTextVar(String labelText) {
    Label label = addLabel(labelText);
    TextVar text = new TextVar(variables, content, SWT.SINGLE | SWT.BORDER);
    text.setLayoutData(defaultGridData());
    text.setData("label", label);
    PropsUi.setLook(text);
    return text;
  }

  private Text addText(String labelText) {
    Label label = addLabel(labelText);
    Text text = new Text(content, SWT.SINGLE | SWT.BORDER);
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

  private Label addLabel(String labelText) {
    Label label = new Label(content, SWT.RIGHT);
    label.setText(labelText);
    label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    PropsUi.setLook(label);
    return label;
  }

  private Label addInfoLabel(String labelText) {
    Label label = new Label(content, SWT.WRAP);
    label.setText(labelText);
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    PropsUi.setLook(label);
    label.setVisible(false);
    ((GridData) label.getLayoutData()).exclude = true;
    return label;
  }

  private GridData defaultGridData() {
    GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    return gridData;
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private int parseInteger(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private void applySelection(Combo combo, GeometryFieldSelection selection) {
    combo.setItems(selection.fieldNames().toArray(String[]::new));
    if (selection.hasSelection()) {
      combo.setText(selection.selectedField());
    } else {
      combo.deselectAll();
      combo.clearSelection();
      combo.setText("");
    }
  }

  private void collectSelectionMessage(List<String> messages, String warning) {
    if (warning != null && !warning.isBlank() && !messages.contains(warning)) {
      messages.add(warning);
    }
  }

  private void setFieldStatus(List<String> messages) {
    String text = String.join("\n", messages);
    wFieldStatus.setText(text);
    boolean visible = !text.isBlank();
    ((GridData) wFieldStatus.getLayoutData()).exclude = !visible;
    wFieldStatus.setVisible(visible);
    content.layout(true, true);
  }

  private void showValidationWarning(String message) {
    MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    messageBox.setText("Invalid configuration");
    messageBox.setMessage(message);
    messageBox.open();
  }

  private String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    if (current.getMessage() == null || current.getMessage().isBlank()) {
      return current.getClass().getName();
    }
    return current.getClass().getSimpleName() + ": " + current.getMessage();
  }
}
