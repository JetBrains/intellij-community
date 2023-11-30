// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.findParentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.TypeEvalContext


/**
 * Reports if a fixture is used without being passed to test function parameters.
 * Suggests a quick fix "Add fixture to test function parameters"
 */
class PyTestUnpassedFixtureInspection : PyInspection() {

  /**
   * Describes an `element` type into which `element: PyReferenceExpression` resolves.
   *
   * Type is `PyFixture` -> `FIXTURE`
   *
   * Type is unresolved -> `NONE`
   *
   * else -> `OTHER`
   */
  private enum class ResolveType {
    FIXTURE,
    NONE,
    OTHER
  }

  private val quickFix = AddFixtureToFuncParametersQuickFix()

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private fun findTestFunc(element: PyReferenceExpression) = element.findParentOfType<PyFunction>()

  private fun isDecorator(element: PyReferenceExpression) = element.findParentOfType<PyDecorator>() != null

  private inner class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyReferenceExpression(element: PyReferenceExpression) {
      if (isDecorator(element)) return

      val testFunction = findTestFunc(element) ?: return
      // no warning if a fixture is in test function parameters
      testFunction.parameterList.parameters.find { it.name == element.name }?.let { return }

      // no warning if a fixture is in '@pytest.mark.usefixtures' arguments
      if (isParameterInDecorator(element, testFunction)) return

      // no warning if an element has type OTHER
      if (getType(element) == ResolveType.OTHER) return

      // try to find a fixture from conftest.py file
      getFixtureLink(element, myTypeEvalContext) ?: return

      holder?.registerProblem(element, PyPsiBundle.message("INSP.use.fixture.without.declaration.in.test.function", element.text),
                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                              quickFix)
    }

    private fun getType(element: PyReferenceExpression): ResolveType {
      val type = myTypeEvalContext.getType(element) ?: return ResolveType.NONE
      val fixture = (type as? PyFunctionType)?.callable
      if (fixture is PyFunction && fixture.isFixture()) {
        return ResolveType.FIXTURE
      }
      return ResolveType.OTHER
    }

    private fun isParameterInDecorator(element: PyReferenceExpression, testFunction: PyFunction): Boolean {
      val pytestUseFixturesDecorator = testFunction.decoratorList?.decorators?.find { it.name == USE_FIXTURES }
      return pytestUseFixturesDecorator?.argumentList?.arguments?.find { it is PyStringLiteralExpression && it.stringValue == element.name } != null
    }
  }

  private inner class AddFixtureToFuncParametersQuickFix : LocalQuickFix {
    override fun getFamilyName() = PyPsiBundle.message("QFIX.add.fixture.to.test.function.parameters.list")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement as? PyReferenceExpression ?: return
      val newParameter = PyElementGenerator.getInstance(project).createParameter(element.text)
      findTestFunc(element)?.parameterList?.addParameter(newParameter)
    }
  }
}