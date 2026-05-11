// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.supportsMarkdown

internal class MarkdownLinkPastePreProcessor : CopyPastePreProcessor {
  override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
    return null
  }

  override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
    val linkText = editor.selectionModel.selectedText ?: return text
    val linkDestination = MarkdownLinkEditingUtil.getLinkDestination(text) ?: return text
    if (linkText.isBlank() || !file.language.supportsMarkdown()) {
      return text
    }
    return MarkdownLinkEditingUtil.createInlineLink(linkText, linkDestination)
  }

  override fun isReformatCodeBeforePaste(): Boolean {
    return false
  }

  override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean {
    return false
  }
}
