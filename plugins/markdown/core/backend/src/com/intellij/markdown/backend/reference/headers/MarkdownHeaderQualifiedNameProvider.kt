// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.reference.headers

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

internal class MarkdownHeaderQualifiedNameProvider : QualifiedNameProvider {
  override fun adjustElementToCopy(element: PsiElement): PsiElement? {
    return PsiTreeUtil.getParentOfType(element, MarkdownHeader::class.java, false)
  }

  override fun getQualifiedName(element: PsiElement): String? {
    return PsiTreeUtil.getParentOfType(element, MarkdownHeader::class.java, false)?.anchorText?.let { "#$it" }
  }

  override fun qualifiedNameToElement(fqn: String, project: Project): PsiElement? = null
}
