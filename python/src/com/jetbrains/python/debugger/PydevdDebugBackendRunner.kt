// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.configurations.RunProfile
import org.jetbrains.annotations.ApiStatus

/**
 * Backend runner for Python debug sessions started through pydevd.
 *
 * This keeps the existing [PyDebugRunner] launch implementation behind the Python backend-runner extension point and works as the
 * fallback backend when debugpy is not applicable to the run configuration.
 */
@ApiStatus.Internal
class PydevdDebugBackendRunner : PyDebugRunner(), PyDebugBackendRunner {
  override val backend: PyDebuggerBackend = PyDebuggerBackend.PYDEVD

  override fun canRun(executorId: String, profile: RunProfile): Boolean = canRunPythonDebug(executorId, profile)
}
