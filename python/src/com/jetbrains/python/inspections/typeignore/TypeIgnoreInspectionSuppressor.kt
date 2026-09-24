// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PySuppressionUtil
import com.jetbrains.python.inspections.quickfix.PySuppressWithTypeIgnoreFix
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils

/**
 * Suppresses inspections on lines (or whole files) annotated with a `# type: ignore` or `# pycharm: ignore`
 * comment.
 *
 * A code in brackets can be one of these:
 *  - a suppress id (`PyTypeChecker`) or its kebab-case alias (`unresolved-references`), with an optional
 *    `pycharm:` prefix. It suppresses the whole inspection.
 *  - a granular type-checker code (`unsupported-operator`). [com.jetbrains.python.inspections.PyTypeCheckerProblemReporter]
 *    applies it. Here it only counts as a known code.
 *  - a foreign code, such as mypy's `attr-defined`. It is not a known code.
 *
 * A comment that names no PyCharm code suppresses every inspection on the line. The one exception is
 * [PyTypeIgnoreWithoutCodeInspection], which reports such a comment. Every bare code of a `# pycharm: ignore`
 * comment is a PyCharm code.
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
    // PyTypeChecker offers a suppress action per granular code, see PyTypeCheckerSuppressableProblemGroup.
    if (PySuppressionUtil.isCustomManaged(toolId)) return SuppressQuickFix.EMPTY_ARRAY
    return arrayOf(PySuppressWithTypeIgnoreFix.forInspection(toolId))
  }
}

private fun suppresses(comment: PsiComment, toolId: String): Boolean {
  val parsed = PyIgnoreCommentUtil.parse(comment) ?: return false
  var namesPyCharmCode = false
  for (ref in PyIgnoreCommentUtil.codeRefs(parsed)) {
    when (val resolution = PyIgnoreCodeResolver.resolve(ref)) {
      is PyIgnoreCodeResolver.Resolution.Inspection -> if (resolution.suppressId == toolId) return true
      PyIgnoreCodeResolver.Resolution.Foreign -> continue
      PyIgnoreCodeResolver.Resolution.Granular -> Unit
    }
    namesPyCharmCode = true
  }
  if (namesPyCharmCode) return false
  return toolId != PyTypeIgnoreWithoutCodeInspection.SUPPRESS_ID
}
