// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Checks member completion on a large class and find usages of a parameter in a project with many files.
 *
 * The gates run in the normal suite. The benchmarks are [PerformanceUnitTest] methods.
 */
@Subsystems.CodeCompletion
@Components.Completion
@Layers.Performance
@TestFor(classes = [PyCodeInsightCounters::class, CompletionVariantsProcessor::class])
class PyEditorFeaturesPerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  /** A prefix that keeps one member builds the lookup item of that member only. */
  @Test
  @TestFor(issues = ["PY-92694"])
  fun `member completion with a prefix does not grow with the class`() {
    assertGrowth("completion/lookup items", MEMBERS, PyGrowth.CONSTANT, PyKnownDefect("PY-92694", PyGrowth.LINEAR)) { members ->
      configureBigClass(members)
      perf.probe.work(setup = { configureCompletion(members, "m_${members - 1}") }) { complete() }.counters[Counter.COMPLETION_LOOKUP_ITEMS]
    }
  }

  @Test
  fun `member completion with a prefix gives the matching member`() {
    val members = MEMBERS.last()
    configureBigClass(members)
    configureCompletion(members, "m_${members - 1}")
    runInEdtAndGet { myFixture.completeBasic() }
    myFixture.checkResult("""
      from big_$members import Big
      Big().m_${members - 1}()
    """.trimIndent())
  }

  @PerformanceUnitTest
  @Test
  fun `member completion on a large class benchmark`() {
    val members = 2000
    configureBigClass(members)
    for (prefix in listOf("", "m_${members - 1}")) {
      perf.probe.report(perf.probe.measure("completion/n=$members/prefix='$prefix'", setup = { configureCompletion(members, prefix) }) {
        complete()
      })
    }
  }

  /**
   * Many project files use the name of the parameter, so the word index has the name in each file. The usages of a
   * parameter are in its function, so the search parses no other file.
   */
  @Test
  @TestFor(issues = ["PY-92693"])
  fun `find usages of a parameter does not grow with the project`() {
    assertGrowth("find_usages/project AST loads", FILES, PyGrowth.CONSTANT, PyKnownDefect("PY-92693", PyGrowth.LINEAR)) { files ->
      val parameter = configureFindUsages(files)
      perf.probe.work { assertEquals(1, findUsages(parameter)) }.astLoads.projectFiles
    }
  }

  @PerformanceUnitTest
  @Test
  fun `find usages of a parameter benchmark`() {
    val parameter = configureFindUsages(800)
    perf.probe.report(perf.probe.measure("find_usages/files=800") { findUsages(parameter) })
  }

  /** `big_N.py` with a class of [members] methods. */
  private fun configureBigClass(members: Int) {
    val code = buildString {
      appendLine("class Big:")
      for (i in 0 until members) appendLine("    def m_$i(self, a: int, b: str = '') -> list[int]: ...")
    }
    myFixture.addFileToProject("big_$members.py", code)
  }

  private fun configureCompletion(members: Int, prefix: String) {
    myFixture.configureByText("use_$members.py", """
      from big_$members import Big
      Big().$prefix<caret>
    """.trimIndent())
    perf.probe.cold()
  }

  private fun complete() {
    runInEdtAndGet { myFixture.completeBasic() }
  }

  /** [files] project files that use the name `name`, and the parameter `name` of a function in the editor file. */
  private fun configureFindUsages(files: Int): PyNamedParameter {
    for (i in 0 until files) {
      myFixture.addFileToProject("pkg_$files/m$i.py", """
        def f$i(name):
            return name + name

        name = 'x'
        print(name)
      """.trimIndent())
    }
    val file = myFixture.configureByText("main_$files.py", """
      def target(name):
          return name
    """.trimIndent()) as PyFile
    return runReadActionBlocking { PsiTreeUtil.findChildOfType(file.findTopLevelFunction("target"), PyNamedParameter::class.java)!! }
  }

  private fun findUsages(parameter: PyNamedParameter): Int = myFixture.findUsages(parameter).size

  private companion object {
    val MEMBERS = listOf(100, 200, 400)
    val FILES = listOf(10, 20, 40)
  }
}
