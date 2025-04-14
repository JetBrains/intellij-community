@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

internal class PsiItemWithSimilarityElementKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return when (element) {
      is PsiItemWithSimilarity<*> -> SearchEverywhereElementKeyProvider.getKeyOrNull(element.value)
      else -> null
    }
  }
}