/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonProcessRunner;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;

/**
 * User : catherine
 */
public abstract class RestCommandLineState extends PythonCommandLineState {
  protected final RestRunConfiguration myConfiguration;

  public RestCommandLineState(RestRunConfiguration configuration,
                              ExecutionEnvironment env) {
    super(configuration, env, Collections.<Filter>emptyList());
    myConfiguration = configuration;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    ParametersList parametersList = commandLine.getParametersList();
    ParamsGroup exe_options = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfiguration.getInterpreterOptions());

    ParamsGroup script_parameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert script_parameters != null;
    String runner = PythonHelpersLocator.getHelperPath(getRunnerPath());
    if (runner != null )
      script_parameters.addParameter(runner);
    final String key = getKey();
    if (key != null)
      script_parameters.addParameter(key);
    script_parameters.addParameter(getTask());

    final String params = myConfiguration.getParams();
    if (params != null) script_parameters.addParametersString(params);

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getInputFile()))
      script_parameters.addParameter(myConfiguration.getInputFile());

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile()))
      script_parameters.addParameter(myConfiguration.getOutputFile());

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory()))
      commandLine.setWorkDirectory(myConfiguration.getWorkingDirectory());
  }

  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    final Runnable afterTask = getAfterTask();
    ProcessHandler processHandler = PythonProcessRunner.createProcess(commandLine);
    if (afterTask != null) {
      processHandler.addProcessListener(new ProcessAdapter() {
                                            public void processTerminated(ProcessEvent event) {
                                              SwingUtilities.invokeLater(afterTask);
                                            }});
    }
    return processHandler;
  }

  @Nullable
  protected Runnable getAfterTask() {
    return null;
  }

  @Nullable
  protected VirtualFile findOutput() {
    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile())) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
        myConfiguration.getOutputFile());
      if (virtualFile == null) {
        virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
          myConfiguration.getWorkingDirectory() + myConfiguration.getOutputFile());
      }
      return virtualFile;
    }
    return null;
  }

  protected abstract String getRunnerPath();

  protected abstract String getTask();

  @Nullable
  protected abstract String getKey();
}
