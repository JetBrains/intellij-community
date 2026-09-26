// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixture
import com.jetbrains.python.testing.pyTestFixtures.USE_FIXTURES
import com.jetbrains.python.testing.pyTestFixtures.getFixtureLink
import com.jetbrains.python.testing.pyTestFixtures.isFixture
import com.jetbrains.python.testing.pyTestFixtures.resolve

/**
 * Check that all parameters from parametrize decorator are declared by function
 */
class PyTestParametrizedInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PyInspectionVisitor(holder, getContext(session)) {
    override fun visitElement(element: PsiElement) {
      if (element is PyFunction) {
        val requiredParameters = element
          .getParametersOfParametrized(myTypeEvalContext)
          .map(PyTestParameter::name)
        if (requiredParameters.isNotEmpty()) {
          val declaredInSignature = element.parameterList.parameters.mapNotNull(PyParameter::getName).toSet()
          val reachableFixtures = collectReachableFixtureNames(element, myTypeEvalContext)
          val declaredOrCovered = declaredInSignature + reachableFixtures
          val diff = requiredParameters.filter { it !in declaredOrCovered }
          if (diff.isNotEmpty()) {
            // Some params are not declared
            val problemSource = element.parameterList.lastChild ?: element.parameterList
            if (problemSource is PsiErrorElement || problemSource !is LeafPsiElement) {
              return // Error element can't be passed to registerProblem
            }
            holder.registerProblem(problemSource, PyPsiBundle.message("INSP.arguments.not.declared.but.provided.by.decorator", diff),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
        }
      }
      super.visitElement(element)
    }
  }

  private fun collectReachableFixtureNames(func: PyFunction, ctx: TypeEvalContext): Set<String> {
    ModuleUtilCore.findModuleForPsiElement(func) ?: return emptySet()

    // Seeds: fixtures used directly by the test function
    val seedFixtures = mutableListOf<PyTestFixture>()

    // a) From parameters
    func.parameterList.parameters
      .filterIsInstance<PyNamedParameter>()
      .forEach { param ->
        if (param.isFixture(ctx)) {
          getFixtureLink(param, ctx)?.fixture?.let { seedFixtures.add(it) }
        }
      }

    // b) From @pytest.mark.usefixtures
    func.decoratorList?.decorators
      ?.firstOrNull { it.name == USE_FIXTURES }
      ?.argumentList?.arguments
      ?.forEach { arg ->
        val expr = when (arg) {
                     is PyStringLiteralExpression -> arg
                     is PyQualifiedExpression -> {
                       // convert qualified expr to string literal like in fixtures resolver
                       val qName = arg.asQualifiedName()?.toString() ?: return@forEach
                       PyElementGenerator.getInstance(arg.project).createStringLiteralFromString(qName)
                     }
                     else -> null
                   } ?: return@forEach
        val link = resolve(expr)?.let { resolvedStr ->
          // getFixtureLink accepts PyElement; we can directly call getFixtureLink on the string literal
          getFixtureLink(resolvedStr, ctx)
        } ?: getFixtureLink(expr, ctx)
        link?.fixture?.let { seedFixtures.add(it) }
      }

    val result = mutableSetOf<String>()
    val queue: ArrayDeque<PyTestFixture> = ArrayDeque()

    seedFixtures.forEach { ft ->
      result.add(ft.name)
      queue.add(ft)
    }

    val visited = mutableSetOf<PyFunction>()
    while (queue.isNotEmpty()) {
      val ft = queue.removeFirst()
      val fFunc = ft.function ?: continue
      if (!visited.add(fFunc)) continue

      fFunc.parameterList.parameters
        .filterIsInstance<PyNamedParameter>()
        .forEach { param ->
          if (param.isFixture(ctx)) {
            getFixtureLink(param, ctx)?.fixture?.let { dep ->
              if (result.add(dep.name)) {
                queue.add(dep)
              }
            }
          }
        }
    }

    return result
  }
}