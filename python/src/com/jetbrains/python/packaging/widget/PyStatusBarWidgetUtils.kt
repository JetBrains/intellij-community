// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.widget

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the Python context module for a status-bar widget.
 *
 * Returns the module containing [file] when possible; otherwise falls back to the module of the main Python project
 * (or `null` when the project has none, or before the first snapshot).
 */
@ApiStatus.Internal
fun findPythonContextModule(project: Project, file: VirtualFile?): Module? {
  if (file != null) {
    val module = ModuleUtil.findModuleForFile(file, project)
    if (module != null) return module
  }
  return project.findMainEvoPyProjectIfReady()?.pyProject?.residesOnModule
}

/**
 * Resolves the Python context for a status-bar widget: the contextual [Module] and the interpreter of its Python
 * project, read from the snapshot.
 *
 * Returns `null` when no Python context module can be resolved for [file] (widgets typically hide in this case).
 * Returns `(module, null)` when a module is resolved but its Python project has no interpreter, or before the first
 * snapshot.
 */
@ApiStatus.Internal
fun resolvePythonWidgetContext(project: Project, file: VirtualFile?): Pair<Module, PythonInterpreter?>? {
  val module = findPythonContextModule(project, file) ?: return null
  return module to module.findEvoPyProjectIfReady()?.interpreter
}
