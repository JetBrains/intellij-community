// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator
import com.jetbrains.python.psi.PyCaseClause
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyPattern
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyCaseClauseImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyCaseClause, PyCaptureContext {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyCaseClause(this)
  }

  override fun getCaptureTypeForChild(pattern: PyPattern, context: TypeEvalContext): PyType? {
    return getSubjectTypeBefore(context)
  }

  private fun getSubjectTypeBefore(context: TypeEvalContext): PyType? {
    val prevClause = PsiTreeUtil.getPrevSiblingOfType(this, PyCaseClause::class.java)
    if (prevClause != null) {
      return prevClause.getSubjectTypeAfter(context)
    }
    else {
      val matchStatement = parent as? PyMatchStatement ?: return null
      val subject = matchStatement.subject ?: return null
      return context.getType(subject)
    }
  }

  override fun getSubjectTypeAfter(context: TypeEvalContext): PyType? {
    fun getSubjectTypeAfterNoCache(): PyType? {
      val beforeType = getSubjectTypeBefore(context)
      val pattern = pattern ?: return beforeType
      if (guardCondition != null && !PyEvaluator.evaluateAsBoolean(guardCondition, false)) {
        return beforeType
      }
      // because subject can be 'Any', and then negative narrowing won't help
      if (pattern.isIrrefutable) return PyNeverType.NEVER

      if (pattern.canExcludePatternType(context)) {
        val patternType = context.getType(pattern)
        val narrowing = PyTypeAssertionEvaluator.createAssertionType(beforeType, patternType, false, true, context)
        if (narrowing != null) {
          return narrowing.get()
        }
      }
      return beforeType
    }

    return PyUtil.getNullableParameterizedCachedValue(this, context) { getSubjectTypeAfterNoCache() }
  }
}
