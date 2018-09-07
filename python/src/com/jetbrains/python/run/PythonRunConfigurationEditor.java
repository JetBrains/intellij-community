// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Leonid Shalupov
 */
public class PythonRunConfigurationEditor extends SettingsEditor<PythonRunConfiguration> {
  private PythonRunConfigurationForm myForm;

  public PythonRunConfigurationEditor(final PythonRunConfiguration configuration) {
    myForm = new PythonRunConfigurationForm(configuration);
  }

  @Override
  protected void resetEditorFrom(@NotNull final PythonRunConfiguration config) {
    PythonRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(@NotNull final PythonRunConfiguration config) throws ConfigurationException {
    PythonRunConfiguration.copyParams(myForm, config);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  @Override
  protected void disposeEditor() {
    myForm = null;
  }
}
