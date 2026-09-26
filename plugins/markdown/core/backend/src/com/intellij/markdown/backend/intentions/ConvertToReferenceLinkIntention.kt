// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.links.ReferenceLinkConversions

internal class ConvertToReferenceLinkIntention: BaseElementAtCaretIntentionAction() {
  override fun getFamilyName(): String = text
  override fun getText(): String = MarkdownBundle.message("markdown.convert.to.reference.link.intention.text")

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    return ReferenceLinkConversions.findInlineLink(element) != null
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val link = ReferenceLinkConversions.findInlineLink(element) ?: return
    val label = ReferenceLinkConversions.convertToReferenceLink(link) ?: return
    editor.caretModel.moveToOffset(label.startOffset + 1)
  }
}
