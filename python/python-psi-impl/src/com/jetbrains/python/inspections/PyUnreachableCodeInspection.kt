// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.isUnreachableForInspection
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList

/**
 * Detects unreachable code using the control flow graph
 */
class PyUnreachableCodeInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyStatementList(node: PyStatementList) {
        if (node.parent.isUnreachableForInspection(myTypeEvalContext)) return
        if (node.isUnreachableForInspection(myTypeEvalContext)) {
          registerProblem(node, PyPsiBundle.message("INSP.unreachable.code"), ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
      }

      override fun visitPyStatement(node: PyStatement) {
        if (node.parent.isUnreachableForInspection(myTypeEvalContext)) return
        if (node.isUnreachableForInspection(myTypeEvalContext)) {
          registerProblem(node, PyPsiBundle.message("INSP.unreachable.code"), ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
      }
    }
  }
}