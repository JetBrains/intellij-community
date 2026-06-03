// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.backtick

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.indexing.FindSymbolParameters
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan

class BacktickReference(element: MarkdownCodeSpan) :
  PsiPolyVariantReferenceBase<MarkdownCodeSpan>(element, true) {

  private object Resolver : ResolveCache.PolyVariantResolver<BacktickReference> {
    override fun resolve(ref: BacktickReference, incompleteCode: Boolean): Array<ResolveResult> {
      return ref.tryResolve()
    }
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    val name = canonicalText
    if (!shouldSearchInSymbols(name)) {
      return super.isReferenceTo(element)
    }

    // Avoid invoking expensive `multiResolve` for all classes / symbols
    if (element is NavigationItem) return element.name == name
    if (element is PsiNamedElement) return element.name == name
    return false
  }

  override fun bindToElement(element: PsiElement): PsiElement {
    if (isReferenceTo(element)) return getElement()
    return super.bindToElement(element)
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val file = element.containingFile
    return ResolveCache.getInstance(file.project).resolveWithCaching(this, Resolver, true, incompleteCode, file)
  }

  private fun tryResolve(): Array<ResolveResult> {
    val name = canonicalText
    val navigationItems = mutableListOf<NavigationItem>()

    resolveWithContributor(name, name, element.project, ChooseByNameContributorEx.CLASS_EP_NAME, navigationItems)
    if (shouldSearchInSymbols(name)) {
      resolveWithContributor(name, name, element.project, ChooseByNameContributorEx.SYMBOL_EP_NAME, navigationItems)
    }

    return navigationItems
      .asSequence()
      .mapNotNull { item -> item.toPsiElement() }
      .filter { it.isValid }
      .distinct()
      .map { PsiElementResolveResult(it) }
      .toList()
      .toTypedArray()
  }

  override fun getRangeInElement(): TextRange = element.getContentRange()!!

  private fun resolveWithContributor(
    name: String,
    pattern: String,
    project: Project,
    contributors: ExtensionPointName<ChooseByNameContributor>,
    items: MutableList<NavigationItem>,
  ) {
    if (items.size > MAX_RESOLVED_ITEMS) return
    for (contributor in DumbService.getInstance(project).filterByDumbAwareness(contributors.extensionList)) {
      if (contributor is ChooseByNameContributorEx) {
        contributor.processElementsWithName(
          name,
          { item ->
            items.add(item)
            items.size <= MAX_RESOLVED_ITEMS
          },
          FindSymbolParameters.wrap(pattern, project, true)
        )
      }
      else {
        items.addAll(contributor.getItemsByName(name, pattern, project, true))
      }
      if (items.size > MAX_RESOLVED_ITEMS) break
    }
  }

  private fun shouldSearchInSymbols(elementName: String): Boolean =
    elementName.length >= Registry.intValue("markdown.backtick.reference.symbol.length") || NameUtil.nameToWordList(elementName).size > 1

  private fun NavigationItem.toPsiElement(): PsiElement? {
    return when (this) {
      is PsiElement -> this
      is PsiElementNavigationItem -> targetElement
      else -> null
    }
  }

  private companion object {
    private const val MAX_RESOLVED_ITEMS = 100
  }
}
