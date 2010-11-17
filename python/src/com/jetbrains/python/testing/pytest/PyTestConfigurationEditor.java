package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Ref;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import com.jetbrains.python.run.PythonRunConfigurationFormUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;

/**
 * @author yole
 */
public class PyTestConfigurationEditor extends SettingsEditor<PyTestRunConfiguration> {
  private JPanel myMainPanel;
  private JPanel myCommonOptionsPlaceholder;
  private JTextField myKeywordsTextField;
  private LabeledComponent myTestScriptComponent;
  private LabeledComponent myKeywordsComponent;
  private TextFieldWithBrowseButton myTestScriptTextField;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private final Project myProject;

  public PyTestConfigurationEditor(final Project project, PyTestRunConfiguration configuration) {
    myProject = project;
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration);
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel());
  }

  protected void resetEditorFrom(PyTestRunConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myCommonOptionsForm);
    myKeywordsTextField.setText(s.getKeywords());
    myTestScriptTextField.setText(toSystemIndependentName(s.getTestToRun()));
  }

  protected void applyEditorTo(PyTestRunConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, s);
    s.setTestToRun(toSystemIndependentName(myTestScriptTextField.getText().trim()));
    s.setKeywords(myKeywordsTextField.getText().trim());
  }

  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }

  protected void disposeEditor() {
  }

  private LabeledComponent createKeyWordsComponent() {
    myKeywordsTextField = new JTextField();

    LabeledComponent<JTextField> myComponent = new LabeledComponent<JTextField>();
    myComponent.setComponent(myKeywordsTextField);
    myComponent.setText(PyBundle.message("runcfg.unittest.dlg.keywords"));

    return myComponent;
  }

  public static LabeledComponent<TextFieldWithBrowseButton> createScriptPathComponent(final Ref<TextFieldWithBrowseButton> testScriptTextFieldWrapper,
                                                                                      final String text) {
    final TextFieldWithBrowseButton testScriptTextField = new TextFieldWithBrowseButton();
    testScriptTextFieldWrapper.set(testScriptTextField);

    LabeledComponent<TextFieldWithBrowseButton> myComponent = new LabeledComponent<TextFieldWithBrowseButton>();
    myComponent.setComponent(testScriptTextField);
    myComponent.setText(text);

    return myComponent;
  }

  private void createUIComponents() {
    myKeywordsComponent = createKeyWordsComponent();
    final Ref<TextFieldWithBrowseButton> testScriptTextFieldWrapper = new Ref<TextFieldWithBrowseButton>();
    myTestScriptComponent = createScriptPathComponent(testScriptTextFieldWrapper, PyBundle.message("runcfg.unittest.dlg.folder_path"));
    myTestScriptTextField = testScriptTextFieldWrapper.get();
    String title = PyBundle.message("runcfg.unittest.dlg.select.script.path");
    PythonRunConfigurationFormUtil.addFileChooser(title, myTestScriptTextField, myProject);
  }  
}
