/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.testing.doctest;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class PythonDocTestRunConfiguration extends AbstractPythonLegacyTestRunConfiguration<PythonDocTestRunConfiguration>
                                          implements PythonDocTestRunConfigurationParams {
  protected String myPluralTitle = "Doctests";
  protected String myTitle = "Doctest";
  public PythonDocTestRunConfiguration(Project project,
                                       ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected SettingsEditor<PythonDocTestRunConfiguration> createConfigurationEditor() {
    return new PythonDocTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    return new PythonDocTestCommandLineState(this, env);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public static void copyParams(PythonDocTestRunConfigurationParams source, PythonDocTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
  }
}
