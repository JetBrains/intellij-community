// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.Reachability
import com.jetbrains.python.codeInsight.controlflow.getReachabilityForInspection
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
        if (node.parent.getReachabilityForInspection(myTypeEvalContext) == Reachability.UNREACHABLE) return
        if (node.getReachabilityForInspection(myTypeEvalContext) == Reachability.UNREACHABLE) {
          registerProblem(node, PyPsiBundle.message("INSP.unreachable.code"))
        }
      }

      override fun visitPyStatement(node: PyStatement) {
        if (node.parent.getReachabilityForInspection(myTypeEvalContext) == Reachability.UNREACHABLE) return
        if (node.getReachabilityForInspection(myTypeEvalContext) == Reachability.UNREACHABLE) {
          registerProblem(node, PyPsiBundle.message("INSP.unreachable.code"))
        }
      }
    }
  }
}