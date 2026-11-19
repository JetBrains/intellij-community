// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import org.intellij.plugins.markdown.lang.supportsMarkdown

class MarkdownCompletionContributor: CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(MarkdownTokenTypes.FENCE_LANG), CodeFenceLanguageListCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (!context.file.supportsMarkdown()) return
    if (context.dummyIdentifier != CompletionInitializationContext.DUMMY_IDENTIFIER) return
    if (closeUnclosedLinkDestination(context)) return
    val element = PsiUtilCore.getElementAtOffset(context.file, context.startOffset)
    if (CodeFenceLanguageListCompletionProvider.isInMiddleOfUnCollapsedFence(element, context.startOffset)) {
      context.dummyIdentifier = fenceDummyIdentifier
    }
  }

  /** Closes the link in the completion copy so its file references can supply the variants. */
  private fun closeUnclosedLinkDestination(context: CompletionInitializationContext): Boolean {
    val text = context.file.viewProvider.contents
    val boundaryOffset = CharArrayUtil.shiftBackwardUntil(text, context.startOffset - 1, "<>(\r\n")
    val pointEnclosed = text.getOrNull(boundaryOffset) == '<'
    val openerOffset = if (pointEnclosed) boundaryOffset - 1 else boundaryOffset
    if (text.getOrNull(openerOffset) != '(' || text.getOrNull(openerOffset - 1) != ']') return false
    if (!isUnclosedLinkCandidate(context.file, openerOffset, context.startOffset)) return false
    context.dummyIdentifier = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + if (pointEnclosed) ">)" else ")"
    return true
  }

  private fun isUnclosedLinkCandidate(file: PsiFile, openerOffset: Int, offset: Int): Boolean {
    val parenthesis = file.findElementAt(openerOffset)?.takeIf { it.elementType == MarkdownTokenTypes.LPAREN } ?: return false
    val container = parenthesis.parent ?: return false
    if (container is MarkdownInlineLink) return false
    val caretElement = file.findElementAt(offset - 1) ?: return false
    return PsiTreeUtil.isAncestor(container, caretElement, false)
  }

  companion object {
    @JvmStatic
    val fenceDummyIdentifier by lazy { "${CompletionInitializationContext.DUMMY_IDENTIFIER}\n" }
  }
}
