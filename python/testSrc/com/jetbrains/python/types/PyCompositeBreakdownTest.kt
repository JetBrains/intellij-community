// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyMismatchStep
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-91327"], classes = [PyTypeChecker::class])
class PyCompositeBreakdownTest : PyCodeInsightTestCase() {
  enum class Shape {
    ACTUAL_UNION, EXPECTED_UNION, ACTUAL_INTERSECTION, EXPECTED_INTERSECTION, EXPECTED_UNSAFE_UNION,
  }

  @ParameterizedTest
  @EnumSource(Shape::class)
  fun `a composite past the bound has only a summary and costs no extra matches`(shape: Shape) {
    withComposite(shape, 2) { expected, actual, context, steps ->
      assertFalse(PyTypeChecker.match(expected, actual, context))
      val verdictCost = steps.getAndSet(0)
      val explanation = PyTypeChecker.explainMismatch(expected, actual, context)!!
      val breakdownCost = steps.get()

      val step = explanation.step
      when (shape) {
        Shape.ACTUAL_INTERSECTION, Shape.EXPECTED_INTERSECTION -> {
          assertTrue(step is PyMismatchStep.NoIntersectionMember, explanation.message.description)
          assertEquals(shape == Shape.EXPECTED_INTERSECTION, (step as PyMismatchStep.NoIntersectionMember).expectedIsIntersection)
        }
        else -> {
          assertTrue(step is PyMismatchStep.NoUnionMember, explanation.message.description)
          assertEquals(shape == Shape.ACTUAL_UNION, (step as PyMismatchStep.NoUnionMember).actualIsUnion)
        }
      }
      assertEquals(0, explanation.children.size, "Only the summary must remain")
      assertEquals(verdictCost, breakdownCost, "Discarding a breakdown must also skip its nested work")
    }
  }

  @ParameterizedTest
  @EnumSource(Shape::class)
  fun `a composite at the bound retains each member reason`(shape: Shape) {
    withComposite(shape, 3) { expected, actual, context, _ ->
      val explanation = PyTypeChecker.explainMismatch(expected, actual, context)!!
      assertEquals(3, explanation.children.size, "Each incompatible member must keep its reason")
    }
  }

  private fun withComposite(
    shape: Shape,
    limit: Int,
    body: (PyType?, PyType?, TypeEvalContext, AtomicLong) -> Unit,
  ) {
    myFixture.configureByText("a.py", """
      from typing import Protocol
      class A: pass
      class B: pass
      class D: pass
      class C:
          value: A | B
      class P1(Protocol):
          value: int
      class P2(Protocol):
          value: str
      class P3(Protocol):
          value: bytes
    """.trimIndent())
    val disposable = Disposer.newDisposable()
    val steps = AtomicLong()
    try {
      Registry.get("python.typing.composite.single.pass").setValue(true, disposable)
      Registry.get("python.typing.composite.breakdown.max.members").setValue(limit, disposable)
      val settings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
      settings.setSetting(PyUnionType.STRICT_UNIONS_SETTING, true, disposable)
      val extension = PyTypeCheckerExtension { _, _, _, _ ->
        steps.incrementAndGet()
        Optional.empty()
      }
      ExtensionTestUtil.maskExtensions(PyTypeCheckerExtension.EP_NAME,
                                      PyTypeCheckerExtension.EP_NAME.extensionList + extension, disposable)
      runReadActionBlocking {
        val file = myFixture.file as PyFile
        val types = file.topLevelClasses.associate { it.name to PyClassTypeImpl(it, false) }
        val actualMembers = listOf(types.getValue("A"), types.getValue("B"), types.getValue("D"))
        val expectedMembers = listOf(types.getValue("P1"), types.getValue("P2"), types.getValue("P3"))
        val (expected, actual) = when (shape) {
          Shape.ACTUAL_UNION -> types.getValue("P1") to PyUnionType.unionOrUnknown(actualMembers)
          Shape.EXPECTED_UNION -> PyUnionType.unionOrUnknown(expectedMembers) to types.getValue("C")
          Shape.ACTUAL_INTERSECTION -> types.getValue("P1") to PyIntersectionType.intersectionOrTop(actualMembers)
          Shape.EXPECTED_INTERSECTION -> PyIntersectionType.intersectionOrTop(expectedMembers) to types.getValue("C")
          Shape.EXPECTED_UNSAFE_UNION -> PyUnsafeUnionType.unsafeUnion(expectedMembers) to types.getValue("C")
        }
        steps.set(0)
        body(expected, actual, TypeEvalContext.codeAnalysis(myFixture.project, file), steps)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
