package ch.so.agi.hop.geoprocessing.transform.coverageops;

import ch.so.agi.hop.geoprocessing.core.CoverageMergeStrategy;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.GeometryOutputMode;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.ParameterId;
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

public class CoverageOperationDialog extends BaseTransformDialog {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  private final CoverageOperationMeta input;
  private final List<OperationDescriptor> operations;

  private Combo wOperation;
  private Text wExecutionMode;
  private Combo wGeometryField;
  private Text wGroupFieldNames;
  private TextVar wGapWidth;
  private Button wDisallowCoverageHoles;
  private TextVar wDistanceValue;
  private TextVar wSnappingDistance;
  private Combo wMergeStrategy;
  private Combo wOutputMode;
  private Text wOutputField;
  private Text wBooleanField;
  private Text wErrorTypeField;
  private Label wFieldStatus;
  private Composite content;

  public CoverageOperationDialog(
      Shell parent, IVariables variables, CoverageOperationMeta transformMeta, PipelineMeta pipelineMeta) {
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
    shell.setText("Coverage Operation");
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

    addFullWidthLabel(
        "Optional reject target hop: Validate Coverage can duplicate invalid rows to a second output stream.");
    wOperation = addCombo("Operation");
    for (OperationDescriptor descriptor : operations) {
      wOperation.add(descriptor.displayLabel());
    }
    wExecutionMode = addReadOnlyText("Execution mode");
    wGeometryField = addCombo("Geometry field");
    wGroupFieldNames = addText("Group fields (CSV/semicolon)");
    wGapWidth = addTextVar("Gap width");
    wDisallowCoverageHoles = addCheck("Disallow coverage holes");
    wDistanceValue = addTextVar("Simplification tolerance");
    wSnappingDistance = addTextVar("Snapping distance");
    wMergeStrategy = addCombo("Merge strategy");
    for (CoverageMergeStrategy strategy : CoverageMergeStrategy.values()) {
      wMergeStrategy.add(strategy.name());
    }
    wOutputMode = addCombo("Output mode");
    for (GeometryOutputMode mode : GeometryOutputMode.values()) {
      wOutputMode.add(mode.name());
    }
    wOutputField = addText("Output geometry field");
    wBooleanField = addText("Boolean output field");
    wErrorTypeField = addText("Error type field");
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
    wGeometryField.addModifyListener(event -> input.setChanged());
    wGroupFieldNames.addModifyListener(event -> input.setChanged());
    wGapWidth.addModifyListener(event -> input.setChanged());
    wDisallowCoverageHoles.addListener(SWT.Selection, event -> input.setChanged());
    wDistanceValue.addModifyListener(event -> input.setChanged());
    wSnappingDistance.addModifyListener(event -> input.setChanged());
    wMergeStrategy.addModifyListener(event -> input.setChanged());
    wOutputMode.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wOutputField.addModifyListener(event -> input.setChanged());
    wBooleanField.addModifyListener(event -> input.setChanged());
    wErrorTypeField.addModifyListener(event -> input.setChanged());
  }

  private void loadFieldChoices() {
    List<String> messages = new ArrayList<>();
    wGeometryField.removeAll();
    try {
      IRowMeta prev = pipelineMeta.getPrevTransformFields(variables, transformName);
      GeometryFieldSelection geometrySelection =
          GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(prev, input.getGeometryFieldName());
      applySelection(wGeometryField, geometrySelection);
      collectSelectionMessage(messages, geometrySelection.warning());
      if (geometrySelection.fieldNames().isEmpty()) {
        messages.add("No geometry field candidates found on the input row.");
      }
    } catch (Exception e) {
      messages.add("Unable to inspect upstream fields: " + rootCauseMessage(e));
    }
    setFieldStatus(messages);
  }

  private void getData() {
    wTransformName.setText(transformName == null ? "" : transformName);
    selectOperation(input.getOperationId());
    wGapWidth.setText(defaultText(input.getGapWidth()));
    wDisallowCoverageHoles.setSelection(input.isDisallowCoverageHoles());
    wDistanceValue.setText(defaultText(input.getDistanceValue()));
    wSnappingDistance.setText(defaultText(input.getSnappingDistance()));
    wMergeStrategy.setText(input.getMergeStrategy().name());
    wOutputMode.setText(input.getOutputMode().name());
    wOutputField.setText(defaultText(input.getOutputFieldName()));
    wBooleanField.setText(defaultText(input.getBooleanFieldName()));
    wErrorTypeField.setText(defaultText(input.getErrorTypeFieldName()));
    wGroupFieldNames.setText(defaultText(input.getGroupFieldNames()));
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
    boolean validateOperation = "coverage_validate".equals(descriptor.id());
    boolean appendMode =
        GeometryOutputMode.valueOf(defaultText(wOutputMode.getText(), GeometryOutputMode.APPEND.name()))
            == GeometryOutputMode.APPEND;

    wExecutionMode.setText(descriptor.executionMode().getLabel());
    toggleControl(wGapWidth, descriptor.requires(ParameterId.GAP_WIDTH));
    toggleControl(wDisallowCoverageHoles, descriptor.requires(ParameterId.DISALLOW_HOLES));
    toggleControl(wDistanceValue, descriptor.requires(ParameterId.DISTANCE));
    toggleControl(wSnappingDistance, descriptor.requires(ParameterId.SNAPPING_DISTANCE));
    toggleControl(wMergeStrategy, descriptor.requires(ParameterId.MERGE_STRATEGY));
    toggleControl(wOutputMode, !validateOperation);
    toggleControl(wBooleanField, validateOperation);
    toggleControl(wOutputField, validateOperation || appendMode);
    toggleControl(wErrorTypeField, validateOperation);
    setControlLabel(
        wOutputField, validateOperation ? "Error field" : "Output geometry field");
    if (validateOperation) {
      if (wBooleanField.getText().isBlank()) {
        wBooleanField.setText("coverage_is_valid");
      }
      if (wOutputField.getText().isBlank()) {
        wOutputField.setText("coverage_error");
      }
      if (wErrorTypeField.getText().isBlank()) {
        wErrorTypeField.setText("coverage_error_type");
      }
    } else if (appendMode && wOutputField.getText().isBlank()) {
      wOutputField.setText("coverage_geometry");
    }
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

  private void setControlLabel(Control control, String labelText) {
    Control label = (Control) control.getData("label");
    if (label instanceof Label swtLabel) {
      swtLabel.setText(labelText);
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    if (wGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a geometry field.");
      return;
    }
    if ("coverage_validate".equals(currentDescriptor().id())) {
      if (wBooleanField.getText().isBlank()) {
        showValidationWarning("Please enter a boolean output field.");
        return;
      }
      if (wOutputField.getText().isBlank()) {
        showValidationWarning("Please enter an error geometry field.");
        return;
      }
      if (wErrorTypeField.getText().isBlank()) {
        showValidationWarning("Please enter an error type field.");
        return;
      }
    }
    if (currentDescriptor().requires(ParameterId.DISTANCE) && wDistanceValue.getText().isBlank()) {
      showValidationWarning("Please enter a non-negative simplification tolerance.");
      return;
    }
    if (!"coverage_validate".equals(currentDescriptor().id())
        && GeometryOutputMode.valueOf(defaultText(wOutputMode.getText(), GeometryOutputMode.APPEND.name()))
            == GeometryOutputMode.APPEND
        && wOutputField.getText().isBlank()) {
      showValidationWarning("Please enter an output geometry field.");
      return;
    }

    transformName = wTransformName.getText();
    input.setOperationId(currentDescriptor().id());
    input.setGeometryFieldName(wGeometryField.getText());
    input.setGroupFieldNames(wGroupFieldNames.getText());
    input.setGapWidth(wGapWidth.getText());
    input.setDisallowCoverageHoles(wDisallowCoverageHoles.getSelection());
    input.setDistanceValue(wDistanceValue.getText());
    input.setSnappingDistance(wSnappingDistance.getText());
    input.setMergeStrategy(
        CoverageMergeStrategy.valueOf(
            defaultText(wMergeStrategy.getText(), CoverageMergeStrategy.LONGEST_BORDER.name())));
    input.setOutputMode(
        GeometryOutputMode.valueOf(
            defaultText(wOutputMode.getText(), GeometryOutputMode.APPEND.name())));
    input.setOutputFieldName(wOutputField.getText());
    input.setBooleanFieldName(wBooleanField.getText());
    input.setErrorTypeFieldName(wErrorTypeField.getText());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private OperationDescriptor currentDescriptor() {
    return operations.get(Math.max(0, wOperation.getSelectionIndex()));
  }

  private void addFullWidthLabel(String labelText) {
    Label label = new Label(content, SWT.WRAP);
    label.setText(labelText);
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    PropsUi.setLook(label);
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
    return new GridData(SWT.FILL, SWT.CENTER, true, false);
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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
