package com.jetbrains.python.testing

import com.intellij.openapi.module.Module

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

  /**
   * Returns true if it's allowed to create/show pytest-related artifacts for the given file under current module settings.
   * In particular: when pytest is selected, only default pytest test modules are allowed.
   */
  @JvmStatic
  fun isPyTestAllowedForFile(module: Module?, fileName: String?): Boolean =
    !isPyTestSelected(module) || isDefaultPyTestTestModule(fileName)

  @JvmStatic
  fun isPyTestAllowedForFile(module: Module?, pyFile: com.jetbrains.python.psi.PyFile?): Boolean {
    if (!isPyTestSelected(module)) return true
    if (pyFile == null) return false
    if (isDefaultPyTestTestModule(pyFile.name)) return true
    // Allow when the file explicitly imports pytest
    val importsPyTest = pyFile.importTargets.any { it.importedQName?.firstComponent == "pytest" } ||
                        pyFile.fromImports.any { it.importSourceQName?.firstComponent == "pytest" }
    return importsPyTest
  }
}
