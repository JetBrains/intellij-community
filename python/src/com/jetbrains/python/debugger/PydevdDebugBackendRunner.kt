// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * Backend runner for Python debug sessions started through pydevd.
 *
 * This keeps the existing [PyDebugRunner] launch implementation behind the Python backend-runner extension point and works as the
 * fallback backend when debugpy is not applicable to the run configuration.
 */
@ApiStatus.Internal
class PydevdDebugBackendRunner : PyDebugRunner(), PyDebugBackendRunner {
  override val backend: PyDebuggerBackend = PyDebuggerBackend.PYDEVD

  override fun isApplicable(executorId: String, profile: RunProfile): Boolean = canRunPythonDebug(executorId, profile)

  /**
   * This backend is the [PyDebugRunner] launch itself, so it runs the state of the environment it is given: pydevd
   * spawns the debuggee through one of [com.jetbrains.python.run.PythonCommandLineState]'s process-starting
   * overloads, which is what [PyDebugRunner.execute] reaches.
   */
  override fun startSession(environment: ExecutionEnvironment): Promise<RunContentDescriptor?> {
    val state = environment.state ?: return resolvedPromise(null)
    return execute(environment, state)
  }
}
