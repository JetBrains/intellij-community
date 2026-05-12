// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.DebugAwareConfiguration
import org.jetbrains.annotations.ApiStatus

/**
 * Python-owned debug backend runner selected by [PythonDebugProgramRunner] at execution time.
 *
 * Implementations are not registered as platform program runners directly, so stopped-session rerun keeps a stable Python runner
 * and re-evaluates backend availability before each launch. Debugpy gets the first chance to run, and pydevd is used as the
 * fallback backend when debugpy is not applicable.
 */
@ApiStatus.Internal
interface PyDebugBackendRunner : ProgramRunner<RunnerSettings> {
  val backend: PyDebuggerBackend

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyDebugBackendRunner> = ExtensionPointName.create("Pythonid.pythonDebugBackendRunner")
  }
}

internal fun findPyDebugBackendRunner(executorId: String, profile: RunProfile): PyDebugBackendRunner? {
  val runners = PyDebugBackendRunner.EP_NAME.extensionList
  return runners.findBackendRunner(PyDebuggerBackend.DEBUGPY, executorId, profile)
         ?: runners.findBackendRunner(PyDebuggerBackend.PYDEVD, executorId, profile)
         ?: runners.firstOrNull { it.canRun(executorId, profile) }
}

private fun List<PyDebugBackendRunner>.findBackendRunner(
  backend: PyDebuggerBackend,
  executorId: String,
  profile: RunProfile,
): PyDebugBackendRunner? = firstOrNull { it.backend == backend && it.canRun(executorId, profile) }

internal fun canRunPythonDebug(executorId: String, profile: RunProfile): Boolean {
  if (executorId != DefaultDebugExecutor.EXECUTOR_ID) return false

  return when (val unwrappedProfile = profile.unwrapPythonRunProfile()) {
    is DebugAwareConfiguration -> unwrappedProfile.canRunUnderDebug()
    is AbstractPythonRunConfiguration<*> -> true
    else -> false
  }
}

internal fun RunProfile.unwrapPythonRunProfile(): RunProfile = (this as? WrappingRunConfiguration<*>)?.peer ?: this
