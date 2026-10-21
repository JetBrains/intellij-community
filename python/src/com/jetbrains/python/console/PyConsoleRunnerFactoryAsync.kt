// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PythonRunConfiguration
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the runner behind a Python Console.
 *
 * Both methods suspend, because building a runner waits for the project model. A synchronous read answers `null` for
 * a module that has an interpreter while the SDK table still loads, and the console then starts on the wrong
 * interpreter or on none. See `findPythonSdkAndModule`.
 */
@ApiStatus.Internal
@Suppress("DEPRECATION")
abstract class PyConsoleRunnerFactoryAsync : PythonConsoleRunnerFactory() {

  abstract suspend fun createConsoleRunnerAsync(project: Project, contextModule: Module?): PydevConsoleRunner

  abstract suspend fun createConsoleRunnerWithFileAsync(project: Project, config: PythonRunConfiguration): PydevConsoleRunner

  companion object {
    /**
     * The registered factory.
     *
     * A factory that still extends only the deprecated [PythonConsoleRunnerFactory] is not one of these, and this
     * skips it. Such a factory has no methods to build a runner.
     */
    @JvmStatic
    fun getInstance(): PyConsoleRunnerFactoryAsync =
      getEpName().extensionList.filterIsInstance<PyConsoleRunnerFactoryAsync>().first()
  }
}
