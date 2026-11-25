// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.Traversal
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.PyTypeUtil.isSameType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAnyOrUnknown
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyUnnecessaryCastInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : PyInspectionVisitor(holder, context) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callee = callExpression.callee ?: return
        val callables = PyTypeUtil.getCallableItems(context.getType(callee))
        val isCastCall = callables.map { it.callable?.qualifiedName }.any {
          it == PyTypingTypeProvider.CAST || it == PyTypingTypeProvider.CAST_EXT
        }
        if (!isCastCall) return

        val args = callExpression.getArguments()
        if (args.size != 2) return
        val targetTypeRef = PyTypingTypeProvider.getType(args[0], myTypeEvalContext)
        val targetType = Ref.deref(targetTypeRef)
        val value = args[1]
        val actualType: PyType? = myTypeEvalContext.getType(value)

        if (!isRedundantCast(targetType, actualType, myTypeEvalContext) &&
            !isSameAsWidenedLiteral(targetType, value, myTypeEvalContext)) return
        val toName = PythonDocumentationProvider.getTypeName(targetType, myTypeEvalContext)
        registerProblem(
          callExpression,
          PyPsiBundle.message(
            "INSP.unnecessary.cast.message",
            toName
          ),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          null,
          TextRange(0, callExpression.arguments[0].nextSibling.endOffset - callExpression.startOffset),
          RemoveUnnecessaryCastQuickFix(),
        )
        registerProblem(
          callExpression,
          PyPsiBundle.message(
            "INSP.unnecessary.cast.message",
            toName
          ),
          ProblemHighlightType.INFORMATION,
          null,
          RemoveUnnecessaryCastQuickFix(),
        )
      }
    }
  }
}

/**
 * Returns true when a cast of a value of [actualType] to [targetType] changes nothing.
 *
 * `Any` matches every type in both directions.
 * When one of the two types contains `Any` or Unknown, the cast is redundant only when the two types are equal.
 */
private fun isRedundantCast(targetType: PyType?, actualType: PyType?, context: TypeEvalContext): Boolean {
  if (containsAnyOrUnknown(targetType, context) || containsAnyOrUnknown(actualType, context)) {
    return targetType == actualType
  }
  return targetType.isSameType(actualType, context)
}

private fun containsAnyOrUnknown(root: PyType?, context: TypeEvalContext): Boolean {
  var found = false
  PyRecursiveTypeVisitor.traverse(root, context, object : PyTypeTraverser() {
    override fun visitPyType(type: PyType): Traversal {
      if (type is PyAnyType) {
        found = true
        return Traversal.TERMINATE
      }
      return Traversal.CONTINUE
    }

    override fun visitPyCallableType(callableType: PyCallableType): Traversal {
      // `Callable[..., R]` has no parameter list, and it takes any arguments
      if (callableType.getParameters(context) == null) {
        found = true
        return Traversal.TERMINATE
      }
      return visitPyType(callableType)
    }

    override fun visitUnknownType(): Traversal {
      found = true
      return Traversal.TERMINATE
    }
  })
  return found
}

/**
 * Returns true when [value] is a literal expression and [targetType] is the class of its literal type, as in `cast(int, 1)`.
 */
private fun isSameAsWidenedLiteral(targetType: PyType?, value: PyExpression, context: TypeEvalContext): Boolean {
  val literalExpression = PyPsiUtils.flattenParens(value) ?: return false
  val literalType = PyLiteralType.getLiteralType(literalExpression, context) ?: return false
  // A conditional expression with a branch that is not a literal gives `Unknown` for that branch.
  if (literalType.asUnionSequence().any { it.isAnyOrUnknown }) return false
  return targetType.isSameType(PyLiteralType.upcastLiteralToClass(literalType), context)
}

private class RemoveUnnecessaryCastQuickFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.remove.cast.call")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val call = element as? PyCallExpression ?: return
    val args = call.getArguments()
    if (args.size != 2) return
    val expr = args[1] ?: return
    call.replace(expr.copy())
  }
}
