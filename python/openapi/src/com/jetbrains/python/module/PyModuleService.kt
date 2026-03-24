// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.Consumer
import org.jetbrains.annotations.ApiStatus

interface PyModuleService {
  /**
   * Returns the Python SDK configured for this module, or `null` if none is set.
   *
   * Unlike [findPythonSdk], this method suspends until the project model is fully loaded
   * before resolving the SDK, so it is safe to call during startup.
   */
  @ApiStatus.Internal
  suspend fun findPythonSdkWaitingForProjectModel(module: Module): Sdk?

  /**
   * Returns the Python SDK configured for this module, or `null` if none is set.
   *
   * **Startup caveat:** may return `null` when a Python SDK *is* configured but hasn't
   * resolved yet (e.g., the SDK table is still loading from a stale workspace model cache).
   * Prefer [findPythonSdkWaitingForProjectModel] in coroutine contexts.
   */
  fun findPythonSdk(module: Module): Sdk?

  @ApiStatus.Internal
  fun forAllFacets(module: Module, facetConsumer: Consumer<Any>)

  @ApiStatus.Internal
  fun isPythonModule(module: Module): Boolean

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyModuleService = project.service<PyModuleService>()
  }
}
