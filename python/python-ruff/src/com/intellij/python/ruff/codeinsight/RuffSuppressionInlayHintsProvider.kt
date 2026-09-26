// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.CODE_PATTERN
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.SUPPRESSION_PATTERN
import com.jetbrains.python.PyTokenTypes

/**
 * Provides inlay hints for Ruff suppression codes in comments.
 * Displays the rule name for each suppression code.
 * For example: `# noqa: F108` will show `# noqa: F108 (unused-value)`
 */
class RuffSuppressionInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

  private class Collector : SharedBypassCollector {
    val hintFormat = HintFormat.default
      .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element is PsiComment && element.elementType == PyTokenTypes.END_OF_LINE_COMMENT) {
        processComment(element, sink)
      }
    }

    private fun processComment(comment: PsiComment, sink: InlayTreeSink) {
      val commentText = comment.text
      val matcher = SUPPRESSION_PATTERN.find(commentText)?.groups[1] ?: return

      for (match in CODE_PATTERN.findAll(matcher.value)) {
        val code = match.value
        val ruleName = comment.project.service<RuffService>().ruleInformation[code]?.name ?: return

        sink.addPresentation(
          position = InlineInlayPosition(comment.startOffset + matcher.range.first + match.range.last + 1, false),
          hintFormat = hintFormat
        ) {
          text(ruleName)
        }
      }
    }
  }
}
