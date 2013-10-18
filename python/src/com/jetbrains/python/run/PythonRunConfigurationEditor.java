package com.jetbrains.python.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Leonid Shalupov
 */
public class PythonRunConfigurationEditor  extends SettingsEditor<PythonRunConfiguration> {
  private PythonRunConfigurationForm myForm;

  public PythonRunConfigurationEditor(final PythonRunConfiguration configuration) {
    myForm = new PythonRunConfigurationForm(configuration);
  }

  protected void resetEditorFrom(final PythonRunConfiguration config) {
    PythonRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonRunConfiguration config) throws ConfigurationException {
    PythonRunConfiguration.copyParams(myForm, config);
  }

  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
