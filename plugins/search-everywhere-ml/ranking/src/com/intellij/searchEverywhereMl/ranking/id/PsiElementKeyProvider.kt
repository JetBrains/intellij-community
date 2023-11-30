package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.psi.PsiElement

private class PsiElementKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is PsiElement -> element
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item
      else -> null
    }
  }
}
