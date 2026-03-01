// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

open class PythonRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "PythonRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is AbstractPythonRunConfiguration<*>
  }

  /**
   * [PythonCommandLineState] inheritors must be ready to be called on any thread, so we can run them on a background thread.
   * Any other state must be invoked on EDT only.
   */
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    if (state is PythonCommandLineState && !state.canRun()) {
      return resolvedPromise(null)
    }

    return environment.project.service<PythonRunnerCoroutineScope>().cs.async {
      writeAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
      val executionResult = if (state is PythonCommandLineState) {
        // TODO [cloud-api.python] profile functionality must be applied here:
        //      - com.jetbrains.django.run.DjangoServerRunConfiguration.patchCommandLineFirst() - host:port is put in user data
        state.execute(environment.executor)
      }
      else {
        withContext(Dispatchers.EDT) {
          state.execute(environment.executor, this@PythonRunner)
        }
      }
      withContext(Dispatchers.EDT) {
        showRunContent(executionResult, environment)
      }
    }.toPromise()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Deferred<T>.toPromise(): Promise<T> {
  val promise = AsyncPromise<T>()
  invokeOnCompletion { throwable ->
    if (throwable != null) {
      promise.setError(throwable)
    }
    else {
      promise.setResult(getCompleted())
    }
  }
  return promise
}

@Service(Service.Level.PROJECT)
private class PythonRunnerCoroutineScope(val cs: CoroutineScope)
