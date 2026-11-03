// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Sets up the paths of a new interpreter of the project: it reads its version and `sys.path` and writes them as facts,
 * see [PythonInterpreterDetails.writeFacts]. [PythonInterpreterRegistry] calls it for each interpreter it registers.
 * The SDK updater implements it, and this module cannot depend on the updater.
 */
@ApiStatus.Internal
interface PythonInterpreterPathsSetup {
  /** Sets up the paths of [interpreter], see [PythonInterpreterPathsSetup]. It returns when the facts are read. */
  suspend fun setUpPaths(interpreter: PythonInterpreter)

  companion object {
    fun getInstance(project: Project): PythonInterpreterPathsSetup = project.service()
  }
}
