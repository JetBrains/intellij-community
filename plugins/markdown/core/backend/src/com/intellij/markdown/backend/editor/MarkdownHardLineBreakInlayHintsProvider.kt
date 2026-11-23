// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
class MarkdownHardLineBreakInlayHintsProvider : InlayHintsProvider, DumbAware {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector

  private object Collector : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element.elementType != MarkdownTokenTypes.HARD_LINE_BREAK || !element.text.isBlank()) return
      sink.addPresentation(
        InlineInlayPosition(element.endOffset, relatedToPrevious = true),
        tooltip = MarkdownBundle.message("markdown.hard.line.break.inlay.tooltip"),
        hintFormat = HintFormat.default.withColorKind(HintColorKind.TextWithoutBackground),
      ) {
        text("⤦")
      }
    }
  }
}
