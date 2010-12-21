package com.jetbrains.python.testing.nosetest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfigurationEditor extends SettingsEditor<PythonNoseTestRunConfiguration> {
  private PythonNoseTestRunConfigurationForm myForm;

  public PythonNoseTestRunConfigurationEditor(final Project project, final PythonNoseTestRunConfiguration configuration) {
    myForm = new PythonNoseTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonNoseTestRunConfiguration config) {
    PythonNoseTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonNoseTestRunConfiguration config) throws ConfigurationException {
    PythonNoseTestRunConfiguration.copyParams(myForm, config);
  }

  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
