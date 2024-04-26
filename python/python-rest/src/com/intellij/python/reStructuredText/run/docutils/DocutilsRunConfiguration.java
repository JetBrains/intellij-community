// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.docutils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.python.reStructuredText.run.RestConfigurationEditor;
import com.intellij.python.reStructuredText.run.RestRunConfiguration;
import com.intellij.python.reStructuredText.PythonRestBundle;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class DocutilsRunConfiguration extends RestRunConfiguration {

  public DocutilsRunConfiguration(final Project project,
                                  final ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    RestConfigurationEditor editor = new RestConfigurationEditor(getProject(), this, new DocutilsTasksModel());
    editor.setConfigurationName("Docutils task");
    editor.setInputDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    editor.setOutputDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    return editor;
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new DocutilsCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (StringUtil.isEmptyOrSpaces(getInputFile()))
      throw new RuntimeConfigurationError(PythonRestBundle.message("python.rest.specify.input.file.name"));
  }

  @Override
  public String suggestedName() {
    return PythonRestBundle.message("python.rest.docutils.run.cfg.default.name", getName());
  }
}
