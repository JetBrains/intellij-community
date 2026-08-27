// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.reference.headers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

internal class MarkdownHeaderCopyAction : CopyReferenceAction() {
  override fun getPsiElements(dataContext: DataContext, editor: Editor?): List<PsiElement> {
    val elements = super.getPsiElements(dataContext, editor)
    if (elements.isNotEmpty()) return elements

    val location = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).psiLocation ?: return elements
    val header = PsiTreeUtil.getParentOfType(location, MarkdownHeader::class.java, false) ?: return elements
    return listOf(header)
  }
}
