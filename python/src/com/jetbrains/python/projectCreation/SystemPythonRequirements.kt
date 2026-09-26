// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectCreation

import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.jetbrains.python.packaging.PyVersionSpecifiers

/**
 * Represents requirements for system Python during virtual environment creation.
 *
 * See [createVenvAndSdk]
 */
sealed class SystemPythonRequirements {
  /**
   * Given [systemPython] will be used to create venv.
   */
  data class Explicit(val systemPython: SystemPython) : SystemPythonRequirements()

  /**
   * System Python will be chosen automatically using [SystemPythonService] based on [versionSpecifiers].
   * If there is no suitable system python, one will be installed with [confirmInstallation].
   */
  data class ByVersionSpecifier(
      val systemPythonService: SystemPythonService = SystemPythonService(),
      val versionSpecifiers: PyVersionSpecifiers = PyVersionSpecifiers.ANY_SUPPORTED,
      val confirmInstallation: suspend () -> Boolean = { true }
  ) : SystemPythonRequirements()
}
