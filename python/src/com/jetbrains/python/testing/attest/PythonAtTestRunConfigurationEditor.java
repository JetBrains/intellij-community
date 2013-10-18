package com.jetbrains.python.testing.attest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonAtTestRunConfigurationEditor extends SettingsEditor<PythonAtTestRunConfiguration> {
  private PythonAtTestRunConfigurationForm myForm;

  public PythonAtTestRunConfigurationEditor(final Project project, final PythonAtTestRunConfiguration configuration) {
    myForm = new PythonAtTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonAtTestRunConfiguration config) {
    PythonAtTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonAtTestRunConfiguration config) throws ConfigurationException {
    PythonAtTestRunConfiguration.copyParams(myForm, config);
  }

  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
