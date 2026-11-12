// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyUnboundLocalVariableInspection
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalVariableInspection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Checks how the work of the native Python code insight grows with the size of a generated input.
 *
 * A scenario has a gate, a benchmark, or both. The gate runs in the normal suite. It counts the work for small sizes
 * and checks the growth with [assertGrowth]. The benchmark is a [PerformanceUnitTest]. It measures one large size with
 * [com.intellij.python.community.testFramework.performance.PyPerfProbe], and IJ Perf tracks its time. The flow
 * inspections have no gate, because no counter sees the work of their scope analysis.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Performance
@TestFor(classes = [PyCodeInsightCounters::class, PyControlFlowBuilder::class])
class PyCodeInsightScalingPerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  @Test
  @TestFor(issues = ["PY-92686"])
  fun `isinstance narrowing grows linearly`() {
    assertGrowth("narrowing/evaluations", NARROWING_SIZES, PyGrowth.LINEAR) { narrowingWork(it)[Counter.GET_TYPE_EVALUATIONS] }
    assertGrowth("narrowing/match steps", NARROWING_SIZES, PyGrowth.LINEAR, PyKnownDefect("PY-92686", PyGrowth.QUADRATIC)) {
      narrowingWork(it)[Counter.MATCH_STEPS]
    }
  }

  @Test
  fun `isinstance narrowing gives the type of the last block`() {
    val file = configureNarrowing(NARROWING_SIZES.last())
    val type = runReadActionBlocking { TypeEvalContext.codeAnalysis(file.project, file).getType(narrowedReads(file).last())?.name }
    assertEquals("C${NARROWING_SIZES.last() - 1}", type)
  }

  @PerformanceUnitTest
  @Test
  fun `isinstance narrowing benchmark`() {
    val file = configureNarrowing(400)
    val references = narrowedReads(file)
    perf.probe.report(perf.probe.measureScaled("narrowing/n=400") { inferAll(file, references) })
  }

  /**
   * The narrowing check uses a `codeInsightFallback` context when the control flow of the scope has 200 instructions
   * or more. The padding puts the function below and above that limit.
   */
  @Test
  fun `narrowing gives the same types below and above the control flow size limit`() {
    for (padding in listOf(10, 200)) {
      val code = buildString {
        appendLine("""
          from typing import TypeIs

          def is_int(x: object) -> TypeIs[int]: ...

          def f(x: object, y: int | str):
              check = is_int
        """.trimIndent())
        for (i in 0 until padding) appendLine("    p$i = $i")
        appendLine("""
          if check(x):
              r1 = x
          if isinstance(y, int):
              r2 = y
        """.trimIndent().prependIndent("    "))
      }
      val file = myFixture.configureByText("limit_$padding.py", code) as PyFile
      val types = runReadActionBlocking {
        val function = file.findTopLevelFunction("f")!!
        val context = TypeEvalContext.codeAnalysis(file.project, file)
        listOf("r1", "r2").map { name ->
          context.getType(PsiTreeUtil.findChildrenOfType(function, PyTargetExpression::class.java).single { it.name == name })?.name
        }
      }
      assertEquals(listOf("int", "int"), types, "padding=$padding")
    }
  }

  @Test
  @TestFor(issues = ["PY-92771"])
  fun `nested try statements grow the control flow linearly`() {
    assertGrowth("finally_in_try/instructions", DEPTHS, PyGrowth.LINEAR) { controlFlowWork(nestedFinally(it, inFinally = false)) }
    assertGrowth("finally_in_finally/instructions", DEPTHS, PyGrowth.LINEAR, PyKnownDefect("PY-92771")) {
      controlFlowWork(nestedFinally(it, inFinally = true))
    }
  }

  @PerformanceUnitTest
  @Test
  fun `nested try statements benchmark`() {
    for (inFinally in listOf(false, true)) {
      val shape = if (inFinally) "finally_in_finally" else "finally_in_try"
      val file = myFixture.configureByText("$shape.py", nestedFinally(9, inFinally)) as PyFile
      val function = runReadActionBlocking { file.findTopLevelFunction("f")!! }
      perf.probe.report(perf.probe.measureScaled("$shape/depth=9", setup = {}) {
        runReadActionBlocking { PyControlFlowBuilder(LanguageLevel.getLatest()).buildControlFlow(function) }
      })
    }
  }

  @PerformanceUnitTest
  @Test
  fun `flow inspections on many local names benchmark`() {
    for ((name, inspection) in FLOW_INSPECTIONS) {
      configureNames(2000)
      myFixture.enableInspections(inspection)
      try {
        perf.probe.report(perf.probe.measureScaled("names_$name/n=2000") { perf.probe.highlight() })
      }
      finally {
        myFixture.disableInspections(inspection)
      }
    }
  }

  @Test
  @TestFor(issues = ["PY-93781"])
  fun `type inference of a generic call grows linearly with the type parameters`() {
    assertGrowth("generic_call/traversal visits", TYPE_PARAMETER_SIZES, PyGrowth.LINEAR) { typeParameters ->
      val (file, target) = configureGenericCall(typeParameters)
      perf.probe.work { inferAll(file, listOf(target)) }[Counter.TYPE_TRAVERSAL_VISITS]
    }
  }

  @Test
  @TestFor(issues = ["PY-93781"])
  fun `type inference of a generic call binds each type parameter`() {
    val typeParameters = TYPE_PARAMETER_SIZES.last()
    val (file, target) = configureGenericCall(typeParameters)
    val type = runReadActionBlocking { TypeEvalContext.codeAnalysis(file.project, file).getType(target)?.name }
    assertEquals((0 until typeParameters).joinToString(", ", "(", ")") { "Literal[$it]" }, type)
  }

  @Test
  fun `overload resolution grows linearly with the overloads`() {
    for (arguments in listOf(1, 4)) {
      assertGrowth("overloads_m=$arguments/candidates", OVERLOAD_SIZES, PyGrowth.LINEAR) { overloads ->
        val (file, targets) = configureOverloads(overloads, arguments)
        perf.probe.work { inferAll(file, targets) }[Counter.OVERLOAD_CANDIDATES_CHECKED]
      }
    }
  }

  @Test
  fun `overload resolution picks the matching overload`() {
    val overloads = OVERLOAD_SIZES.last()
    val (file, targets) = configureOverloads(overloads, arguments = 4)
    val type = runReadActionBlocking { TypeEvalContext.codeAnalysis(file.project, file).getType(targets.last())?.name }
    assertEquals("A${overloads - 1}", type)
  }

  @PerformanceUnitTest
  @Test
  fun `overload resolution benchmark`() {
    for (arguments in listOf(1, 4)) {
      val (file, targets) = configureOverloads(200, arguments)
      perf.probe.report(perf.probe.measureScaled("overloads_m=$arguments/n=200") { inferAll(file, targets) })
    }
  }

  @Test
  fun `type checking of a literal union grows linearly with the literals`() {
    assertGrowth("union/match steps", UNION_SIZES, PyGrowth.LINEAR) { literals ->
      configureUnion(literals)
      highlightWork(PyTypeCheckerInspection())[Counter.MATCH_STEPS]
    }
  }

  @PerformanceUnitTest
  @Test
  fun `type checking of a literal union benchmark`() {
    val inspection = PyTypeCheckerInspection()
    configureUnion(200)
    myFixture.enableInspections(inspection)
    try {
      perf.probe.report(perf.probe.measureScaled("union/n=200") { perf.probe.highlight() })
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  private fun narrowingWork(blocks: Int) = configureNarrowing(blocks).let { file ->
    val references = narrowedReads(file)
    perf.probe.work { inferAll(file, references) }
  }

  /** `N` classes and one function with `N` `isinstance` blocks. Each block reads the narrowed parameter. */
  private fun configureNarrowing(blocks: Int): PyFile {
    val code = buildString {
      for (i in 0 until blocks) appendLine("class C$i: pass")
      appendLine()
      appendLine("def f(x: object):")
      for (i in 0 until blocks) {
        appendLine("""
          if isinstance(x, C$i):
              r$i = x
        """.trimIndent().prependIndent("    "))
      }
    }
    return myFixture.configureByText("narrowing_$blocks.py", code) as PyFile
  }

  /** The reads of `x` in the `r = x` statements, without the `isinstance` arguments. */
  private fun narrowedReads(file: PyFile): List<PyReferenceExpression> = runReadActionBlocking {
    PsiTreeUtil.findChildrenOfType(file.findTopLevelFunction("f"), PyReferenceExpression::class.java)
      .filter { it.text == "x" && it.parent !is PyArgumentList }
  }

  private fun controlFlowWork(code: String): Long {
    val file = myFixture.configureByText("finally.py", code) as PyFile
    val function = runReadActionBlocking { file.findTopLevelFunction("f")!! }
    return perf.probe.work(setup = {}) {
      runReadActionBlocking { PyControlFlowBuilder(LanguageLevel.getLatest()).buildControlFlow(function) }
    }[Counter.CFG_INSTRUCTIONS]
  }

  /**
   * One function with `try`/`finally` statements nested [depth] times. The nested statement is in the `finally` body
   * when [inFinally] is true, and in the `try` body otherwise.
   */
  private fun nestedFinally(depth: Int, inFinally: Boolean): String = buildString {
    appendLine("def f(c):")
    fun block(level: Int) {
      val indent = "    ".repeat(level + 1)
      if (level == depth) {
        appendLine("${indent}x = c")
        return
      }
      appendLine("${indent}try:")
      if (inFinally) appendLine("$indent    y$level = c") else block(level + 1)
      appendLine("${indent}finally:")
      if (inFinally) block(level + 1) else appendLine("$indent    z$level = c")
    }
    block(0)
    appendLine("    return c")
  }

  /**
   * One function with `N` local names. Every tenth name is bound only in a branch, so the unbound inspection has work.
   * Every seventh name is bound twice, so the redeclaration inspection has work. Half of the names are read.
   */
  private fun configureNames(names: Int) {
    val code = buildString {
      appendLine("def f(c):")
      for (i in 0 until names) {
        if (i % 10 == 0) {
          appendLine("""
            if c:
                v$i = $i
          """.trimIndent().prependIndent("    "))
        }
        else {
          appendLine("    v$i = $i")
        }
        if (i % 7 == 0) appendLine("    v$i = c")
      }
      for (i in 0 until names step 2) appendLine("    print(v$i)")
    }
    myFixture.configureByText("names_$names.py", code)
  }

  /** `N` classes, a function with `N` overloads of [arguments] parameters, and twenty calls of it. */
  private fun configureOverloads(overloads: Int, arguments: Int): Pair<PyFile, List<PyTargetExpression>> {
    val code = buildString {
      appendLine("from typing import overload")
      appendLine()
      for (i in 0 until overloads) appendLine("class A$i: pass")
      for (i in 0 until overloads) {
        appendLine("""
          @overload
          def f(${(0 until arguments).joinToString(", ") { "p$it: A$i" }}) -> A$i: ...
        """.trimIndent())
      }
      appendLine("def f(*args): ...")
      appendLine()
      for (k in 0 until SITES) {
        val target = (k * (overloads - 1)) / (SITES - 1)
        appendLine("r$k = f(${(0 until arguments).joinToString(", ") { "A$target()" }})")
      }
    }
    val file = myFixture.configureByText("overloads_${overloads}_$arguments.py", code) as PyFile
    return file to runReadActionBlocking { (0 until SITES).map { file.findTopLevelAttribute("r$it")!! } }
  }

  /** A union of `N` literal types, assigned and passed at fifty sites. A third of the calls pass a `str`. */
  private fun configureUnion(literals: Int) {
    val code = buildString {
      appendLine("""
        from typing import Literal

        L = Literal[${(0 until literals).joinToString(", ") { "\"v$it\"" }}]

        def g(x: L) -> L:
            return x

        def h(y: L, s: str):
      """.trimIndent())
      for (k in 0 until SITES_UNION) {
        appendLine("""
          a$k: L = g(y)
          b$k: L = "v${k % literals}"
          g(s)
        """.trimIndent().prependIndent("    "))
      }
    }
    myFixture.configureByText("union_$literals.py", code)
  }

  /** A function with `N` type parameters, and one call of it whose arguments are generic calls too. */
  private fun configureGenericCall(typeParameters: Int): Pair<PyFile, PyTargetExpression> {
    val indices = 0 until typeParameters
    val code = """
      def f[T](x: T) -> list[T]: ...
      def g[${indices.joinToString(", ") { "T$it" }}](${indices.joinToString(", ") { "a$it: list[T$it]" }}) -> tuple[${indices.joinToString(", ") { "T$it" }}]: ...
      r = g(${indices.joinToString(", ") { "f($it)" }})
    """.trimIndent()
    val file = myFixture.configureByText("generic_call_$typeParameters.py", code) as PyFile
    return file to runReadActionBlocking { file.findTopLevelAttribute("r")!! }
  }

  private fun inferAll(file: PyFile, elements: List<PyTypedElement>) {
    runReadActionBlocking {
      val context = TypeEvalContext.codeAnalysis(file.project, file)
      elements.forEach { context.getType(it) }
    }
  }

  /** The work of all threads in one highlighting of the editor file with only [inspection] on. */
  private fun highlightWork(inspection: LocalInspectionTool): PyCodeInsightCounters.Snapshot {
    myFixture.enableInspections(inspection)
    try {
      return perf.probe.work { perf.probe.highlight() }.counters
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  private companion object {
    val NARROWING_SIZES = listOf(50, 100, 200)
    val DEPTHS = listOf(4, 8)
    val OVERLOAD_SIZES = listOf(10, 20, 40)
    val UNION_SIZES = listOf(10, 20, 40)
    val TYPE_PARAMETER_SIZES = listOf(10, 20, 40)
    const val SITES = 20
    const val SITES_UNION = 50

    val FLOW_INSPECTIONS: List<Pair<String, LocalInspectionTool>> = listOf(
      "unused" to PyUnusedLocalVariableInspection(),
      "redeclaration" to PyRedeclarationInspection(),
      "unbound" to PyUnboundLocalVariableInspection(),
    )
  }
}
