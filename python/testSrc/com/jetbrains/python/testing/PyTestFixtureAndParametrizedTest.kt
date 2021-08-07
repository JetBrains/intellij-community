// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection
import com.jetbrains.python.testing.pyTestParametrized.PyTestParametrizedInspection

/**
 * Test py.test fixtures and paramterized completions and inspections
 */
class PyTestFixtureAndParametrizedTest : PyTestCase() {
  companion object {
    const val testSubfolder = "/testCompletion"
    fun testInspectionStatic(fixture: CodeInsightTestFixture) {
      fixture.configureByFile("test_for_inspection_test.py")
      fixture.enableInspections(PyUnusedLocalInspection::class.java, PyTestParametrizedInspection::class.java)
      fixture.checkHighlighting(true, false, true)
    }
  }

  override fun getTestDataPath() = super.getTestDataPath() + testSubfolder
  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
  }

  fun testInspection() {
    myFixture.copyDirectoryToProject(".", ".")
    testInspectionStatic(myFixture)
  }

  fun testTypeCompletion() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_parametrized.py")
    myFixture.completeBasicAllCarets('\t')
    myFixture.checkResultByFile("after_test_parametrized.txt")
  }

  fun testCompletion() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_test.py")
    myFixture.completeBasicAllCarets('\t')
    myFixture.checkResultByFile("after_test_test.txt")
  }

  fun testRename() {
    myFixture.configureByFile("test_for_rename.py")
    myFixture.renameElementAtCaret("spam")
    myFixture.checkResultByFile("test_for_rename.after.py.txt")
  }
}
