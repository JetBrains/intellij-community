// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonDocTestRunConfigurationEditor extends SettingsEditor<PythonDocTestRunConfiguration> {
  private PythonDocTestRunConfigurationForm myForm;

  public PythonDocTestRunConfigurationEditor(final Project project, final PythonDocTestRunConfiguration configuration) {
    myForm = new PythonDocTestRunConfigurationForm(project, configuration);
  }

  @Override
  protected void resetEditorFrom(@NotNull final PythonDocTestRunConfiguration config) {
    PythonDocTestRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(@NotNull final PythonDocTestRunConfiguration config) throws ConfigurationException {
    PythonDocTestRunConfiguration.copyParams(myForm, config);
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
