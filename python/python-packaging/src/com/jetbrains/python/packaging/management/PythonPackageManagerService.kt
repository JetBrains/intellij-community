// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface PythonPackageManagerProvider {

  /** Creates the [PythonPackageManager] for [interpreter], or `null` when this provider does not handle it. */
  fun createPackageManager(interpreter: PythonInterpreter): PythonPackageManager? =
    @Suppress("DEPRECATION") createPackageManager(interpreter.project, interpreter)

  /** For the providers outside the Python plugin. */
  @Deprecated("Implement createPackageManager(interpreter)")
  fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? = null

  companion object {
    val EP_NAME: ExtensionPointName<PythonPackageManagerProvider> =
      ExtensionPointName.create("Pythonid.pythonPackageManagerProvider")
  }
}

@ApiStatus.Internal
interface PythonPackageManagerService {
  /** The manager of the environment that [interpreter] runs in. */
  @Throws(AlreadyDisposedException::class)
  fun forPythonInterpreter(interpreter: PythonInterpreter): PythonPackageManager

  /** The cached manager of [interpreter], or `null` when no caller asked for it yet. It creates no manager. */
  fun cachedFor(interpreter: PythonInterpreter): PythonPackageManager?

}
