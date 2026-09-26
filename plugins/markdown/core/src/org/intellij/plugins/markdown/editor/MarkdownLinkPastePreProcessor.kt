// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.supportsMarkdown

internal class MarkdownLinkPastePreProcessor : CopyPastePreProcessor {
  override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
    return null
  }

  override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
    val linkText = editor.selectionModel.selectedText ?: return text
    val linkDestination = MarkdownLinkEditingUtil.getLinkDestination(text) ?: return text
    val selectionRange = TextRange(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
    if (linkText.isBlank() || !file.supportsMarkdown(selectionRange) || isInIgnoredContext(file, selectionRange)) {
      return text
    }
    return MarkdownLinkEditingUtil.createInlineLink(linkText, linkDestination)
  }

  override fun isReformatCodeBeforePaste(): Boolean {
    return false
  }

  private fun isInIgnoredContext(file: PsiFile, selectionRange: TextRange): Boolean {
    val contextFile = file.viewProvider.getPsi(MarkdownLanguage.INSTANCE) ?: file
    val startElement = contextFile.findElementAt(selectionRange.startOffset) ?: return false
    val endElement = contextFile.findElementAt(selectionRange.endOffset - 1) ?: return false
    return sequenceOf(startElement, endElement).any { element ->
      // Markdown PSI can return an embedded HTML element at the requested offset.
      !element.supportsMarkdown() || element.parents(withSelf = true).any { it.elementType in ignoredElementTypes }
    }
  }

  companion object {
    private val ignoredElementTypes = setOf(
      MarkdownElementTypes.CODE_FENCE,
      MarkdownElementTypes.CODE_BLOCK,
      MarkdownElementTypes.CODE_SPAN,
      MarkdownElementTypes.HTML_BLOCK,
      MarkdownElementTypes.LINK_TEXT,
      MarkdownElementTypes.LINK_DESTINATION,
      MarkdownElementTypes.LINK_DEFINITION,
      MarkdownElementTypes.INLINE_LINK,
      MarkdownElementTypes.FULL_REFERENCE_LINK,
      MarkdownElementTypes.SHORT_REFERENCE_LINK,
      MarkdownElementTypes.IMAGE,
      MarkdownElementTypes.TEST_LINK,
      MarkdownElementTypes.AUTOLINK,
      MarkdownElementTypes.FRONT_MATTER_HEADER,
      MarkdownTokenTypes.GFM_AUTOLINK,
    )
  }
}
