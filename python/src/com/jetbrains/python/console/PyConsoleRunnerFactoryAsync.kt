// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PythonRunConfiguration
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the runner behind a Python Console.
 *
 * Both methods suspend, because building a runner waits for the project model. A synchronous read answers `null` for
 * a module that has an interpreter while the SDK table still loads, and the console then starts on the wrong
 * interpreter or on none. See `findPythonSdkAndModule`.
 *
 * The `Async` suffix is not a choice. The deprecated [PythonConsoleRunnerFactory] keeps its method names, so that
 * factories outside this repository stay as they are, and a suspending method cannot take a name that a
 * non-suspending one of the same parameter types already has.
 *
 * The blocking methods of the base class are implemented here once, for callers outside this repository. Nothing
 * inside this repository goes through them.
 */
@ApiStatus.Internal
@Suppress("DEPRECATION")
abstract class PyConsoleRunnerFactoryAsync : PythonConsoleRunnerFactory() {

  abstract suspend fun createConsoleRunnerAsync(project: Project, contextModule: Module?): PydevConsoleRunner

  abstract suspend fun createConsoleRunnerWithFileAsync(project: Project, config: PythonRunConfiguration): PydevConsoleRunner

  @Deprecated("Blocks. Call createConsoleRunnerAsync.", ReplaceWith("createConsoleRunnerAsync(project, contextModule)"))
  final override fun createConsoleRunner(project: Project, contextModule: Module?): PydevConsoleRunner =
    runBlockingMaybeCancellable { createConsoleRunnerAsync(project, contextModule) }

  @Deprecated("Blocks. Call createConsoleRunnerWithFileAsync.", ReplaceWith("createConsoleRunnerWithFileAsync(project, config)"))
  final override fun createConsoleRunnerWithFile(project: Project, config: PythonRunConfiguration): PydevConsoleRunner =
    runBlockingMaybeCancellable { createConsoleRunnerWithFileAsync(project, config) }

  companion object {
    /**
     * The registered factory.
     *
     * A factory that still extends only the deprecated [PythonConsoleRunnerFactory] is not one of these, and this
     * skips it. Such a factory cannot answer without blocking, which is the whole reason for this class.
     */
    @JvmStatic
    fun getInstance(): PyConsoleRunnerFactoryAsync =
      getEpName().extensionList.filterIsInstance<PyConsoleRunnerFactoryAsync>().first()
  }
}
