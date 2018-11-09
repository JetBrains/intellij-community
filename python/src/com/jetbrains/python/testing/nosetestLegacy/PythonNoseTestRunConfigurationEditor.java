// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonNoseTestRunConfigurationEditor extends SettingsEditor<PythonNoseTestRunConfiguration> {
  private PythonNoseTestRunConfigurationForm myForm;

  public PythonNoseTestRunConfigurationEditor(final Project project, final PythonNoseTestRunConfiguration configuration) {
    myForm = new PythonNoseTestRunConfigurationForm(project, configuration);
  }

  @Override
  protected void resetEditorFrom(@NotNull final PythonNoseTestRunConfiguration config) {
    PythonNoseTestRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(@NotNull final PythonNoseTestRunConfiguration config) throws ConfigurationException {
    PythonNoseTestRunConfiguration.copyParams(myForm, config);
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
