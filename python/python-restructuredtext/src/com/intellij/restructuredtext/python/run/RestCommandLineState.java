// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.run.*;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.run.PythonScriptCommandLineState.getExpandedWorkingDir;

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
    if (key != null) {
      scriptParameters.addParameter(key);
    }
    scriptParameters.addParameter(getTask());

    final String params = myConfiguration.getParams();
    if (params != null) scriptParameters.addParametersString(params);

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getInputFile())) {
      scriptParameters.addParameter(myConfiguration.getInputFile());
    }

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile())) {
      scriptParameters.addParameter(myConfiguration.getOutputFile());
    }

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory())) {
      commandLine.setWorkDirectory(getExpandedWorkingDir(myConfiguration));
    }
  }

  @Override
  protected @NotNull PythonExecution buildPythonExecution(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareRequest) {
    PythonScriptExecution pythonScriptExecution = PythonScripts.prepareHelperScriptExecution(getRunner(), helpersAwareRequest);
    final String key = getKey();
    if (key != null) pythonScriptExecution.addParameter(key);
    pythonScriptExecution.addParameter(getTask());

    final String params = myConfiguration.getParams();
    if (params != null) PythonScripts.addParametersString(pythonScriptExecution, params);

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getInputFile())) {
      pythonScriptExecution.addParameter(myConfiguration.getInputFile());
    }

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile())) {
      pythonScriptExecution.addParameter(myConfiguration.getOutputFile());
    }
    return pythonScriptExecution;
  }

  @Override
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    final Runnable afterTask = getAfterTask();
    ProcessHandler processHandler = PythonProcessRunner.createProcess(commandLine, false);
    if (afterTask != null) {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(afterTask);
        }
      });
    }
    return processHandler;
  }

  @Override
  protected @NotNull ProcessHandler startProcess(@NotNull PythonScriptTargetedCommandLineBuilder builder) throws ExecutionException {
    Runnable afterTask = getAfterTask();
    ProcessHandler processHandler = super.startProcess(builder);
    if (afterTask != null) {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(afterTask);
        }
      });
    }
    return processHandler;
  }

  protected @Nullable Runnable getAfterTask() {
    return null;
  }

  protected @Nullable VirtualFile findOutput() {
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

  protected abstract @Nullable String getKey();
}
