package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

private class SearchEverywhereSymbolStatistician : SearchEverywhereStatistician<Any>(PsiElement::class.java,
                                                                                      PsiItemWithPresentation::class.java) {
  override fun getContext(element: Any): String? {
    val contextName = getContextName(element) ?: return null
    return "$contextPrefix#$contextName"
  }

  private fun getContextName(element: Any): String? {
    if (element is PsiItemWithPresentation) return element.presentation.containerText
    if (element !is PsiElement) return null

    return runReadAction {
      if (!element.isValid) null
      else (element.context as? PsiNamedElement)?.name
    }
  }

  override fun getValue(element: Any, location: String): String? {
    if (element is PsiItemWithPresentation) return element.presentation.presentableText
    if (element !is PsiNamedElement) return null

    return runReadAction {
      if (!element.isValid) null
      else element.name
    }
  }
}