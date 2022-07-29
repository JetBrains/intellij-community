package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.statistics.StatisticsInfo

internal class SearchEverywhereSymbolStatistician : SearchEverywhereStatistician<Any>(PsiElement::class.java,
                                                                                      PsiItemWithPresentation::class.java) {
  override fun getContext(element: Any): String? {
    val contextName = getContextName(element) ?: return null
    return "$contextPrefix#$contextName"
  }

  override fun serializeElement(element: Any, location: String): StatisticsInfo? {
    val context = getContext(element) ?: return null
    val value = getElementName(element) ?: return null

    return StatisticsInfo(context, value)
  }

  private fun getContextName(element: Any): String? {
    if (element is PsiItemWithPresentation) return element.presentation.containerText
    if (element !is PsiElement) return null

    return runReadAction {
      if (!element.isValid) null
      else (element.context as? PsiNamedElement)?.name
    }
  }

  private fun getElementName(element: Any): String? {
    if (element is PsiItemWithPresentation) return element.presentation.presentableText
    if (element !is PsiNamedElement) return null

    return runReadAction {
      if (!element.isValid) null
      else element.name
    }
  }
}