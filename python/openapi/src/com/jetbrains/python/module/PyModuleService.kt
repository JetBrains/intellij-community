// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyModuleService {
  /**
   * Returns the Python SDK stored on this module, or `null` if none is set.
   *
   * Only for the code that the project structure depends on: the language level pusher, through
   * `PythonSdkUtil.readStoredPythonSdk`, and a settings page that compares the stored value with what it wrote. Every
   * other caller reads the interpreter of the Python project.
   *
   * **Startup caveat:** may return `null` when a Python SDK *is* configured but hasn't
   * resolved yet (e.g., the SDK table is still loading from a stale workspace model cache).
   */
  fun findPythonSdk(module: Module): Sdk?

  @ApiStatus.Internal
  fun forAllFacets(module: Module, facetConsumer: Consumer<Any>)

  /**
   * Whether [module] is a Python project: of the Python type, with the Python facet, any module with a content root in
   * PyCharm, or a module that names an interpreter. It is the rule of `PyProject.asPyProject`, read from the current
   * workspace model, so a module of GoLand with only an interpreter is one too.
   */
  @ApiStatus.Internal
  fun isPythonModule(module: Module): Boolean

  /**
   * Do not use it directly. Use `PyProject.setPythonInterpreter`.
   */
  @ApiStatus.Internal
  @RequiresWriteLock
  @PyInternalExecApi
  fun setPythonSdk(module: Module, sdk: Sdk?)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyModuleService = project.service<PyModuleService>()
  }
}
