// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyIgnoreCodeComputer
import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator

/**
 * Reports a `# type: ignore` or `# pycharm: ignore` comment that names no known inspection code.
 *
 * Such a comment suppresses every inspection on its line, or in the whole file when it stands in the leading
 * comments of the file. It can therefore hide a problem that appears there later. The comment keeps this
 * effect. This inspection only asks the user to name the inspection codes. For a comment on a line of code,
 * the quick fix adds the codes of the problems on that line.
 */
class PyTypeIgnoreWithoutCodeInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
      override fun visitComment(comment: PsiComment) {
        val parsed = PyIgnoreCommentUtil.parse(comment) ?: return
        if (PyIgnoreCodeResolver.namesPyCharmCode(parsed)) return
        when (ignoreScope(comment)) {
          IgnoreScope.LINE -> registerProblem(comment, PyPsiBundle.message("INSP.type.ignore.without.code.line"), AddInspectionCodesFix())
          IgnoreScope.FILE -> registerProblem(comment, PyPsiBundle.message("INSP.type.ignore.without.code.file"))
          null -> return
        }
      }
    }
  }

  companion object {
    const val SUPPRESS_ID: String = "PyTypeIgnoreWithoutCode"
  }
}

/**
 * Adds the codes of the problems on the line of the comment to its brackets. [PyIgnoreCodeComputer] finds the
 * codes. The fix keeps the codes that the comment already lists and the text after the brackets.
 */
private class AddInspectionCodesFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("INSP.type.ignore.add.code.fix")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val comment = element as? PsiComment ?: return
    val originalFile = comment.containingFile?.originalFile ?: return
    val document = PsiDocumentManager.getInstance(project).getDocument(originalFile) ?: return
    val lineNumber = document.getLineNumber(comment.textRange.startOffset)

    val codes = PyIgnoreCodeComputer.codesOnLine(originalFile, lineNumber, setOf(PyTypeIgnoreWithoutCodeInspection.SUPPRESS_ID))
    val newText = mergeCodes(comment.text, codes)
    if (newText == comment.text) return

    val level = LanguageLevel.forElement(comment)
    comment.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment::class.java, newText))
  }

  private fun mergeCodes(commentText: String, codes: List<String>): String {
    if (codes.isEmpty()) return commentText
    val afterIgnore = commentText.lowercase().indexOf("ignore").let { if (it < 0) return commentText else it + "ignore".length }
    val open = commentText.indexOf('[', afterIgnore)
    if (open < 0) {
      return commentText.substring(0, afterIgnore) + "[" + codes.joinToString(", ") + "]" + commentText.substring(afterIgnore)
    }
    val close = commentText.indexOf(']', open)
    if (close < 0) return commentText
    val merged = LinkedHashSet<String>()
    commentText.substring(open + 1, close).split(',').forEach { val c = it.trim(); if (c.isNotEmpty()) merged.add(c) }
    merged.addAll(codes)
    return commentText.substring(0, open + 1) + merged.joinToString(", ") + commentText.substring(close)
  }
}
