// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise

/**
 * Stable platform program runner for Python debug configurations.
 *
 * The runner cached in [ExecutionEnvironment] stays independent of the selected Python debugger backend. Actual execution
 * is delegated to [PyDebugBackendRunner] implementations, so rerun resolves the backend from current settings.
 */
@ApiStatus.Internal
class PythonDebugProgramRunner : PyDebugRunner() {
  override fun canRun(executorId: String, profile: RunProfile): Boolean = canRunPythonDebug(executorId, profile)

  /**
   * Hands the launch to the selected backend.
   *
   * [state] is the one [com.intellij.execution.runners.AsyncProgramRunner] built from this environment, and it goes
   * unused: a backend picks its own [RunProfileState], from an environment whose runner is the backend itself. That
   * choice decides whether [com.jetbrains.python.run.PythonCommandLineState] spawns the debuggee, so it belongs to
   * the backend. See [PyDebugBackendRunner.startSession].
   */
  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val backendRunner = findPyDebugBackendRunner(environment.executor.id, environment.runProfile)
                        ?: throw ExecutionException(ExecutionBundle.message("dialog.message.cannot.find.runner",
                                                                           environment.runProfile.name))
    return backendRunner.startSession(environment)
  }
}
