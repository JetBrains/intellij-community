// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator

/**
 * Reports a PyCharm code in a `# type: ignore` or `# pycharm: ignore` comment that names no inspection.
 *
 * A PyCharm code has the `pycharm:` namespace, or it stands in a `# pycharm: ignore` comment. Such a code
 * suppresses nothing. A bare code in `# type: ignore` is not reported, because other type checkers use their
 * own codes there. A name that looks like a suppress id, such as `PyFoo`, is not reported either. See
 * [PyIgnoreCodeResolver.Resolution.Unknown].
 */
class PyUnknownIgnoreCodeInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
      override fun visitComment(comment: PsiComment) {
        val codes = PyIgnoreCommentUtil.parseCodes(comment) ?: return
        val refs = codes.occurrences.map { PyIgnoreCommentUtil.codeRef(codes.directive, it.rawCode) }
        val resolutions = refs.map { it?.let(PyIgnoreCodeResolver::resolve) }
        for ((index, occurrence) in codes.occurrences.withIndex()) {
          val ref = refs[index] ?: continue
          if (resolutions[index] != PyIgnoreCodeResolver.Resolution.Unknown) continue
          val fixes = if (canRemove(resolutions, index)) arrayOf<LocalQuickFix>(RemoveIgnoreCodeFix(index)) else LocalQuickFix.EMPTY_ARRAY
          holder.registerProblem(comment, occurrence.range, PyPsiBundle.message("INSP.unknown.ignore.code", ref.name), *fixes)
        }
      }
    }
  }
}

/**
 * The fix can remove the code at [index] when no code stays, or when a code that stays names a PyCharm code.
 * Only foreign codes left would make the comment suppress every inspection on its line.
 */
private fun canRemove(resolutions: List<PyIgnoreCodeResolver.Resolution?>, index: Int): Boolean {
  val remaining = resolutions.filterIndexed { i, _ -> i != index }
  return remaining.isEmpty() || remaining.any { it != null && it != PyIgnoreCodeResolver.Resolution.Foreign }
}

/**
 * Removes the code at [index] from the brackets of an ignore comment. When no other code stays, the fix removes
 * the whole comment instead, because a bare comment suppresses every inspection on its line. The text after the
 * brackets stays as a plain comment.
 */
private class RemoveIgnoreCodeFix(private val index: Int) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.unknown.ignore.code.remove.fix")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val comment = element as? PsiComment ?: return
    val codes = PyIgnoreCommentUtil.parseCodes(comment) ?: return
    val bracketContent = codes.bracketContent ?: return
    if (index >= codes.occurrences.size) return
    val text = comment.text
    val remaining = codes.occurrences.filterIndexed { i, _ -> i != index }
    if (remaining.isNotEmpty()) {
      val newCodes = remaining.joinToString(", ") { it.rawCode }
      replaceComment(project, comment, bracketContent.replace(text, newCodes))
      return
    }
    val tail = text.substring(bracketContent.endOffset + 1).trim()
    when {
      tail.isEmpty() -> {
        (comment.prevSibling as? PsiWhiteSpace)?.takeIf { !it.textContains('\n') }?.delete()
        comment.delete()
      }
      tail.startsWith("#") -> replaceComment(project, comment, tail)
      else -> replaceComment(project, comment, "# $tail")
    }
  }

  private fun replaceComment(project: Project, comment: PsiComment, newText: String) {
    val level = LanguageLevel.forElement(comment)
    comment.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment::class.java, newText))
  }
}
