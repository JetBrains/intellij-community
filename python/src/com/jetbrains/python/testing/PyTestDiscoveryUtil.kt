// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.PyFile

/**
 * Shared helpers for pytest-related discovery gating used by configuration producer and gutter contributor.
 */
internal object PyTestDiscoveryUtil {
  /** Returns true if the selected test runner for the module is pytest. */
  @JvmStatic
  fun isPyTestSelected(module: Module?): Boolean =
    module != null && TestRunnerService.getInstance(module).selectedFactory is PyTestFactory

  /** Default pytest test module detection by filename. Excludes special conftest.py. */
  @JvmStatic
  fun isDefaultPyTestTestModule(fileName: String?): Boolean =
    fileName != null && (fileName.startsWith("test_") || fileName.endsWith("_test.py")) && fileName != "conftest.py"

  @JvmStatic
  fun isPyTestAllowedForFile(module: Module?, pyFile: PyFile?): Boolean {
    if (!isPyTestSelected(module)) return true
    if (pyFile == null) return false
    if (isDefaultPyTestTestModule(pyFile.name)) return true
    // Allow when the file explicitly imports pytest
    val importsPyTest = pyFile.importTargets.any { it.importedQName?.firstComponent == "pytest" } ||
                        pyFile.fromImports.any { it.importSourceQName?.firstComponent == "pytest" }
    return importsPyTest
  }
}