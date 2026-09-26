// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.toc.TableOfContentsMarkers
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

internal class OmitHeadingFromTocIntention: BaseElementAtCaretIntentionAction() {
  override fun getFamilyName(): String = text
  override fun getText(): String = MarkdownBundle.message("markdown.omit.heading.from.toc.intention.text")
  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = findHeader(element) != null

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val header = findHeader(element) ?: return
    val document = editor.document
    val line = document.getLineNumber(header.startOffset)
    document.insertString(document.getLineStartOffset(line), "${TableOfContentsMarkers.OMIT_MARKER}\n")
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }

  private fun findHeader(element: PsiElement): MarkdownHeader? =
    element.parentOfType<MarkdownHeader>(withSelf = true)?.takeUnless(TableOfContentsMarkers::isOmittedFromToc)
}
