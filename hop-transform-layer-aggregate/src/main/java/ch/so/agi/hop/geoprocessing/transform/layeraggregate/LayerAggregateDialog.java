package ch.so.agi.hop.geoprocessing.transform.layeraggregate;

import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class LayerAggregateDialog extends BaseTransformDialog {

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  private final LayerAggregateMeta input;
  private final List<OperationDescriptor> operations;

  private Combo wOperation;
  private Text wExecutionMode;
  private Combo wGeometryField;
  private Text wGroupFieldNames;
  private Text wOutputFieldName;
  private Label wFieldStatus;
  private Composite content;

  public LayerAggregateDialog(
      Shell parent, IVariables variables, LayerAggregateMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
    this.operations = input.listOperations();
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    shell.setMinimumSize(740, 520);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("Layer Aggregate");
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

    wOperation = addCombo(content, "Operation");
    for (OperationDescriptor descriptor : operations) {
      wOperation.add(descriptor.group().getLabel() + " - " + descriptor.label());
    }
    wExecutionMode = addReadOnlyText(content, "Execution mode");
    wGeometryField = addCombo(content, "Geometry field");
    wGroupFieldNames = addText(content, "Group fields (CSV/semicolon)");
    wOutputFieldName = addText(content, "Output geometry field");
    wFieldStatus = addInfoLabel(content, "");

    loadFieldChoices();
    getData();

    wTransformName.addModifyListener(event -> input.setChanged());
    wOperation.addModifyListener(event -> input.setChanged());
    wGeometryField.addModifyListener(event -> input.setChanged());
    wGroupFieldNames.addModifyListener(event -> input.setChanged());
    wOutputFieldName.addModifyListener(event -> input.setChanged());

    wOk.addListener(SWT.Selection, event -> ok());
    wCancel.addListener(SWT.Selection, event -> cancel());

    BaseDialog.defaultShellHandling(shell, value -> ok(), value -> cancel());
    return transformName;
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
    wExecutionMode.setText(input.descriptor().executionMode().getLabel());
    wGroupFieldNames.setText(defaultText(input.getGroupFieldNames()));
    wOutputFieldName.setText(defaultText(input.getOutputFieldName()));
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

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    if (wGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a geometry field.");
      return;
    }
    transformName = wTransformName.getText();
    input.setOperationId(operations.get(Math.max(0, wOperation.getSelectionIndex())).id());
    input.setGeometryFieldName(wGeometryField.getText());
    input.setGroupFieldNames(wGroupFieldNames.getText());
    input.setOutputFieldName(wOutputFieldName.getText());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private Combo addCombo(Composite parent, String labelText) {
    addLabel(parent, labelText);
    Combo combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY | SWT.BORDER);
    combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    PropsUi.setLook(combo);
    return combo;
  }

  private Text addText(Composite parent, String labelText) {
    addLabel(parent, labelText);
    Text text = new Text(parent, SWT.SINGLE | SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    PropsUi.setLook(text);
    return text;
  }

  private Text addReadOnlyText(Composite parent, String labelText) {
    addLabel(parent, labelText);
    Text text = new Text(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    PropsUi.setLook(text);
    return text;
  }

  private Label addInfoLabel(Composite parent, String text) {
    Label label = new Label(parent, SWT.WRAP);
    label.setText(text);
    GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    label.setLayoutData(gridData);
    PropsUi.setLook(label);
    label.setVisible(false);
    gridData.exclude = true;
    return label;
  }

  private void addLabel(Composite parent, String labelText) {
    Label label = new Label(parent, SWT.RIGHT);
    label.setText(labelText);
    label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    PropsUi.setLook(label);
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
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
