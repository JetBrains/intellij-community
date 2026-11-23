// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

internal class MarkdownIndentPastePreProcessor : CopyPastePreProcessor {
  override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
    return null
  }

  override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
    if ('\n' !in text || !file.supportsMarkdown()) {
      return text
    }
    val document = editor.document
    val offset = editor.selectionModel.selectionStart
    val element = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, offset) ?: return text
    val fence = MarkdownCodeFenceUtils.getCodeFence(element)
    val quote = PsiTreeUtil.getParentOfType(element, MarkdownBlockQuote::class.java)
    val indent = when {
      fence != null -> MarkdownCodeFenceUtils.getIndent(fence) ?: return text
      quote != null -> MarkdownPsiUtil.getBlockQuoteIndent(quote, document)
      else -> return text
    }
    if (indent.isEmpty() || !MarkdownCodeFenceUtils.getIndent(document, offset).startsWith(indent)) {
      return text
    }
    val markers = indent.trimEnd()
    return text.lineSequence()
      .mapIndexed { index, line -> if (index == 0 || markers.isNotEmpty() && line.startsWith(markers)) line else indent + line }
      .joinToString("\n")
  }

  override fun isReformatCodeBeforePaste(): Boolean {
    return false
  }
}
