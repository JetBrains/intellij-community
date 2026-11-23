// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.getPythonInfo

/**
 * The native interpreters of the Python projects of [project] that can attach to a local process. The interpreter with
 * the highest Python version is first. The list is empty before the first snapshot.
 *
 * It reads only what each interpreter records, so it runs no process and does not suspend.
 */
internal fun localAttachInterpreters(project: Project): List<PythonInterpreter> {
  return projectInterpretersIfReady(project)
    .filter { it.isLocal() }
    .mapNotNull { interpreter -> interpreter.getPythonInfo().successOrNull?.let { interpreter to it.languageLevel } }
    .sortedByDescending { (_, languageLevel) -> languageLevel }
    .map { (interpreter, _) -> interpreter }
}

/** The interpreter of the Python projects of [project] whose binary is [binaryPath], or `null`. */
internal fun findLocalAttachInterpreter(project: Project, binaryPath: String): PythonInterpreter? {
  return projectInterpretersIfReady(project)
    .firstOrNull { it.isLocal() && it.binaryPathString == binaryPath }
}

/** The name of the attach debugger of [interpreter]: the Python version and the path of the binary. */
internal fun attachDebuggerName(interpreter: PythonInterpreter): String {
  val version = interpreter.getPythonInfo().successOrNull?.languageLevel?.toPythonVersion()
  return if (version != null) "Python $version (${interpreter.binaryPathString})" else interpreter.binaryPathString
}

/** The interpreters that the Python projects of the last snapshot own. Empty before the first snapshot. */
private fun projectInterpretersIfReady(project: Project): List<PythonInterpreter> {
  val registry = PythonInterpreterRegistry.getInstance(project)
  val evoPyProjects = EvoPyProjectModel.getInstance(project).snapshotOrNull()?.evoPyProjects ?: return emptyList()
  return evoPyProjects.flatMap { registry.interpretersIfReady(it.pyProject) }.distinct().toList()
}

private fun PythonInterpreter.isLocal(): Boolean = when (this) {
  is NativePythonInterpreter -> true
  is TargetPythonInterpreter -> false
}
