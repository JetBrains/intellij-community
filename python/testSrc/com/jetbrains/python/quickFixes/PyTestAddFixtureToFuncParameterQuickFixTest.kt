// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.pyTestFixtures.PyTestUnpassedFixtureInspection

class PyTestAddFixtureToFuncParameterQuickFixTest : PyQuickFixTestCase() {

  private fun doTest() {
    doQuickFixTest(PyTestUnpassedFixtureInspection::class.java,
                   PyPsiBundle.message("QFIX.add.fixture.to.test.function.parameters.list"),
                   LanguageLevel.getLatest())
  }

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }
  
  fun testSimpleUnpassedFixture() {
    doTest()
  }

  fun testUnpassedFixtureFromImport() {
    doTest()
  }

  fun testUnpassedFixtureFromConftest() {
    doTest()
  }
}