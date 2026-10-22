package ch.so.agi.hop.geoprocessing.transform.geometryreproject;

import ch.so.agi.hop.geoprocessing.core.RowMetaSupport;
import ch.so.agi.hop.geoprocessing.core.reproject.ReprojectErrorMode;
import ch.so.agi.hop.geoprocessing.core.reproject.ReprojectSourceMode;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Dialog that edits only an independent draft until OK is selected. */
public class GeometryReprojectDialog extends BaseTransformDialog {
  private final GeometryReprojectMeta input;
  private GeometryReprojectDialogModel model;
  private Composite content;
  private Text name;
  private Combo geometryField;
  private Combo sourceMode;
  private TextVar sourceEpsg;
  private TextVar targetEpsg;
  private Combo outputField;
  private Button overwrite;
  private Combo errorMode;
  private Label status;
  private boolean loading;

  public GeometryReprojectDialog(Shell parent, IVariables variables,
      GeometryReprojectMeta meta, PipelineMeta pipelineMeta) {
    super(parent, variables, meta, pipelineMeta);
    input = meta;
  }

  @Override
  public String open() {
    model = GeometryReprojectDialogModel.from(input);
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    shell.setText("Geometry Reproject");
    shell.setMinimumSize(680, 490);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("OK");
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    setButtonPositions(new Button[] {ok, cancel}, margin, null);
    content = new Composite(shell, SWT.NONE);
    PropsUi.setLook(content);
    content.setLayout(new GridLayout(2, false));
    FormData fd = new FormData();
    fd.width = 740;
    fd.height = 380;
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(0, 0);
    fd.bottom = new FormAttachment(ok, -margin);
    content.setLayoutData(fd);
    label("Transform name");
    name = new Text(content, SWT.SINGLE | SWT.BORDER);
    name.setLayoutData(fill());
    PropsUi.setLook(name);
    geometryField = combo("Geometry field", true);
    sourceMode = combo("Source CRS mode", false);
    sourceMode.setItems("From geometry SRID", "Explicit EPSG override");
    sourceEpsg = epsg("Source EPSG");
    targetEpsg = epsg("Target EPSG");
    outputField = combo("Output field", true);
    label("Overwrite existing field");
    overwrite = new Button(content, SWT.CHECK);
    overwrite.setLayoutData(fill());
    PropsUi.setLook(overwrite);
    errorMode = combo("On data error", false);
    errorMode.setItems(java.util.Arrays.stream(ReprojectErrorMode.values()).map(Enum::name).toArray(String[]::new));
    Label hint = new Label(content, SWT.WRAP);
    hint.setText("X/Y order: easting/northing or longitude/latitude. "
        + "Z and M are preserved; heights are not transformed.");
    hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    PropsUi.setLook(hint);
    status = new Label(content, SWT.WRAP);
    status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    PropsUi.setLook(status);
    loading = true;
    GeometryReprojectMeta draft = model.draft();
    name.setText(text(transformName));
    geometryField.setText(text(draft.getGeometryField()));
    sourceMode.select(draft.getSourceMode() == ReprojectSourceMode.EXPLICIT_EPSG ? 1 : 0);
    sourceEpsg.setText(text(draft.getSourceEpsg()));
    targetEpsg.setText(text(draft.getTargetEpsg()));
    outputField.setText(text(draft.getOutputField()));
    overwrite.setSelection(draft.isOverwriteExistingField());
    errorMode.setText(draft.getErrorMode().name());
    try {
      var previous = pipelineMeta.getPrevTransformFields(variables, transformName);
      model.inspectInput(previous);
      if (previous != null) {
        choices(geometryField, RowMetaSupport.geometryFieldNames(previous));
        choices(outputField, RowMetaSupport.fieldNames(previous));
      }
    } catch (Exception e) {
      model.inspectionFailed(e.getMessage());
    }
    loading = false;
    geometryField.addModifyListener(e -> refresh());
    sourceMode.addModifyListener(e -> refresh());
    sourceEpsg.addModifyListener(e -> refresh());
    targetEpsg.addModifyListener(e -> refresh());
    outputField.addModifyListener(e -> refresh());
    errorMode.addModifyListener(e -> refresh());
    overwrite.addListener(SWT.Selection, e -> refresh());
    ok.addListener(SWT.Selection, e -> ok());
    cancel.addListener(SWT.Selection, e -> cancel());
    refresh();
    name.selectAll();
    name.setFocus();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel(), false);
    return transformName;
  }

  private void readDraft() {
    var draft = model.draft();
    draft.setGeometryField(geometryField.getText());
    draft.setSourceMode(sourceMode.getSelectionIndex() == 1
        ? ReprojectSourceMode.EXPLICIT_EPSG : ReprojectSourceMode.GEOMETRY_SRID);
    draft.setSourceEpsg(sourceEpsg.getText());
    draft.setTargetEpsg(targetEpsg.getText());
    draft.setOutputField(outputField.getText());
    draft.setOverwriteExistingField(overwrite.getSelection());
    draft.setErrorMode(ReprojectErrorMode.valueOf(errorMode.getText()));
  }

  private void refresh() {
    if (loading) { return; }
    readDraft();
    sourceEpsg.setEnabled(model.sourceEpsgEnabled());
    ((Label) sourceEpsg.getData("label")).setEnabled(model.sourceEpsgEnabled());
    String message = model.validate(variables);
    status.setText(message == null ? "Configuration is valid." : message);
    content.layout(true, true);
  }

  private void ok() {
    if (name.getText().isBlank()) { return; }
    readDraft();
    String validation = model.validate(variables);
    if (validation != null && !model.hasDeferredVariables(variables)) {
      MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
      box.setText("Invalid configuration");
      box.setMessage(validation);
      box.open();
      return;
    }
    model.commitTo(input);
    input.setChanged(true);
    transformName = name.getText();
    dispose();
  }

  private void cancel() {
    transformName = null;
    dispose();
  }

  private void label(String value) {
    Label label = new Label(content, SWT.RIGHT);
    label.setText(value);
    label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    PropsUi.setLook(label);
  }
  private Combo combo(String value, boolean editable) {
    label(value);
    Combo combo = new Combo(content, SWT.DROP_DOWN | SWT.BORDER | (editable ? 0 : SWT.READ_ONLY));
    combo.setLayoutData(fill());
    PropsUi.setLook(combo);
    return combo;
  }
  private TextVar epsg(String value) {
    label(value);
    Control label = content.getChildren()[content.getChildren().length - 1];
    TextVar widget = new TextVar(variables, content, SWT.SINGLE | SWT.BORDER);
    widget.setLayoutData(fill());
    widget.setData("label", label);
    PropsUi.setLook(widget);
    return widget;
  }
  private GridData fill() { return new GridData(SWT.FILL, SWT.CENTER, true, false); }
  private String text(String value) { return value == null ? "" : value; }
  private void choices(Combo widget, java.util.List<String> names) {
    String current = widget.getText();
    widget.setItems(names.toArray(String[]::new));
    widget.setText(current);
  }
}
