// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Suppresses inspections on lines (or whole files) annotated with a `# type: ignore` comment.
 *
 * A comment with a code in brackets suppresses only the inspection whose suppress id matches the code. The
 * code is the suppress id, with an optional `pycharm:` namespace prefix. A comment that names no known
 * inspection code suppresses every inspection on the line. The one exception is
 * [PyTypeIgnoreWithoutCodeInspection], which reports such a comment.
 */
class TypeIgnoreInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element is PsiFile) return false
    val containingFile = element.containingFile
    if (containingFile !is PyFile) return false

    val sameLineComment = PyPsiUtils.findSameLineComment(element)
    if (sameLineComment != null && suppresses(sameLineComment, toolId)) return true
    return containingFile.leadingFileLevelComments().any { suppresses(it, toolId) }
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}

private fun suppresses(comment: PsiComment, toolId: String): Boolean {
  val targets = typeIgnoreTargets(comment) ?: return false
  if (targets.isNotEmpty()) return toolId in targets
  return toolId != PyTypeIgnoreWithoutCodeInspection.SUPPRESS_ID
}
