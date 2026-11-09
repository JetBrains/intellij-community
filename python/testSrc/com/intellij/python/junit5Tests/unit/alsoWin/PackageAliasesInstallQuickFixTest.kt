// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The install fix of an unresolved import takes its PyPI project from the module alias table in `tools/packages`.
 */
@PyCodeInsightTestApplication
@InspectionTest(PyUnresolvedReferencesInspection::class)
@TestFor(issues = ["PY-55983"], classes = [PyPsiPackageUtil::class])
internal class PackageAliasesInstallQuickFixTest {

  @Test
  fun `module named differently from its project installs the project`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("python-dotenv"))

    fixture.configureByText("foo.py", "import dotenv<caret>")

    fixture.findSingleIntention("Install package python-dotenv")
  }

  @Test
  fun `PyQt5 installs its own project`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("pyqt5"))

    fixture.configureByText("foo.py", "import PyQt5<caret>")

    fixture.findSingleIntention("Install package pyqt5")
  }

  @Test
  fun `PyQt6 installs its own project`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("pyqt6"))

    fixture.configureByText("foo.py", "import PyQt6<caret>")

    fixture.findSingleIntention("Install package pyqt6")
  }

  // The test environment has setuptools installed, so no install fix would show; the table is what decides.
  @Test
  fun `pkg_resources maps to setuptools rather than a fork`() {
    assertEquals("setuptools", PyPsiPackageUtil.moduleToPackageName("pkg_resources"))
  }
}
