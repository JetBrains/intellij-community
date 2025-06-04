// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner

/**
 * Detects unreachable code using control flow graph
 */
class PyUnreachableCodeInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitElement(element: PsiElement) {
        if (element is ScopeOwner) {
          for (e in PyInspectionsUtil.collectUnreachable(element, null, myTypeEvalContext)) {
            registerProblem(e, PyPsiBundle.message("INSP.unreachable.code"))
          }
        }
      }
    }
  }
}
