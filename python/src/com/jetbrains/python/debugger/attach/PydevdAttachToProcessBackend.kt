// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.debugger.PyDebuggerBackend

/**
 * Attaches over pydevd, as the action always has. This is the fallback backend, so it takes every process.
 */
internal class PydevdAttachToProcessBackend : PyAttachToProcessBackend {
  override val backend: PyDebuggerBackend = PyDebuggerBackend.PYDEVD

  override fun isApplicable(project: Project, adapterInterpreter: PythonInterpreter?): Boolean = true

  override fun attach(project: Project, target: PyAttachToProcessTarget) {
    PyAttachToProcessDebugRunner(project, target.pid, target.processInterpreter).launch()
  }
}
