// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.doctest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class PythonDocTestRunConfigurationEditor extends SettingsEditor<PythonDocTestRunConfiguration> {
  private PythonDocTestRunConfigurationForm myForm;

  public PythonDocTestRunConfigurationEditor(final Project project, final PythonDocTestRunConfiguration configuration) {
    myForm = new PythonDocTestRunConfigurationForm(project, configuration);
  }

  @Override
  protected void resetEditorFrom(final @NotNull PythonDocTestRunConfiguration config) {
    PythonDocTestRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(final @NotNull PythonDocTestRunConfiguration config) throws ConfigurationException {
    PythonDocTestRunConfiguration.copyParams(myForm, config);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myForm.getPanel();
  }

  @Override
  protected void disposeEditor() {
    myForm.removeListeners();
    myForm = null;
  }
}
