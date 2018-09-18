// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.unittestLegacy;

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

  @Override
  protected void resetEditorFrom(@NotNull final PythonUnitTestRunConfiguration config) {
    PythonUnitTestRunConfiguration.copyParams(config, myForm);
  }

  @Override
  protected void applyEditorTo(@NotNull final PythonUnitTestRunConfiguration config) throws ConfigurationException {
    PythonUnitTestRunConfiguration.copyParams(myForm, config);
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
