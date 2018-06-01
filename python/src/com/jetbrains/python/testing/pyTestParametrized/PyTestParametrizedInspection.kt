// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Check that all parameters from parametrize decorator are declared by function
 */
class PyTestParametrizedInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement?) {
      if (element is PyFunction) {
        val requiredParameters = element
          .getParametersOfParametrized(TypeEvalContext.codeAnalysis(element.project, element.containingFile))
          .map(PyTestParameter::name)
        if (requiredParameters.isNotEmpty()) {
          val declaredParameters = element.parameterList.parameters.mapNotNull(PyParameter::getName)
          val diff = requiredParameters.minus(declaredParameters)
          if (diff.isNotEmpty()) {
            // Some params are not declared
            val problemSource = element.parameterList.lastChild ?: element.parameterList
            if (problemSource is PsiErrorElement || problemSource !is LeafPsiElement) {
              return // Error element can't be passed to registerProblem
            }
            holder.registerProblem(problemSource, "Following arguments are not declared but provided by decorator: $diff",
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
        }
      }
      super.visitElement(element)
    }
  }

  override fun getDisplayName(): String {
    return PyBundle.message("INSP.NAME.pytest-parametrized")
  }
}