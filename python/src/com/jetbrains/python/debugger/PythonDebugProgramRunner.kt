// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.annotations.ApiStatus

/**
 * Stable platform program runner for Python debug configurations.
 *
 * The runner cached in [ExecutionEnvironment] stays independent of the selected Python debugger backend. Actual execution
 * is delegated to [PyDebugBackendRunner] implementations, so rerun resolves the backend from current settings.
 */
@ApiStatus.Internal
class PythonDebugProgramRunner : PyDebugRunner() {
  override fun canRun(executorId: String, profile: RunProfile): Boolean = canRunPythonDebug(executorId, profile)

  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment) {
    val backendRunner = findPyDebugBackendRunner(environment.executor.id, environment.runProfile)
                        ?: throw ExecutionException(ExecutionBundle.message("dialog.message.cannot.find.runner",
                                                                            environment.runProfile.name))
    backendRunner.execute(environment)
  }
}
