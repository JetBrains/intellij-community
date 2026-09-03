// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.breadcrumbs

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.util.MarkdownPsiStructureUtil

class MarkdownBreadcrumbsProvider : BreadcrumbsProvider {
  override fun getLanguages() = arrayOf(MarkdownLanguage.INSTANCE)

  override fun acceptElement(element: PsiElement): Boolean = element is MarkdownHeader

  override fun getElementInfo(element: PsiElement): String {
    val header = element as MarkdownHeader
    return header.buildVisibleText().takeUnless { it.isNullOrBlank() } ?: header.text.trim()
  }

  override fun getParent(element: PsiElement): PsiElement? {
    val header = element as? MarkdownHeader ?: return findHeaderBefore(element)
    if (header.level == 1) return null
    return findPreviousHeader(header) { it.level < header.level }
  }

  override fun acceptStickyElement(element: PsiElement): Boolean = false

  private fun findHeaderBefore(element: PsiElement): MarkdownHeader? =
    PsiTreeUtil.getParentOfType(element, MarkdownHeader::class.java) ?: findPreviousHeader(element)

  private fun findPreviousHeader(from: PsiElement, predicate: (MarkdownHeader) -> Boolean = { true }): MarkdownHeader? {
    return from.parents(withSelf = true)
      .takeWhile { it !is PsiFile }
      .flatMap { current ->
        current.siblings(forward = false, withSelf = false)
          .flatMap { headersIn(it).reversed().asSequence() }
      }
      .firstOrNull(predicate)
  }

  private fun headersIn(element: PsiElement): Collection<MarkdownHeader> = when (element) {
    is MarkdownHeader -> listOf(element)
    else -> if (element.hasType(MarkdownPsiStructureUtil.TRANSPARENT_CONTAINERS)) {
      PsiTreeUtil.findChildrenOfType(element, MarkdownHeader::class.java)
    }
    else emptyList()
  }
}
