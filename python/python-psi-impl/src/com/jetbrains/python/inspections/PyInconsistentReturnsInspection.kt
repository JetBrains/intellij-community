package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.extensions.isExhaustive
import com.jetbrains.python.inspections.quickfix.PyMakeReturnExplicitFix
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * PEP8:
 * Be consistent in return statements. Either all return statements in a function should return an expression,
 * or none of them should. If any return statement returns an expression, any return statements where no value
 * is returned should explicitly state this as return None, and an explicit return statement should be present
 * at the end of the function (if reachable).
 */
class PyInconsistentReturnsInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyFunction(node: PyFunction) {
        val returnPoints = node.getReturnPoints(myTypeEvalContext)
        val hasExplicitReturns = returnPoints.any { it is PyReturnStatement && it.expression != null }
        if (!hasExplicitReturns) return

        for (statement in returnPoints) {
          val message = when {
            statement is PyReturnStatement && statement.expression == null ->
              PyPsiBundle.message("INSP.inconsistent.returns.return.without.value")

            statement !is PyReturnStatement && !isTooComplexCase(statement, myTypeEvalContext) ->
              PyPsiBundle.message("INSP.inconsistent.returns.missing.return.stmt.on.some.paths")

            else -> continue
          }

          this.holder!!
            .problem(statement, message)
            .range(statement.firstLineRange() ?: statement.textRange)
            .fix(PyMakeReturnExplicitFix(statement)).register()
        }
      }
    }
  }
}

private fun isTooComplexCase(statement: PyStatement, context: TypeEvalContext): Boolean {
  val parent = statement.findParentInFile(withSelf = false) {
    it is ScopeOwner
    || it is PyTryExceptStatement
    || it is PyForPart
    || it is PyWhilePart
    || it is PyIfPart && it.parentOfType<PyIfStatement>()?.elsePart == null
    || (it is PyWithStatement && it.isSuppressingExceptions(context))
    || (it is PyMatchStatement && !it.isExhaustive(context))
  }
  return parent !is ScopeOwner
}

private fun PsiElement.firstLineRange(): TextRange? {
  val document = containingFile.fileDocument
  val startOffset = textRange.startOffset
  val lineEndOffset = document.getLineEndOffset(document.getLineNumber(startOffset))
  val endOffset = minOf(lineEndOffset, textRange.endOffset)

  return TextRange(0, endOffset - startOffset)
}