// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.breadcrumbs

import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.intellij.plugins.markdown.lang.MarkdownLanguage

class MarkdownBreadcrumbsProvider : BreadcrumbsProvider {
  override fun getLanguages() = arrayOf(MarkdownLanguage.INSTANCE)

  override fun acceptElement(element: PsiElement) = false

  override fun getElementInfo(element: PsiElement) = throw NotImplementedError("Breadcrumbs are not implemented for Markdown")

  override fun getParent(element: PsiElement): PsiElement? = null

  override fun isShownByDefault() = false
}
