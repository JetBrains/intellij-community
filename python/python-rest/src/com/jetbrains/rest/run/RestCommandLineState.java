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
package com.jetbrains.rest.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonProcessRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestCommandLineState extends PythonCommandLineState {
  protected final RestRunConfiguration myConfiguration;

  public RestCommandLineState(RestRunConfiguration configuration,
                              ExecutionEnvironment env) {
    super(configuration, env);
    myConfiguration = configuration;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    ParametersList parametersList = commandLine.getParametersList();
    ParamsGroup exeOptions = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exeOptions != null;
    exeOptions.addParametersString(myConfiguration.getInterpreterOptions());

    ParamsGroup scriptParameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert scriptParameters != null;
    getRunner().addToGroup(scriptParameters, commandLine);
    final String key = getKey();
    if (key != null)
      scriptParameters.addParameter(key);
    scriptParameters.addParameter(getTask());

    final String params = myConfiguration.getParams();
    if (params != null) scriptParameters.addParametersString(params);

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getInputFile()))
      scriptParameters.addParameter(myConfiguration.getInputFile());

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile()))
      scriptParameters.addParameter(myConfiguration.getOutputFile());

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory()))
      commandLine.setWorkDirectory(myConfiguration.getWorkingDirectory());
  }

  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    final Runnable afterTask = getAfterTask();
    ProcessHandler processHandler = PythonProcessRunner.createProcess(commandLine, false);
    if (afterTask != null) {
      processHandler.addProcessListener(new ProcessAdapter() {
                                            public void processTerminated(@NotNull ProcessEvent event) {
                                              TransactionGuard.getInstance().submitTransactionLater(ApplicationManager.getApplication(), afterTask);
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

  protected abstract HelperPackage getRunner();

  protected abstract String getTask();

  @Nullable
  protected abstract String getKey();
}
