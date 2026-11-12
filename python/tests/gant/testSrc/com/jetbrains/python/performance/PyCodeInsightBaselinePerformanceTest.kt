// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.community.testFramework.performance.PyPerfProbe
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyDeprecationInspection
import com.jetbrains.python.psi.types.TypeEvalContextImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

/**
 * Measures the cold highlighting, the highlighting after a one-character edit, and one direct type inference pass
 * on two large real files from the Python helpers.
 *
 * `pyparsing/core.py` is a large annotated module with its package. `pycodestyle.py` is a large module without
 * annotations. The highlighting uses the Python inspections of the default profile. The benchmarks are
 * [PerformanceUnitTest] methods, and IJ Perf tracks their time and median counters. The gates run in the normal suite.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Performance
@TestFor(classes = [PyCodeInsightCounters::class, TypeEvalContextImpl::class])
class PyCodeInsightBaselinePerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  @PerformanceUnitTest
  @Test
  fun `annotated pyparsing core`() {
    configurePyparsingCore()
    measureFile("pyparsing_core")
  }

  @PerformanceUnitTest
  @Test
  fun `untyped pycodestyle`() {
    configurePycodestyle()
    measureFile("pycodestyle")
  }

  /** Only the annotators and the other highlighting passes run. No inspection is on. */
  @PerformanceUnitTest
  @Test
  fun `untyped pycodestyle without inspections`() {
    configurePycodestyle()
    var highlights = 0
    perf.probe.report(perf.probe.measure("pycodestyle/highlight_without_inspections") { highlights = perf.probe.highlight() })
    assertTrue(highlights > 0, "The annotators give no highlight")
  }

  /**
   * The deprecation marks of the library come from the stubs, so the check parses no library file. The test drops all
   * PSI files first, so the library files that earlier tests parsed have no AST.
   */
  @Test
  @TestFor(issues = ["PY-92689"])
  fun `deprecation inspection parses no library file`() {
    myFixture.tempDirFixture.copyAll(helper("generator3/_vendor/pyparsing"), "pyparsing")
    runInEdtAndWait { WriteAction.run<RuntimeException> { PsiManagerEx.getInstanceEx(myFixture.project).cleanupForNextTest() } }
    myFixture.configureFromTempProjectFile("pyparsing/core.py")
    val inspection = PyDeprecationInspection()
    myFixture.enableInspections(inspection)
    val loads = try {
      perf.probe.work { perf.probe.highlight() }.astLoads
    }
    finally {
      myFixture.disableInspections(inspection)
    }
    assertExpectation("pyparsing_core/deprecation", "no library AST load, the loads: $loads", loads.libraryFiles == 0L,
                      PyKnownDefect("PY-92689"))
  }

  private fun configurePyparsingCore() {
    myFixture.tempDirFixture.copyAll(helper("generator3/_vendor/pyparsing"), "pyparsing")
    myFixture.configureFromTempProjectFile("pyparsing/core.py")
  }

  private fun configurePycodestyle() {
    myFixture.configureByText("pycodestyle.py", PythonHelpersLocator.findPathInHelpers("pycodestyle.py").readText())
  }

  private fun helper(path: String): String = PythonHelpersLocator.findPathInHelpers(path).absolutePathString()

  private fun measureFile(name: String) {
    val cold = perf.probe.measureEditorFile(name, PyPerfProbe.defaultPythonInspections())
    assertTrue(cold[Counter.GET_TYPE_EVALUATIONS] > 0, "The cold highlighting evaluates no type")
  }
}
