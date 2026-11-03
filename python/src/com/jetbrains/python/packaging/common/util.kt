// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

/** Package changes of an environment, on the bus of the project of its package manager. */
@ApiStatus.Experimental
interface PythonPackageManagementListener {
  /** The installed packages of [interpreter] changed. */
  fun packagesChanged(interpreter: PythonInterpreter) {}

  /** The outdated packages of [interpreter] changed. */
  fun outdatedPackagesChanged(interpreter: PythonInterpreter) {}

  /**
   * The packages that the dependency files of [interpreter] declare changed, such as when their first read ends after
   * the start of the manager.
   */
  fun declaredPackagesChanged(interpreter: PythonInterpreter) {}
}