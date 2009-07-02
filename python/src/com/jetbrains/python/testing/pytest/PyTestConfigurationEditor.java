package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.jetbrains.python.run.PyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PyTestConfigurationEditor extends SettingsEditor<PyTestRunConfiguration> {
  private JPanel myMainPanel;
  private JPanel myCommonOptionsPlaceholder;
  private JTextField myTestToRunField;
  private JTextField myKeywordsTextField;
  private PyCommonOptionsForm myCommonOptionsForm;

  public PyTestConfigurationEditor(PyTestRunConfiguration configuration) {
    myCommonOptionsForm = new PyCommonOptionsForm(configuration);
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel());
  }

  protected void resetEditorFrom(PyTestRunConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myCommonOptionsForm);
    myTestToRunField.setText(s.getTestToRun());
    myKeywordsTextField.setText(s.getKeywords());
  }

  protected void applyEditorTo(PyTestRunConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, s);
    s.setTestToRun(myTestToRunField.getText());
    s.setKeywords(myKeywordsTextField.getText());
  }

  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }

  protected void disposeEditor() {
  }
}
