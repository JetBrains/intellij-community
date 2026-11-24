// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach

import com.intellij.execution.ExecutionException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.debugger.PyDebuggerBackend
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider
import com.jetbrains.python.debugger.resolveEffectiveBackend
import org.jetbrains.annotations.ApiStatus

/**
 * What the Attach to Process action hands to a backend.
 *
 * @property processInterpreter the interpreter of the process, when the IDE knows it. pydevd runs its helper on it.
 * @property adapterInterpreter the interpreter of the project, which the debugpy debug adapter runs on. The process says
 * nothing about it: the process can be started anywhere, from a terminal outside the IDE included. Null when pydevd is
 * the backend from the settings, which needs none, and when the IDE has no usable interpreter.
 */
@ApiStatus.Internal
class PyAttachToProcessTarget(
  val pid: Int,
  val processInterpreter: PythonInterpreter?,
  val adapterInterpreter: PythonInterpreter?,
)

/**
 * Starts an Attach to Process debug session on one Python debugger backend.
 */
@ApiStatus.Internal
interface PyAttachToProcessBackend {
  val backend: PyDebuggerBackend

  /**
   * Whether this backend can attach when the debug adapter runs on [adapterInterpreter].
   */
  fun isApplicable(project: Project, adapterInterpreter: PythonInterpreter?): Boolean

  /**
   * Starts the debug session. The platform calls it from a UI callback of the Attach to Process popup or dialog, so on
   * the EDT, and the signature is synchronous. An implementation must therefore only schedule its heavy work.
   */
  @RequiresEdt
  @Throws(ExecutionException::class)
  fun attach(project: Project, target: PyAttachToProcessTarget)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyAttachToProcessBackend> =
      ExtensionPointName.create("Pythonid.pythonAttachToProcessBackend")

    /**
     * Returns the backend that attaches with [adapterInterpreter]. It is debugpy when the settings select it and it
     * applies, and pydevd otherwise, see [resolveEffectiveBackend]. Null when no plugin registers that backend.
     */
    @JvmStatic
    fun find(
      project: Project,
      adapterInterpreter: PythonInterpreter?,
    ): PyAttachToProcessBackend? {
      val backends = EP_NAME.extensionList
      val debugpy = backends.firstOrNull { it.backend == PyDebuggerBackend.DEBUGPY }
      val effective = resolveEffectiveBackend(PyDebuggerOptionsProvider.getInstance(project).selectedBackend,
                                              debugpy?.isApplicable(project, adapterInterpreter) == true)
      return backends.firstOrNull { it.backend == effective }
    }
  }
}
