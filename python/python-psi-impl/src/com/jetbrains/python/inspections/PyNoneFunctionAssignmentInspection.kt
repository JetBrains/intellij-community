// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parents
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.quickfix.PyRemoveAssignmentQuickFix
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * User: ktisha
 * 
 * pylint E1111
 * 
 * Used when an assignment is done on a function call but the inferred function doesn't return anything.
 * Also reports when the result of such a function is used in other contexts (e.g., as an argument, in f-strings).
 */
class PyNoneFunctionAssignmentInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    private val myHasInheritors = mutableMapOf<PyFunction, Boolean>()

    override fun visitPyCallExpression(call: PyCallExpression) {
      if (!call.isReturnValueUsed()) {
        return
      }

      val type = myTypeEvalContext.getType(call)
      val callee = call.callee

      if (!type.isNoneType || callee == null) return
      val callables = call.multiResolveCalleeFunction(resolveContext)
      if (callables.isEmpty() || callables.any { callable ->
          !myTypeEvalContext.getReturnType(callable).isNoneType ||
          PythonSdkUtil.isElementInSkeletons(callable) ||
          callable is PyFunction && callable.hasInheritors()
        }) return
      val parent = call.parent
      if (parent is PyAssignmentStatement) {
        registerProblem(
          parent, PyPsiBundle.message("INSP.none.function.assignment", callee.name),
          PyRemoveAssignmentQuickFix()
        )
      }
      else {
        registerProblem(call, PyPsiBundle.message("INSP.none.function.assignment", callee.name))
      }
    }

    fun PyFunction.hasInheritors(): Boolean =
      myHasInheritors.getOrPut(this) { PyOverridingMethodsSearch.search(this, true).findFirst() != null }

    /**
     * Checks if the return value of the call expression is used.
     * Returns false if the call is a standalone expression statement.
     */
    private fun PyCallExpression.isReturnValueUsed(): Boolean {
      // Standalone expression statement - value not used
      return getParentSkippingParentheses() !is PyExpressionStatement
    }

    private fun PsiElement.getParentSkippingParentheses(): PsiElement =
      parents(withSelf = false)
        .first { it !is PyParenthesizedExpression }
  }
}
