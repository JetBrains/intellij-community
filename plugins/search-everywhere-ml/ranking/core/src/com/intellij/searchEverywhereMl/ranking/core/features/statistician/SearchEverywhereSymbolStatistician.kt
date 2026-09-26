package com.intellij.searchEverywhereMl.ranking.core.features.statistician

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

internal class SearchEverywhereSymbolStatistician : SearchEverywhereStatistician<Any>(PsiElement::class.java,
                                                                                      PsiItemWithPresentation::class.java) {
  override val requiresReadAction: Boolean = true

  override fun getContext(element: Any): String? {
    val contextName = getContextName(element) ?: return null
    return "$contextPrefix#$contextName"
  }

  private fun getContextName(element: Any): String? {
    if (element is PsiItemWithPresentation) return element.presentation.containerText
    if (element !is PsiElement) return null

    return if (!element.isValid) null
    else (element.context as? PsiNamedElement)?.name
  }

  override fun getValue(element: Any, location: String): String? {
    if (element is PsiItemWithPresentation) return element.presentation.presentableText
    if (element !is PsiNamedElement) return null

    return if (!element.isValid) null
    else element.name
  }
}