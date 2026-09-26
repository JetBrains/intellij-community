// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor

/**
 * Reports a `# type: ignore` comment that names no known inspection code.
 *
 * Such a comment suppresses every inspection on its line, or in the whole file when it stands in the leading
 * comments of the file. It can therefore hide a problem that appears there later. The comment keeps this
 * effect. This inspection only asks the user to name the inspection codes.
 */
class PyTypeIgnoreWithoutCodeInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
      override fun visitComment(comment: PsiComment) {
        val targets = typeIgnoreTargets(comment) ?: return
        if (targets.isNotEmpty()) return
        val message = when (ignoreScope(comment)) {
          IgnoreScope.LINE -> PyPsiBundle.message("INSP.type.ignore.without.code.line")
          IgnoreScope.FILE -> PyPsiBundle.message("INSP.type.ignore.without.code.file")
          null -> return
        }
        registerProblem(comment, message)
      }
    }
  }

  companion object {
    const val SUPPRESS_ID: String = "PyTypeIgnoreWithoutCode"
  }
}
