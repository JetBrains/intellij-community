@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class PsiElementKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return when (element) {
      is PsiElement -> element
      is PSIPresentationBgRendererWrapper.ItemWithPresentation<*> -> element.item
      else -> null
    }
  }
}
