// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.tools.sdkTools.PyConformanceTestSuiteUtils
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


// The test suite has not been updated for 3.14 by default.
// In particular, forward references are not enabled by default
// and require an explicit `from __future__ import annotations`.
private val PY313_DESCRIPTOR = PyLightProjectDescriptor(LanguageLevel.PYTHON313)

@RunWith(Parameterized::class)
class PyTypingConformanceTest(private val testFileName: String) : PyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = PY313_DESCRIPTOR

  @Test
  fun test() {
    myFixture.configureByFiles(*PyConformanceTestSuiteUtils.getFilePaths(testFileName))
    myFixture.enableInspections(*PyConformanceTestSuiteUtils.pythonConformanceSuiteInspections)
    PyConformanceTestSuiteUtils.checkHighlighting(myFixture, testFileName)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<String> = PyConformanceTestSuiteUtils.getTestFiles()

    @AfterClass
    @JvmStatic
    fun afterClass() {
      val failures = PyConformanceTestSuiteUtils.failures
      if (failures.isNotEmpty()) {
        val missingErrorsCount = failures.sumOf { it.missingErrorsCount }
        val unexpectedErrorsCount = failures.sumOf { it.unexpectedErrorsCount }
        println("Test failed: missing errors: $missingErrorsCount; unexpected errors: $unexpectedErrorsCount")

        failures.sortedBy { it.missingErrorsCount + it.unexpectedErrorsCount }.forEach {
          println("${it.testFileName} ${it.missingErrorsCount} ${it.unexpectedErrorsCount}")
        }
      }
    }
  }
}