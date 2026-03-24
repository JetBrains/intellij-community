/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.inspections.quickfix.PyRemoveCallQuickFix
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyCallingNonCallableInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  class Visitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)
      checkCallable(node, node.callee)
    }

    override fun visitPyDecorator(decorator: PyDecorator) {
      super.visitPyDecorator(decorator)
      checkCallable(decorator, decorator.callee)
      if (decorator.hasArgumentList()) {
        checkCallable(decorator, decorator)
      }
    }

    private fun checkCallable(node: PyElement, callee: PyExpression?) {
      if (node.parent is PyDecorator) return // we've already been here

      if (callee != null && isCallable(callee, myTypeEvalContext) == false) {
        val calleeType = myTypeEvalContext.getType(callee)
        val message = when {
          calleeType is PyClassType -> PyPsiBundle.message("INSP.class.object.is.not.callable", calleeType.name)
          callee.name != null -> PyPsiBundle.message("INSP.symbol.is.not.callable", callee.name)
          else -> PyPsiBundle.message("INSP.expression.is.not.callable")
        }
        registerProblem(node, message, PyRemoveCallQuickFix())
      }
    }
  }
}

private fun isCallable(element: PyExpression, context: TypeEvalContext): Boolean? {
  if (element is PyQualifiedExpression && PyNames.__CLASS__ == element.name) return true

  if (element is PyReferenceExpression) {
    val resolved = element.getReference(PyResolveContext.defaultContext(context)).resolve()
    if (resolved is PyTargetExpression) {
      if (isExplicitTypeAliasWithNonCallableValue(resolved, context)) return false
      // TODO: Handle implicit aliases
    }
  }

  return PyTypeChecker.isCallable(context.getType(element))
}

private fun isExplicitTypeAliasWithNonCallableValue(target: PyTargetExpression, context: TypeEvalContext): Boolean {
  if (!PyTypingTypeProvider.isExplicitTypeAlias(target, context)) return false

  val aliasExpr = target.findAssignedValue() ?: return false

  // Union type cannot be instantiated: https://docs.python.org/3/library/typing.html#typing.Union
  return context.getType(aliasExpr) is PyUnionType
}
