// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * @author Leonid Shalupov
 */
public class PythonRunConfigurationEditor extends SettingsEditor<PythonRunConfiguration> {
  private PythonRunConfigurationForm myForm;

  public PythonRunConfigurationEditor(final PythonRunConfiguration configuration) {
    myForm = new PythonRunConfigurationForm(configuration);
  }

  @Override
  protected void resetEditorFrom(final @NotNull PythonRunConfiguration config) {
    PythonRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(final @NotNull PythonRunConfiguration config) throws ConfigurationException {
    PythonRunConfiguration.copyParams(myForm, config);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myForm.getPanel();
  }

  @Override
  protected void disposeEditor() {
    myForm = null;
  }
}
