// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.references.ReferenceUtil.hasMarkdownFiles

internal class MarkdownReferenceUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element !is PsiNamedElement) return null
    if (element.useScope !is GlobalSearchScope) return null
    if (!hasMarkdownFiles(element.project)) return null

    return GlobalSearchScope.getScopeRestrictedByFileTypes(
      GlobalSearchScope.projectScope(element.project),
      MarkdownFileType.INSTANCE
    )
  }
}
