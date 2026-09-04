// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.links.ReferenceLinkConversions

internal class ConvertToInlineLinkIntention: BaseElementAtCaretIntentionAction() {
  override fun getFamilyName(): String = text
  override fun getText(): String = MarkdownBundle.message("markdown.convert.to.inline.link.intention.text")

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    return ReferenceLinkConversions.findReferenceLink(element) != null
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    ReferenceLinkConversions.convertToInlineLink(element)
  }
}
