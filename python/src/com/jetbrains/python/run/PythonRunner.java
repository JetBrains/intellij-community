// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.Callable;

public class PythonRunner extends AsyncProgramRunner<RunnerSettings> {
  @Override
  public @NotNull String getRunnerId() {
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
  private static <R> CancellablePromise<R> execute(@NotNull RunProfileState profileState, @NotNull Callable<R> supplier) {
    if (profileState instanceof PythonCommandLineState) {
      return executeInBgt(supplier);
    }
    else {
      return executeInEdt(supplier);
    }
  }

  @Override
  protected @NotNull Promise<@Nullable RunContentDescriptor> execute(@NotNull ExecutionEnvironment env, @NotNull RunProfileState state) {
    // aborts the execution of the run configuration if `.canRun` returns false
    // this is used for cases in which a user action prevents the execution; for example,
    // a warning dialog could be displayed to the user asking them if they wish to proceed with
    // running the configuration
    if (state instanceof PythonCommandLineState pythonState && !pythonState.canRun()) {
      return Promises.resolvedPromise(null);
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    return execute(state, () -> {
      ExecutionResult executionResult;
      if (state instanceof PythonCommandLineState) {
        // TODO [cloud-api.python] profile functionality must be applied here:
        //      - com.jetbrains.django.run.DjangoServerRunConfiguration.patchCommandLineFirst() - host:port is put in user data
        executionResult = ((PythonCommandLineState)state).execute(env.getExecutor());
      }
      else {
        executionResult = state.execute(env.getExecutor(), this);
      }
      return executionResult;
    }).thenAsync((ExecutionResult executionResult) -> {
      return executeInEdt(() -> DefaultProgramRunnerKt.showRunContent(executionResult, env));
    });
  }

  private static <V> CancellablePromise<V> executeInEdt(Callable<V> callable) {
    return AppUIExecutor.onUiThread().submit(callable);
  }

  private static  <V> CancellablePromise<V> executeInBgt(Callable<V> callable) {
    AsyncPromise<V> promise = new AsyncPromise<>();
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      try {
        V result = callable.call();
        promise.setResult(result);
      }
      catch (Exception e) {
        promise.setError(e);
      }
    });
    return promise;
  }
}
