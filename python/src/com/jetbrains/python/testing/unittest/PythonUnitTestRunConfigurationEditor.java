package com.jetbrains.python.testing.unittest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationEditor extends SettingsEditor<PythonUnitTestRunConfiguration> {
  private PythonUnitTestRunConfigurationForm myForm;

  public PythonUnitTestRunConfigurationEditor(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myForm = new PythonUnitTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonUnitTestRunConfiguration config) {
    PythonUnitTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonUnitTestRunConfiguration config) throws ConfigurationException {
    PythonUnitTestRunConfiguration.copyParams(myForm, config);
  }

  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
