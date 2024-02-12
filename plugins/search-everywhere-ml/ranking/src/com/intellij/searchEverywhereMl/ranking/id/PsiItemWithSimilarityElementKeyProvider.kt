package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity

internal class PsiItemWithSimilarityElementKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is PsiItemWithSimilarity<*> -> ElementKeyForIdProvider.getKeyOrNull(element.value)
      else -> null
    }
  }
}