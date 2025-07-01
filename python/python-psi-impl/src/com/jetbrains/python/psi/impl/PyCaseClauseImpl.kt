package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Ref
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator
import com.jetbrains.python.psi.PyCaseClause
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyPattern
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyCaseClauseImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyCaseClause {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyCaseClause(this)
  }

  override fun getCaptureTypeForChild(pattern: PyPattern, context: TypeEvalContext): PyType? {
    val matchStatement = getParent() as? PyMatchStatement ?: return null
    val subject = matchStatement.subject ?: return null

    var subjectType = context.getType(subject)
    for (cs in matchStatement.caseClauses) {
      if (cs === this) break
      if (cs.pattern == null) continue
      if (cs.guardCondition != null && !PyEvaluator.evaluateAsBoolean(cs.guardCondition, false)) continue
      if (cs.pattern!!.canExcludePatternType(context)) {
        subjectType = Ref.deref(
          PyTypeAssertionEvaluator.createAssertionType(subjectType, context.getType(cs.pattern!!), false, context))
      }
    }

    return subjectType
  }
}
