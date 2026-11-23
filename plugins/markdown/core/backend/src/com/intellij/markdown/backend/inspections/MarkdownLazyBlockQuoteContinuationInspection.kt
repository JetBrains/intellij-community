// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.isAirPrompt
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownLazyBlockQuoteContinuationInspection : LocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    // An Air prompt quotes chat messages with `>`, so text after a quote is intentional there
    return file.supportsMarkdown() && !file.viewProvider.virtualFile.isAirPrompt()
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : MarkdownElementVisitor() {
      override fun visitParagraph(paragraph: MarkdownParagraph) {
        super.visitParagraph(paragraph)
        PsiTreeUtil.findFirstParent(paragraph, true) { it.elementType in QUOTES } ?: return
        val content = nextLineContent(paragraph) ?: return
        val lazyParagraph = PsiTreeUtil.getParentOfType(content, MarkdownParagraph::class.java) ?: return

        val text = holder.file.viewProvider.contents
        val start = paragraph.textRange.startOffset
        val prefix = text.subSequence(StringUtil.lastIndexOf(text, '\n', 0, start) + 1, start)
          .map { if (it == '>' || it.isWhitespace()) it else ' ' }
          .joinToString("")
        holder.registerProblem(
          lazyParagraph,
          MarkdownBundle.message("markdown.lazy.block.quote.continuation.inspection.description"),
          AddBlockQuoteMarkersFix(prefix)
        )
      }
    }
  }

  /**
   * Replaces the leading whitespace of each line of the paragraph with [prefix], the markers of the quoted paragraph.
   */
  private class AddBlockQuoteMarkersFix(private val prefix: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = MarkdownBundle.message("markdown.lazy.block.quote.continuation.quick.fix.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val document = element.containingFile.fileDocument
      val text = document.charsSequence
      val range = element.textRange
      for (line in document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)) {
        val lineStart = document.getLineStartOffset(line)
        document.replaceString(lineStart, StringUtil.skipWhitespaceForward(text, lineStart), prefix)
      }
    }
  }
}

private val QUOTES = TokenSet.create(MarkdownElementTypes.BLOCK_QUOTE, MarkdownElementTypes.ALERT)

/** The first content leaf of the line after [element], or `null` when that line is blank or absent. */
private fun nextLineContent(element: PsiElement): PsiElement? {
  var newLines = 0
  var leaf = PsiTreeUtil.nextLeaf(element)
  while (leaf != null && leaf.elementType in MarkdownTokenTypeSets.WHITE_SPACES) {
    newLines += StringUtil.countNewLines(leaf.node.chars)
    if (newLines > 1) return null
    leaf = PsiTreeUtil.nextLeaf(leaf)
  }
  return leaf.takeIf { newLines == 1 }
}
