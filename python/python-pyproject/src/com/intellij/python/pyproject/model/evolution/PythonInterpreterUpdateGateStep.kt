// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdateStep
import com.intellij.python.sdk.backend.ensureSkeletonsRoot
import com.intellij.python.sdk.backend.isDisposed
import com.jetbrains.python.errorProcessing.PyError

/**
 * Stops the update when it may not run: the project or the interpreter is gone, or a background update asks for an
 * interpreter that no Python project uses, see PY-88315. It runs before the probe, and makes the skeletons root: the
 * roots follow the facts that the probe writes, so the root must exist by then.
 */
internal class PythonInterpreterUpdateGateStep : PythonInterpreterUpdateStep {
  override val awaited: Boolean get() = true

  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    if (project.isDisposed || interpreter.isDisposed) return false
    if (request.background && interpreter !in EvoPyProjectModel.getInstance(project).snapshot().interpreters) {
      logSkipped(project, interpreter.toString())
      return false
    }
    interpreter.ensureSkeletonsRoot()
    return true
  }

  /**
   * A background request for an interpreter that the current snapshot shows as unused is rejected: skeleton generation
   * and a package scan would start it for nothing. Before the first snapshot it is accepted, and [update] checks again.
   */
  override fun accepts(project: Project, key: PythonInterpreterKey, request: PythonInterpreterUpdateRequest): Boolean {
    if (!request.background) return true
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return true
    if (snapshot.interpreters.any { it.ownerDir == key.ownerDir && it.ref == key.ref }) return true
    logSkipped(project, key.toString())
    return false
  }

  private fun logSkipped(project: Project, interpreter: String) {
    logger<PythonInterpreterUpdateGateStep>().info("Skipping the background update of $interpreter: no Python project of '${project.name}' uses it")
  }
}
