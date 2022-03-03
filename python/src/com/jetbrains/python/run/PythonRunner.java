// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

public class PythonRunner extends AsyncProgramRunner<RunnerSettings> {
  @Override
  @NotNull
  public String getRunnerId() {
    return "PythonRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof AbstractPythonRunConfiguration;
  }

  /**
   * {@link PythonCommandLineState} inheritors must be ready to be called on any thread, so we can run then on the background thread.
   * Any other state must be invoked on EDT only
   */
  private static void execute(@NotNull RunProfileState profileState, @NotNull Runnable runnable) {
    if (profileState instanceof PythonCommandLineState) {
      AppExecutorUtil.getAppExecutorService().execute(runnable);
    } else {
      ApplicationManager.getApplication().invokeAndWait(runnable);
    }
  }

  @NotNull
  @Override
  protected Promise<@Nullable RunContentDescriptor> execute(@NotNull ExecutionEnvironment env, @NotNull RunProfileState state) {
    FileDocumentManager.getInstance().saveAllDocuments();

    AsyncPromise<RunContentDescriptor> promise = new AsyncPromise<>();
    execute(state, () -> {
      try {
        boolean useTargetsAPI = Registry.get("python.use.targets.api").asBoolean();

        ExecutionResult executionResult;
        RunProfile profile = env.getRunProfile();
        if (useTargetsAPI && state instanceof PythonCommandLineState) {
          // TODO [cloud-api.python] profile functionality must be applied here:
          //      - com.jetbrains.django.run.DjangoServerRunConfiguration.patchCommandLineFirst() - host:port is put in user data
          executionResult = ((PythonCommandLineState)state).execute(env.getExecutor());
        }
        else if (!useTargetsAPI && state instanceof PythonCommandLineState && profile instanceof CommandLinePatcher) {
          executionResult = ((PythonCommandLineState)state).execute(env.getExecutor(), (CommandLinePatcher)profile);
        }
        else {
          executionResult = state.execute(env.getExecutor(), this);
        }
        ApplicationManager.getApplication().invokeLater(
          () -> promise.setResult(DefaultProgramRunnerKt.showRunContent(executionResult, env)),
          ModalityState.any());
      }
      catch (ExecutionException | RuntimeException err) {
        promise.setError(err);
      }
    });
    return promise;
  }
}
