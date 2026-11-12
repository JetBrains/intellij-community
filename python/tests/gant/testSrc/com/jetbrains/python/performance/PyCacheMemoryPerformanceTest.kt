// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.community.testFramework.performance.PyCacheMemoryProbe
import com.intellij.python.community.testFramework.performance.PyPerfProbe
import com.intellij.testFramework.PerformanceUnitTest
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContextCacheImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.absolutePathString

/**
 * Measures the memory that the code insight caches retain after a cold highlighting of `pyparsing/core.py`, with the
 * `PYMEM` lines of [PyCacheMemoryProbe]. The first attempt also loads one-off data, so it is not reported.
 */
@PerformanceUnitTest
@Subsystems.Typing
@Components.TypeInference
@Layers.Performance
@TestFor(issues = ["PY-92759", "PY-92760"], classes = [TypeEvalContextCacheImpl::class])
class PyCacheMemoryPerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  @Test
  fun `cache memory after cold highlighting of pyparsing core`() {
    myFixture.tempDirFixture.copyAll(PythonHelpersLocator.findPathInHelpers("generator3/_vendor/pyparsing").absolutePathString(), "pyparsing")
    val file = myFixture.configureFromTempProjectFile("pyparsing/core.py") as PyFile
    myFixture.enableInspections(*PyPerfProbe.defaultPythonInspections().toTypedArray())
    val memory = PyCacheMemoryProbe(myFixture.project)
    repeat(ATTEMPTS + 1) { attempt ->
      perf.probe.cold()
      // Stress mode turns the test debug logging off. That logging allocates for each recursion prevention.
      ApplicationManagerEx.runInStressTest<RuntimeException>(true) { perf.probe.highlight() }
      val counts = memory.contextCounts()
      val drops = memory.dropAll(file)
      if (attempt > 0) {
        memory.report("pyparsing_core#$attempt", counts, drops)
        assertTrue(drops.first { it.name == "typeContexts" }.freedBytes > 0, "Dropping the type contexts frees nothing")
      }
    }
  }

  private companion object {
    const val ATTEMPTS = 4
  }
}
