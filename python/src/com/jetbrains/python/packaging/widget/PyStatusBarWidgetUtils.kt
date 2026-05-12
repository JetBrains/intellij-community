// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.widget

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the Python context module for a status-bar widget.
 *
 * Returns the module containing [file] when possible; otherwise falls back to the project's
 * single module (or `null` when the project has zero or multiple modules).
 */
@ApiStatus.Internal
fun findPythonContextModule(project: Project, file: VirtualFile?): Module? {
  if (file != null) {
    val module = ModuleUtil.findModuleForFile(file, project)
    if (module != null) return module
  }
  return ModuleManager.getInstance(project).modules.singleOrNull()
}

/**
 * Resolves the Python context for a status-bar widget: the contextual [Module] and its [Sdk] if any.
 *
 * Returns `null` when no Python context module can be resolved for [file] (widgets typically hide in this case).
 * Returns `(module, null)` when a module is resolved but it has no Python SDK configured.
 */
@ApiStatus.Internal
fun resolvePythonWidgetContext(project: Project, file: VirtualFile?): Pair<Module, Sdk?>? {
  val module = findPythonContextModule(project, file) ?: return null
  return module to PythonSdkUtil.findPythonSdk(module)
}
