// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.backend.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownTablePipeInCodeSpanInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : MarkdownElementVisitor() {
      override fun visitTable(table: MarkdownTable) {
        super.visitTable(table)
        for (row in table.getRows(true)) {
          val rowText = row.text
          scanCodeSpansForPipes(rowText) { pipeOffsetInRow, contentStart, contentEnd ->
            val absRange = TextRange(row.textRange.startOffset + contentStart, row.textRange.startOffset + contentEnd)
            val rangePointer = SmartPointerManager.getInstance(row.project)
              .createSmartPsiFileRangePointer(row.containingFile, absRange)
            holder.registerProblem(
              row,
              TextRange(pipeOffsetInRow, pipeOffsetInRow + 1),
              MarkdownBundle.message("markdown.table.pipe.in.code.span.inspection.description"),
              EscapePipeInCodeSpanFix(rangePointer)
            )
          }
        }
      }
    }
  }

  private class EscapePipeInCodeSpanFix(
    @FileModifier.SafeFieldForPreview
    private val rangePointer: SmartPsiFileRange
  ) : LocalQuickFix {
    override fun getName(): String = MarkdownBundle.message("markdown.table.pipe.in.code.span.fix.text")
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val segment = rangePointer.range ?: return
      val document = PsiDocumentManager.getInstance(project).getDocument(descriptor.psiElement.containingFile) ?: return
      val content = document.getText(TextRange.create(segment))
      val newContent = content.replace(Regex("""(?<!\\)\|""")) { """\|""" }
      if (newContent != content) {
        document.replaceString(segment.startOffset, segment.endOffset, newContent)
      }
    }
  }

  /**
   * Scans [text] for backtick code spans and finds unescaped '|' inside them.
   * Calls [onPipeFound] with: pipe offset in text, content range start, content range end
   * (all relative to [text] start).
   */
  private fun scanCodeSpansForPipes(text: String, onPipeFound: (pipeOffset: Int, contentStart: Int, contentEnd: Int) -> Unit) {
    var i = 0
    while (i < text.length) {
      if (text[i] != '`') { i++; continue }
      val btStart = i
      while (i < text.length && text[i] == '`') i++
      val btCount = i - btStart
      val contentStart = i

      // Search for matching closing backtick sequence
      var j = contentStart
      var found = false
      while (j < text.length) {
        if (text[j] == '`') {
          val closeStart = j
          while (j < text.length && text[j] == '`') j++
          if (j - closeStart == btCount) {
            val contentEnd = closeStart
            for (k in contentStart until contentEnd) {
              if (text[k] == '|' && (k == contentStart || text[k - 1] != '\\')) {
                onPipeFound(k, contentStart, contentEnd)
              }
            }
            i = j
            found = true
            break
          }
        } else {
          j++
        }
      }
      if (!found) i = contentStart
    }
  }

}