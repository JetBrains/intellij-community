package com.jetbrains.python.testing.doctest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonDocTestRunConfigurationEditor extends SettingsEditor<PythonDocTestRunConfiguration> {
  private PythonDocTestRunConfigurationForm myForm;

  public PythonDocTestRunConfigurationEditor(final Project project, final PythonDocTestRunConfiguration configuration) {
    myForm = new PythonDocTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonDocTestRunConfiguration config) {
    PythonDocTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonDocTestRunConfiguration config) throws ConfigurationException {
    PythonDocTestRunConfiguration.copyParams(myForm, config);
  }

  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
