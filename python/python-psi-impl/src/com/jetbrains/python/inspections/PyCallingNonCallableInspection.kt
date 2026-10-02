// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
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
import com.jetbrains.python.psi.types.PyTypeChecker.isCallable
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyCallingNonCallableInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context)
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
      if (callee == null) return

      if (callee.isCallable(myTypeEvalContext) != false) return
      val calleeType = myTypeEvalContext.getType(callee)
      if (calleeType is PyUnionType && PyUnionType.isStrictSemanticsEnabled()) {
        val uncallable = PyUnionType.unionOrUnknown(calleeType.members.filter { it.isCallable == false })
        val message =
          PyPsiBundle.problemMessage(if (uncallable is PyUnionType) "INSP.members.are.not.callable" else "INSP.member.is.not.callable",
                                     CodifiedParam.ofType(uncallable, callee, myTypeEvalContext),
                                     CodifiedParam.ofType(calleeType, callee, myTypeEvalContext))
        registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, PyRemoveCallQuickFix())
        return
      }
      val message = when {
        calleeType is PyClassType -> PyPsiBundle.problemMessage("INSP.class.object.is.not.callable", calleeType.name)
        callee.name != null -> PyPsiBundle.problemMessage("INSP.symbol.is.not.callable", callee.name)
        else -> PyPsiBundle.problemMessage("INSP.expression.is.not.callable")
      }
      registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, PyRemoveCallQuickFix())
    }
  }
}

private fun PyExpression.isCallable(context: TypeEvalContext): Boolean? {
  if (this is PyQualifiedExpression && PyNames.__CLASS__ == this.name) return true

  if (this is PyReferenceExpression) {
    // PEP 747: `TypeForm(...)` is callable, even though the declared type of `TypeForm` is the non-callable `_SpecialForm`.
    val qNames = PyTypingTypeProvider.resolveToQualifiedNames(this, context)
    if (PyTypingTypeProvider.TYPE_FORM in qNames || PyTypingTypeProvider.TYPE_FORM_EXT in qNames) {
      return true
    }
    val resolved = this.getReference(PyResolveContext.defaultContext(context)).resolve()
    if (resolved is PyTargetExpression) {
      if (isExplicitTypeAliasWithNonCallableValue(resolved, context)) return false
      // TODO: Handle implicit aliases
    }
  }

  return context.getType(this).isCallable
}

private fun isExplicitTypeAliasWithNonCallableValue(target: PyTargetExpression, context: TypeEvalContext): Boolean {
  if (!PyTypingTypeProvider.isExplicitTypeAlias(target, context)) return false

  val aliasExpr = target.findAssignedValue() ?: return false

  // Union type cannot be instantiated: https://docs.python.org/3/library/typing.html#typing.Union
  return context.getType(aliasExpr) is PyUnionType
}
