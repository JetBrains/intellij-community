// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.impl.InspectionVisitorOptimizer
import com.intellij.idea.TestFor
import com.intellij.psi.HintedPsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SyntaxTraverser
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The platform passes a Python inspection visitor only the PSI elements that its overridden visit methods handle.
 */
@TestFor(classes = [PyElementVisitor::class, InspectionVisitorOptimizer::class])
@Subsystems.Inspections
@Components.Inspection
@Layers.Functional
class PyInspectionVisitorOptimizerTest : PyCodeInsightTestCase() {

  @TestFor(issues = ["PY-92772"])
  @Test
  fun `a visitor of call expressions accepts only call expressions`() {
    val hinted = object : PsiElementVisitor(), HintedPsiElementVisitor {
      override fun getHintPsiElements(): List<Class<*>> = listOf(PyCallExpression::class.java)
      override fun visitElement(element: PsiElement) {}
    }
    assertEquals(listOf(PyCallExpression::class.java), InspectionVisitorOptimizer.getAcceptingPsiTypes(hinted), "The optimizer is off")

    val visitor = object : PyElementVisitor() {
      override fun visitPyCallExpression(node: PyCallExpression) {}
    }
    assertEquals(listOf(PyCallExpression::class.java), InspectionVisitorOptimizer.getAcceptingPsiTypes(visitor))
  }

  @TestFor(issues = ["PY-92772"])
  @Test
  fun `a visitor of expressions selects every expression of the file and no other element`() {
    val file = myFixture.configureByText("a.py", "print(len(items))\n")
    val elements = SyntaxTraverser.psiTraverser(file).toList()
    val visitor = object : PyElementVisitor() {
      override fun visitPyExpression(node: PyExpression) {}
    }
    val accepted = InspectionVisitorOptimizer(elements).filterElements(InspectionVisitorOptimizer.getAcceptingPsiTypes(visitor))
    assertEquals(elements.filterIsInstance<PyExpression>(), accepted)
  }
}
