// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyInspectionTestCase
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.testing.pyTestFixtures.PyTestUnpassedFixtureInspection

class PyTestUnpassedFixtureInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection> {
    return PyTestUnpassedFixtureInspection::class.java
  }

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return ourPyLatestDescriptor
  }

  override fun isLowerCaseTestFile(): Boolean = false

  fun testFixtureInDecorator() {
    doTest()
  }

  fun testFixtureInParameters() {
    doTest()
  }

  fun testResolvedExpression() {
    doTest()
  }

  fun testResolvedExpressionFromImport() {
    doTest()
  }

  fun testSimpleUnpassedFixture() {
    doTest()
  }

  fun testUnpassedFixtureFromConftest() {
    doTest()
  }

  fun testUnpassedFixtureFromImport() {
    doTest()
  }
}