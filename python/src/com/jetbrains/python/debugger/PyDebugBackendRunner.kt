// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.concurrency.Promise
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.DebugAwareConfiguration
import org.jetbrains.annotations.ApiStatus

/**
 * How one Python debugger backend starts a debug session. [PythonDebugProgramRunner] picks one at execution time.
 *
 * This is deliberately **not** a [com.intellij.execution.runners.ProgramRunner]: the platform registers exactly
 * one Python debug runner, so stopped-session rerun keeps a stable runner id and the backend is re-chosen from
 * current settings on every launch. `platform/dap` is built the same way — one `DapProgramRunner`, and its
 * extension point supplies launch data rather than another runner.
 *
 * Debugpy gets the first chance to run, and pydevd is the fallback when debugpy is not applicable.
 */
@ApiStatus.Internal
interface PyDebugBackendRunner {
  val backend: PyDebuggerBackend

  /**
   * Whether this backend can start [profile]. Answered per launch, never cached in the [ExecutionEnvironment].
   */
  fun isApplicable(executorId: String, profile: RunProfile): Boolean

  /**
   * Starts this backend and returns the launch.
   *
   * [PythonDebugProgramRunner] is the runner the platform registers, and as an
   * [com.intellij.execution.runners.AsyncProgramRunner] it owns the one
   * [com.intellij.execution.ExecutionManager.startRunProfile] of a launch. A backend therefore returns its
   * launch instead of opening a run profile of its own — two of them would report the launch twice.
   *
   * The facade's [com.intellij.execution.configurations.RunProfileState] is not passed in: a backend takes the
   * state from [environment] itself, because only the backend knows whether it wants the state to spawn the
   * debuggee or to leave that to a debug adapter.
   */
  fun startSession(environment: ExecutionEnvironment): Promise<RunContentDescriptor?>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyDebugBackendRunner> = ExtensionPointName.create("Pythonid.pythonDebugBackendRunner")
  }
}

/**
 * Backend used when a project has no explicit user selection (storage value is
 * [PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER] or absent from `workspace.xml`). Flipping this
 * constant in a future release will propagate to all projects without an explicit user choice;
 * explicit choices written to `workspace.xml` are preserved.
 */
@JvmField
@ApiStatus.Internal
val DEFAULT_PY_DEBUGGER_BACKEND: PyDebuggerBackend = PyDebuggerBackend.DEBUGPY

/**
 * Returns the backend that will actually be used given the user's stored preference and the
 * current debugpy availability. [PyDebuggerBackend.DEBUGPY] collapses to [PyDebuggerBackend.PYDEVD]
 * when debugpy cannot run in the project's current state; [PyDebuggerBackend.PYDEVD] always passes
 * through. The availability flag is supplied by the caller because the predicate lives in the DAP
 * plugin and cannot be referenced from this module.
 */
@ApiStatus.Internal
fun resolveEffectiveBackend(storedBackend: PyDebuggerBackend, isDebugpyAvailable: Boolean): PyDebuggerBackend =
  if (storedBackend == PyDebuggerBackend.DEBUGPY && !isDebugpyAvailable) PyDebuggerBackend.PYDEVD
  else storedBackend

internal fun findPyDebugBackendRunner(executorId: String, profile: RunProfile): PyDebugBackendRunner? {
  val runners = PyDebugBackendRunner.EP_NAME.extensionList
  return runners.findBackendRunner(PyDebuggerBackend.DEBUGPY, executorId, profile)
         ?: runners.findBackendRunner(PyDebuggerBackend.PYDEVD, executorId, profile)
         ?: runners.firstOrNull { it.isApplicable(executorId, profile) }
}

private fun List<PyDebugBackendRunner>.findBackendRunner(
  backend: PyDebuggerBackend,
  executorId: String,
  profile: RunProfile,
): PyDebugBackendRunner? = firstOrNull { it.backend == backend && it.isApplicable(executorId, profile) }

internal fun canRunPythonDebug(executorId: String, profile: RunProfile): Boolean {
  if (executorId != DefaultDebugExecutor.EXECUTOR_ID) return false

  return when (val unwrappedProfile = profile.unwrapPythonRunProfile()) {
    is DebugAwareConfiguration -> unwrappedProfile.canRunUnderDebug()
    is AbstractPythonRunConfiguration<*> -> true
    else -> false
  }
}

internal fun RunProfile.unwrapPythonRunProfile(): RunProfile = (this as? WrappingRunConfiguration<*>)?.peer ?: this
