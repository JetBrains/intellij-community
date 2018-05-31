// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.jetbrains.python.PyNames
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyUnusedLocalInspection
import com.jetbrains.python.testing.pyTestParametrized.PyTestParametrizedInspection

/**
 * Test py.test fixtures and paramterized completions and inspections
 */
class PyTestFixtureAndParametrizedTest : PyTestCase() {
  override fun getTestDataPath() = super.getTestDataPath() + "/testCompletion"
  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).projectConfiguration =
      PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)
  }

  fun testInspection() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_for_inspection_test.py")
    myFixture.enableInspections(PyUnusedLocalInspection::class.java, PyTestParametrizedInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
  }

  fun testCompletion() {

    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_test.py")
    myFixture.completeBasicAllCarets(null)
    myFixture.checkResultByFile("after_test_test.txt")
  }
}