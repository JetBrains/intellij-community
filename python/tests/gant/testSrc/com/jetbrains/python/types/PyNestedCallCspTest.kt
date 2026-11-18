// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A call whose argument is itself a call builds the constraint problem of that argument once, however many candidates
 * the enclosing callee has.
 *
 * The build of a nested CSP does not read the parameter that the argument is passed to, so every candidate of the
 * enclosing callee gets the same nested result. Rebuilding it per candidate rebuilds the subtree at depth k
 * candidates^k times, each rebuild adding a fresh set of inference variables, which is what makes
 * PyNumpyNestedCallPerformanceTest, over a statement of nested NumPy dtype calls, take 15 s on master.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-94162"])
class PyNestedCallCspTest : PyCodeInsightTestCase() {

  @Test
  fun `a nested call of an overloaded callee keeps the type of every nesting level`() {
    val file = myFixture.configureByText("a.py", CODE)
    runReadActionBlocking {
      val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
      assertEquals("int", typeOfTarget(file, "shallow", context))
      assertEquals("str", typeOfTarget(file, "other_overload", context))
      assertEquals("int", typeOfTarget(file, "deep", context))
    }
  }

  @Test
  fun `the nested problem of a deeper call does not multiply with the candidates above it`() {
    val file = myFixture.configureByText("a.py", CODE)
    val shallow = countInferenceVariables(file, "shallow")
    val deep = countInferenceVariables(file, "deep")
    // `deep` nests three calls of a four-way overload: 1 + 4 + 16 builds per candidate against 3 per anchor. That
    // reads as 9 inference variables with the memo and 63 without it, and the bound sits between them because the
    // variables one level adds follow the solver. On PyNumpyNestedCallPerformanceTest the two readings are 48 and
    // 27,096, so the shape of the difference is what this pins, not its size.
    assertTrue(deep < 20, "inference variables: shallow=$shallow deep=$deep")
  }

  @Test
  fun `partly generic overloads keep the type of each nesting level`() {
    val file = myFixture.configureByText("a.py", GENERIC_CODE)
    runReadActionBlocking {
      val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
      assertEquals("Pair[int, str]", typeOfTarget(file, "shallow", context))
      assertEquals("Box[bytes]", typeOfTarget(file, "other_overload", context))
      assertEquals("Box[str]", typeOfTarget(file, "pair_and_str", context))
      assertEquals("str", typeOfTarget(file, "pair_and_int", context))
      assertEquals("int", typeOfTarget(file, "direct_bytes", context))
      assertEquals("Literal[\"s\"]", typeOfTarget(file, "deep_level_2", context))
      assertEquals("Box[str]", typeOfTarget(file, "reboxed_level_2", context))
      assertEquals("Box[str]", typeOfTarget(file, "deep_level_3", context))
      assertEquals("Box[bytes]", typeOfTarget(file, "deep_level_4", context))
      assertEquals("Box[bytes]", typeOfTarget(file, "deep", context))
    }
  }

  @Test
  fun `the nested problem of partly generic overloads does not multiply with candidates above it`() {
    val file = myFixture.configureByText("a.py", GENERIC_CODE)
    val deep = countInferenceVariables(file, "deep")
    // The measured count is 281; keep the limit below twice that value.
    assertTrue(deep < 562, "inference variables: deep=$deep")
  }

  private fun countInferenceVariables(file: PsiFile, name: String): Long {
    val counts = PyCodeInsightCounters.countOnCurrentThread {
      runReadActionBlocking {
        // A context of its own, so the count is the work of this expression and not what an earlier one left cached
        typeOfTarget(file, name, TypeEvalContext.codeAnalysis(myFixture.project, file))
      }
    }
    val variables = counts[Counter.CSP_INFERENCE_VARIABLES] ?: 0
    assertTrue(variables > 0, "the expression of `$name` builds no constraint problem")
    return variables
  }

  private fun typeOfTarget(file: PsiFile, name: String, context: TypeEvalContext): String? {
    val target = PsiTreeUtil.findChildrenOfType(file, PyTargetExpression::class.java).first { it.name == name }
    return PythonDocumentationProvider.getTypeName(context.getType(target.findAssignedValue()!!), context)
  }

  private companion object {
    // `pick` has four candidates and takes a Box, `rebox` makes one, so each nesting level of `pick(rebox(...))`
    // multiplies the builds of everything below it when the nested problem is not shared between the candidates.
    private val CODE = """
      from typing import Generic, TypeVar, overload

      T = TypeVar("T")

      class Box(Generic[T]):
          def get(self) -> T: ...

      def rebox(value: T) -> Box[T]: ...

      @overload
      def pick(a: Box[int]) -> int: ...
      @overload
      def pick(a: Box[str]) -> str: ...
      @overload
      def pick(a: Box[float]) -> float: ...
      @overload
      def pick(a: Box[bytes]) -> bytes: ...
      def pick(a): ...

      shallow = pick(rebox(1))
      other_overload = pick(rebox("s"))
      deep = pick(rebox(pick(rebox(pick(rebox(1))))))
    """.trimIndent()

    private const val GENERIC_CODE = """
      from typing import Generic, TypeVar, overload

      T = TypeVar("T")
      U = TypeVar("U")

      text: str = "s"
      number: int = 7

      class Box(Generic[T]):
          def get(self) -> T: ...

      class Pair(Generic[T, U]):
          pass

      def rebox(value: T) -> Box[T]: ...

      def pair(first: T, second: U) -> Pair[T, U]: ...

      @overload
      def mix(a: Box[int], b: T) -> Pair[int, T]: ...
      @overload
      def mix(a: Box[str], b: T) -> Box[T]: ...
      @overload
      def mix(a: Pair[T, U], b: str) -> Box[U]: ...
      @overload
      def mix(a: Pair[T, U], b: T) -> U: ...
      @overload
      def mix(a: T, b: bytes) -> T: ...
      def mix(a, b): ...

      shallow = mix(rebox(1), "s")
      other_overload = mix(rebox("s"), b"raw")
      pair_and_str = mix(pair(1, text), "tag")
      pair_and_int = mix(pair(1, text), number)
      direct_bytes = mix(number, b"raw")

      deep_level_2 = mix(mix(rebox(1), "s"), 3)
      reboxed_level_2 = rebox(mix(mix(rebox(1), "s"), 3))
      deep_level_3 = mix(pair(1, text), "tag")
      deep_level_4 = mix(deep_level_3, b"raw")
      deep = mix(pair(1, mix(pair(1, mix(mix(mix(pair(1, text), "tag"), b"raw"), b"raw")), 7)), 7)
    """.trimIndent()
  }
}
