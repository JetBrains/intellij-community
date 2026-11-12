// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@PyCodeInsightTestApplication
@InspectionTest(PyUnresolvedReferencesInspection::class)
internal class InstallAndImportPackageQuickFixTest {

  @Test
  @TestFor(issues = ["PY-42307"])
  fun `install and import package by name alias`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("pandas"))

    fixture.configureByText("foo.py", "pd<caret>.array()")

    // standard library
    fixture.findSingleIntention("Import 'turtle.pd'")

    // 'pd' is a common import alias for 'pandas' from PyPI
    fixture.findSingleIntention("Install and import package 'pandas'")
  }

  @Test
  @TestFor(issues = ["PY-55983"])
  fun `install and import is not offered for a capitalized name of a lowercase module`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("starlette"))

    fixture.configureByText("foo.py", "app = Starlette<caret>()")

    assertTrue(fixture.filterAvailableIntentions("Ignore unresolved reference").isNotEmpty())
    assertEquals(emptyList<String>(), fixture.filterAvailableIntentions("Install").map { it.text })
  }

  @Test
  @TestFor(issues = ["PY-55983"])
  fun `install and import is offered for a lowercase module`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("starlette"))

    fixture.configureByText("foo.py", "app = starlette<caret>.Starlette()")

    fixture.findSingleIntention("Install and import package 'starlette'")
  }

  @Test
  @TestFor(issues = ["PY-55983"])
  fun `install and import is offered for a capitalized module from the alias table`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("ipython"))

    fixture.configureByText("foo.py", "IPython<caret>.embed()")

    fixture.findSingleIntention("Install and import package 'IPython'")
  }

  @Test
  @TestFor(issues = ["PY-55983"])
  fun `install and import is not offered for a class name that a rare project ships as a module`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("pyrq", "mock"))

    fixture.configureByText("foo.py", "m = Mock<caret>()")

    assertTrue(fixture.filterAvailableIntentions("Ignore unresolved reference").isNotEmpty())
    assertEquals(emptyList<String>(), fixture.filterAvailableIntentions("Install").map { it.text })
  }

  @Test
  @TestFor(issues = ["PY-55983"])
  fun `install and import is not offered for a standard library class name`(fixture: CodeInsightTestFixture) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(fixture.project, availablePackageNames = listOf("htmlparser"))

    fixture.configureByText("foo.py", "parser = HTMLParser<caret>()")

    assertTrue(fixture.filterAvailableIntentions("Ignore unresolved reference").isNotEmpty())
    assertEquals(emptyList<String>(), fixture.filterAvailableIntentions("Install").map { it.text })
  }
}
