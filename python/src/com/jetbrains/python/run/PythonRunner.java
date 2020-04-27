// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;

public class PythonRunner implements ProgramRunner<RunnerSettings> {
  @Override
  @NotNull
  public String getRunnerId() {
    return "PythonRunner";
  }

  @Override
  public final void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, state -> {
      return doExecute(state, environment);
    });
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof AbstractPythonRunConfiguration;
  }

  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    RunProfile profile = env.getRunProfile();
    if (state instanceof PythonCommandLineState && profile instanceof CommandLinePatcher) {
      executionResult = ((PythonCommandLineState)state).execute(env.getExecutor(), (CommandLinePatcher)profile);
    }
    else {
      executionResult = state.execute(env.getExecutor(), this);
    }
    return DefaultProgramRunnerKt.showRunContent(executionResult, env);
  }
}
