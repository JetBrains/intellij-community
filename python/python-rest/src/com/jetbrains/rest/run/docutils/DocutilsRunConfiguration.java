/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest.run.docutils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.rest.PythonRestBundle;
import com.jetbrains.rest.run.RestConfigurationEditor;
import com.jetbrains.rest.run.RestRunConfiguration;
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
