// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/**
 * Guards what a composite type costs to match and to explain (PY-91327).
 *
 * The counter is a [PyTypeCheckerExtension] masked in alongside the real ones. [PyTypeChecker] consults every
 * extension at the top of each non-trivial match, before any composite branch runs, so tallying there counts
 * the recursive match steps without instrumenting production code. An empty [Optional] leaves the verdict to
 * the normal implementation.
 *
 * The expected counts are closed forms, not magic numbers. See each test.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-91327"], classes = [PyTypeChecker::class])
class PyCompositeMatchCostTest : PyCodeInsightTestCase() {

  private val steps = AtomicLong()

  private inner class CountingExtension : PyTypeCheckerExtension {
    override fun match(
      expected: PyType?,
      actual: PyType?,
      context: TypeEvalContext,
      substitutions: PyTypeChecker.GenericSubstitutions,
    ): Optional<Boolean> {
      steps.incrementAndGet()
      return Optional.empty()
    }
  }

  /** `n+1`, because the walk records as it goes. It was `2n+1`: each failing member was matched twice. */
  @Test
  fun `explaining a union costs one match per member`() = withCounter { fixture ->
    for (width in WIDTHS) {
      val union = PyUnionType.unionOrUnknown(fixture.actualMembers(width))!!
      val cost = count { PyTypeChecker.explainMismatch(fixture.int, union, fixture.context) }
      assertEquals(width + 1L, cost, "A union of $width members must be explained in one walk")
    }
  }

  /** The intersection walk is the same either way, so recording its breakdown is free. */
  @Test
  fun `explaining an intersection costs what deciding it costs`() = withCounter { fixture ->
    for (width in WIDTHS) {
      val intersection = PyIntersectionType.intersectionOrTop(fixture.actualMembers(width))!!
      val verdict = count { PyTypeChecker.match(fixture.int, intersection, fixture.context) }
      val breakdown = count { PyTypeChecker.explainMismatch(fixture.int, intersection, fixture.context) }
      assertEquals(verdict, breakdown, "Recording an intersection breakdown of $width members must be free")
    }
  }

  /**
   * Union against union is the one quadratic shape, because both sides are walked. The assertion is an upper
   * bound, not the exact `n^2+n+1`, so a later improvement does not fail it.
   */
  @Test
  fun `explaining a union against a union stays within the pairwise bound`() = withCounter { fixture ->
    for (width in WIDTHS) {
      val actual = PyUnionType.unionOrUnknown(fixture.actualMembers(width))!!
      val expected = PyUnionType.unionOrUnknown(fixture.expectedMembers(width))!!
      val cost = count { PyTypeChecker.explainMismatch(expected, actual, fixture.context) }
      val bound = width.toLong() * width + width + 1
      assertTrue(cost <= bound, "Explaining union($width) against union($width) took $cost, over the $bound bound")
    }
  }

  /** The bound is configurable, so a lowered one must move where the per-member detail stops. */
  @Test
  fun `the breakdown bound follows its registry value`() {
    val singlePass = Registry.get("python.typing.composite.single.pass")
    val maxMembers = Registry.get("python.typing.composite.breakdown.max.members")
    singlePass.setValue(true)
    maxMembers.setValue(2)
    try {
      withCounter { fixture ->
        // Three members is past a bound of two, so it collapses, although the default bound of five allows it.
        val union = PyUnionType.unionOrUnknown(fixture.actualMembers(3))!!
        val verdict = count { PyTypeChecker.match(fixture.int, union, fixture.context) }
        val breakdown = count { PyTypeChecker.explainMismatch(fixture.int, union, fixture.context) }
        assertEquals(verdict, breakdown, "A lowered bound must collapse a union the default bound would explain")
      }
    }
    finally {
      maxMembers.resetToDefault()
      singlePass.resetToDefault()
    }
  }

  /** Past the bound the breakdown is not collected, so explaining costs what deciding costs. */
  @Test
  fun `past the breakdown bound explaining a union costs what deciding it costs`() {
    val registry = Registry.get("python.typing.composite.single.pass")
    registry.setValue(true)
    try {
      withCounter { fixture ->
        val bound = PyTypeChecker.maxBreakdownMembers()
        val width = bound + 1
        val union = PyUnionType.unionOrUnknown(fixture.actualMembers(width))!!
        val verdict = count { PyTypeChecker.match(fixture.int, union, fixture.context) }
        val breakdown = count { PyTypeChecker.explainMismatch(fixture.int, union, fixture.context) }
        assertEquals(verdict, breakdown, "A union of $width members must not be broken down member by member")

        // At the bound the per-member detail survives, so the two costs still differ.
        val atBound = PyUnionType.unionOrUnknown(fixture.actualMembers(bound))!!
        val atBoundVerdict = count { PyTypeChecker.match(fixture.int, atBound, fixture.context) }
        val atBoundBreakdown = count { PyTypeChecker.explainMismatch(fixture.int, atBound, fixture.context) }
        assertTrue(atBoundBreakdown > atBoundVerdict,
                   "A union at the bound must keep its per-member breakdown")
      }
    }
    finally {
      registry.resetToDefault()
    }
  }

  /**
   * A PEP 604 annotation is a union form, not a chain of `__or__` calls, so declaring one must cost no match.
   * Checking those calls used to make an annotation of n members cost O(n^2).
   */
  @Test
  fun `declaring a pep604 union annotation costs no match`() {
    val width = WIDTHS.max()
    val classes = (1..width).joinToString("\n") { "class C$it: pass" }
    val pipe = (1..width).joinToString(" | ") { "C$it" }
    val disposable = Disposer.newDisposable("PY-91327 match counter")
    try {
      ExtensionTestUtil.maskExtensions(
        PyTypeCheckerExtension.EP_NAME,
        PyTypeCheckerExtension.EP_NAME.extensionList + CountingExtension(),
        disposable,
      )
      myFixture.enableInspections(PyTypeCheckerInspection())
      myFixture.configureByText("a.py", "$classes\ndef f(u: $pipe) -> None:\n    pass")
      val cost = count { myFixture.doHighlighting() }
      assertEquals(0L, cost, "A PEP 604 annotation of $width members must not be type-checked as `__or__` calls")
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /** Runs [body] and returns how many match steps it took. */
  private fun count(body: () -> Unit): Long {
    steps.set(0)
    body()
    return steps.get()
  }

  private class Fixture(val context: TypeEvalContext, val int: PyType, private val byPrefix: Map<Char, List<PyType>>) {
    /** Members for the provided side. They are distinct classes, because equal members would collapse. */
    fun actualMembers(width: Int): List<PyType> = byPrefix.getValue('A').take(width)

    /** Members for the required side, disjoint from [actualMembers] so no pair matches. */
    fun expectedMembers(width: Int): List<PyType> = byPrefix.getValue('E').take(width)
  }

  private fun withCounter(body: (Fixture) -> Unit) {
    val widest = WIDTHS.max()
    val classes = (1..widest).joinToString("\n") { "class A$it: pass\nclass E$it: pass" }
    myFixture.configureByText("a.py", classes)
    val disposable = Disposer.newDisposable("PY-91327 match counter")
    try {
      ExtensionTestUtil.maskExtensions(
        PyTypeCheckerExtension.EP_NAME,
        PyTypeCheckerExtension.EP_NAME.extensionList + CountingExtension(),
        disposable,
      )
      runReadActionBlocking {
        val file = myFixture.file as PyFile
        val byPrefix = file.topLevelClasses
          .map { PyClassTypeImpl(it, false) as PyType }
          .groupBy { it.name!!.first() }
        body(Fixture(TypeEvalContext.codeAnalysis(myFixture.project, file),
                     PyBuiltinCache.getInstance(file).intType!!,
                     byPrefix))
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private companion object {
    val WIDTHS = listOf(2, 3, 5, 8, 12, 20)
  }
}
