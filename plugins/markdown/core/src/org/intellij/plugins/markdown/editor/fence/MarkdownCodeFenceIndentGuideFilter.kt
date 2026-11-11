// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePassFilter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

internal class MarkdownCodeFenceIndentGuideFilter : IndentGuidePassFilter {
  override fun shouldRunIndentsPass(editor: Editor): Boolean = true

  override fun shouldShowIndentGuide(editor: Editor, descriptor: IndentGuideDescriptor): Boolean {
    val project = editor.project ?: return true
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return true
    if (!file.language.isMarkdownLanguage()) return true
    val element = PsiUtilCore.getElementAtOffset(file, editor.document.getLineStartOffset(descriptor.startLine))
    return PsiTreeUtil.getParentOfType(element, MarkdownCodeFence::class.java) == null
  }
}
