package ch.so.agi.hop.geoprocessing.transform.spatialpredicate;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.EditorKind;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import ch.so.agi.hop.geoprocessing.core.DistanceMode;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelection;
import ch.so.agi.hop.geoprocessing.core.GeometryFieldSelectionResolver;
import ch.so.agi.hop.geoprocessing.core.OperationDescriptor;
import ch.so.agi.hop.geoprocessing.core.ParameterId;
import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.SpatialPredicateResultMode;
import ch.so.agi.hop.geoprocessing.core.TransformStreamRouting;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class SpatialPredicateDialog extends BaseTransformDialog {

  private static final String MISSING_INFO =
      "Connect both producers, then choose the secondary producer in 'Secondary info transform'.";

  private static final GeometryFieldSelectionResolver GEOMETRY_FIELD_SELECTION_RESOLVER =
      new GeometryFieldSelectionResolver();

  private final SpatialPredicateMeta input;
  private final List<OperationDescriptor> operations;

  private Combo wOperation;
  private Text wExecutionMode;
  private Combo wInfoTransformName;
  private Combo wPrimaryGeometryField;
  private Combo wSecondaryGeometryField;
  private ValueOrFieldControl wDistance;
  private Combo wResultMode;
  private Text wBooleanField;
  private Text wFieldPrefix;
  private Label wFieldStatus;
  private Composite content;

  public SpatialPredicateDialog(
      Shell parent, IVariables variables, SpatialPredicateMeta transformMeta, PipelineMeta pipelineMeta) {
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
    shell.setText("Spatial Predicate");
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

    addFullWidthLabel(MISSING_INFO);
    addFullWidthLabel(
        "Optional reject target hop: KEEP_MATCHED and KEEP_UNMATCHED can send the opposite set to a second output stream.");
    wOperation = addCombo("Operation");
    for (OperationDescriptor descriptor : operations) {
      wOperation.add(descriptor.displayLabel());
    }
    wExecutionMode = addReadOnlyText("Execution mode");
    wInfoTransformName = addCombo("Secondary info transform");
    wPrimaryGeometryField = addCombo("Primary geometry field");
    wSecondaryGeometryField = addCombo("Secondary geometry field");
    Label distanceLabel = addLabel("Distance");
    wDistance = ValueOrFieldControl.builder(content, variables)
        .editor(EditorKind.TEXT)
        .fieldProvider(this::distanceFieldNames)
        .onChange(input::setChanged)
        .build();
    wDistance.setLayoutData(defaultGridData());
    wDistance.setData("label", distanceLabel);
    wResultMode = addCombo("Result mode");
    for (SpatialPredicateResultMode mode : SpatialPredicateResultMode.values()) {
      wResultMode.add(mode.name());
    }
    wBooleanField = addText("Boolean output field");
    wFieldPrefix = addText("Join field prefix");
    wFieldStatus = addInfoLabel("");

    getData();
    loadFieldChoices(
        input.getPrimaryGeometryFieldName(), input.getSecondaryGeometryFieldName());
    refreshVisibility();
    input.setChanged(changed);
    attachListeners();

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
    wInfoTransformName.addListener(
        SWT.Selection,
        event -> {
          input.setChanged();
          loadFieldChoices(
              wPrimaryGeometryField.getText(), wSecondaryGeometryField.getText());
        });
    wPrimaryGeometryField.addModifyListener(event -> input.setChanged());
    wSecondaryGeometryField.addModifyListener(event -> input.setChanged());
    wResultMode.addModifyListener(
        event -> {
          input.setChanged();
          refreshVisibility();
        });
    wBooleanField.addModifyListener(event -> input.setChanged());
    wFieldPrefix.addModifyListener(event -> input.setChanged());
  }

  private void loadFieldChoices(String primaryField, String secondaryField) {
    List<String> messages = new ArrayList<>();
    wPrimaryGeometryField.removeAll();
    wSecondaryGeometryField.removeAll();
    pipelineMeta.clearCaches();
    TransformMeta target = pipelineMeta.findTransform(transformName);
    TransformMeta source = selectedInfoSource(target);
    try {
      IRowMeta prev = TransformStreamRouting.primaryFields(pipelineMeta, variables, target, source);
      GeometryFieldSelection primarySelection =
          GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(prev, primaryField);
      applySelection(wPrimaryGeometryField, primarySelection);
      collectSelectionMessage(messages, primarySelection.warning());
      if (primarySelection.fieldNames().isEmpty()) {
        messages.add("No geometry field candidates found on the primary input.");
      }
    } catch (Exception e) {
      messages.add("Unable to inspect primary input fields: " + rootCauseMessage(e));
    }

    if (source != null) {
      try {
        IRowMeta infoRowMeta = pipelineMeta.getTransformFields(variables, source, target, null);
        GeometryFieldSelection secondarySelection =
            GEOMETRY_FIELD_SELECTION_RESOLVER.resolve(infoRowMeta, secondaryField);
        applySelection(wSecondaryGeometryField, secondarySelection);
        collectSelectionMessage(messages, secondarySelection.warning());
        if (secondarySelection.fieldNames().isEmpty()) {
          messages.add("No geometry field candidates found on the secondary info stream.");
        }
      } catch (Exception e) {
        messages.add("Unable to inspect secondary info fields: " + rootCauseMessage(e));
      }
    } else {
      messages.add("No secondary info transform is assigned yet.");
    }
    wDistance.refreshFields();
    setFieldStatus(messages);
  }

  private String[] distanceFieldNames() throws HopTransformException {
    TransformMeta target = pipelineMeta.findTransform(transformName);
    TransformMeta source = selectedInfoSource(target);
    if (source == null) {
      return new String[0];
    }
    IRowMeta primary = TransformStreamRouting.primaryFields(pipelineMeta, variables, target, source);
    return RowMetaSupport.numericFieldNames(primary).toArray(String[]::new);
  }

  private void getData() {
    wTransformName.setText(transformName == null ? "" : transformName);
    selectOperation(input.getOperationId());
    wExecutionMode.setText(input.descriptor().executionMode().getLabel());
    List<TransformMeta> sources = TransformStreamRouting.infoSources(pipelineMeta, pipelineMeta.findTransform(transformName));
    wInfoTransformName.add("(Select a producer)");
    int selection = 0;
    TransformMeta assigned = input.getTransformIOMeta().getInfoStreams().get(0).getTransformMeta();
    for (TransformMeta source : sources) {
      wInfoTransformName.add(source.getName());
      if (source == assigned) {
        selection = wInfoTransformName.getItemCount() - 1;
      }
    }
    wInfoTransformName.select(selection);
    wDistance.setValue(new ValueOrField(
        input.getDistanceMode() == DistanceMode.FIELD ? SourceMode.FIELD : SourceMode.CONFIGURED,
        defaultText(input.getDistanceValue()),
        defaultText(input.getDistanceFieldName())));
    wResultMode.setText((input.getResultMode() == null ? SpatialPredicateResultMode.BOOLEAN_COLUMN : input.getResultMode()).name());
    wBooleanField.setText(defaultText(input.getBooleanFieldName()));
    wFieldPrefix.setText(defaultText(input.getFieldPrefix()));
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private TransformMeta selectedInfoSource(TransformMeta target) {
    if (wInfoTransformName.getSelectionIndex() <= 0) {
      return null;
    }
    return TransformStreamRouting.infoSources(pipelineMeta, target).stream()
        .filter(source -> source.getName().equals(wInfoTransformName.getText()))
        .findFirst()
        .orElse(null);
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
    boolean needsDistance = descriptor.requires(ParameterId.DISTANCE);
    SpatialPredicateResultMode currentResultMode =
        SpatialPredicateResultMode.valueOf(
            defaultText(wResultMode.getText(), SpatialPredicateResultMode.BOOLEAN_COLUMN.name()));

    toggleControl(wDistance, needsDistance);
    toggleControl(wBooleanField, currentResultMode == SpatialPredicateResultMode.BOOLEAN_COLUMN);
    toggleControl(wFieldPrefix, currentResultMode == SpatialPredicateResultMode.INNER_JOIN);
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
    if (wTransformName.getText().isBlank()) {
      showValidationWarning("Please enter a transform name.");
      return;
    }
    TransformMeta source = selectedInfoSource(pipelineMeta.findTransform(transformName));
    if (source == null) {
      showValidationWarning(MISSING_INFO);
      return;
    }
    if (wPrimaryGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a primary geometry field.");
      return;
    }
    if (wSecondaryGeometryField.getText().isBlank()) {
      showValidationWarning("Please select a secondary geometry field.");
      return;
    }
    ValueOrField distance = wDistance.getValue();
    if (currentDescriptor().requires(ParameterId.DISTANCE)) {
      if (distance.mode() == SourceMode.FIELD && distance.fieldName().isBlank()) {
        showValidationWarning("Please select a numeric distance field.");
        return;
      }
      if (distance.mode() == SourceMode.CONFIGURED && !validConfiguredDistance(distance.configuredValue())) {
        showValidationWarning("Please enter a finite nonnegative distance or variable.");
        return;
      }
    }
    var stream = input.getTransformIOMeta().getInfoStreams().get(0);
    stream.setTransformMeta(source);
    input.handleStreamSelection(stream);
    transformName = wTransformName.getText();
    input.setOperationId(currentDescriptor().id());
    input.setPrimaryGeometryFieldName(wPrimaryGeometryField.getText());
    input.setSecondaryGeometryFieldName(wSecondaryGeometryField.getText());
    input.setDistanceMode(distance.mode() == SourceMode.FIELD ? DistanceMode.FIELD : DistanceMode.STATIC);
    input.setDistanceValue(distance.configuredValue());
    input.setDistanceFieldName(distance.fieldName());
    input.setResultMode(
        SpatialPredicateResultMode.valueOf(
            defaultText(wResultMode.getText(), SpatialPredicateResultMode.BOOLEAN_COLUMN.name())));
    input.setBooleanFieldName(wBooleanField.getText());
    input.setFieldPrefix(wFieldPrefix.getText());
    dispose();
  }

  private boolean validConfiguredDistance(String value) {
    List<String> variableNames = new ArrayList<>();
    StringUtil.getUsedVariables(value, variableNames, true);
    if (!variableNames.isEmpty()) {
      return true;
    }
    try {
      double distance = Double.parseDouble(value);
      return Double.isFinite(distance) && distance >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
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

  private void addFullWidthLabel(String labelText) {
    Label label = new Label(content, SWT.WRAP);
    label.setText(labelText);
    GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    label.setLayoutData(gridData);
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

  private Text addText(String labelText) {
    Label label = addLabel(labelText);
    Text text = new Text(content, SWT.SINGLE | SWT.BORDER);
    text.setLayoutData(defaultGridData());
    text.setData("label", label);
    PropsUi.setLook(text);
    return text;
  }

  private Text addReadOnlyText(String labelText) {
    Label label = addLabel(labelText);
    Text text = new Text(content, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
    text.setLayoutData(defaultGridData());
    text.setData("label", label);
    PropsUi.setLook(text);
    return text;
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
    GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    label.setLayoutData(gridData);
    PropsUi.setLook(label);
    label.setVisible(false);
    gridData.exclude = true;
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
