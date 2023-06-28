package com.intellij.searchEverywhereMl.semantics.reordering

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereReorderingService
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettingsManager

class SearchEverywhereReorderingServiceImpl: SearchEverywhereReorderingService {
  override fun isEnabled(): Boolean {
    return service<SemanticSearchSettingsManager>().getIsEnabledInActionsTab()
  }

  override fun reorder(items: MutableList<SearchEverywhereFoundElementInfo>) {
    if (!isEnabled()) {
      return
    }

    val (semantic, classic) = items.partition { it.contributor is SemanticSearchEverywhereContributor }
    if (classic.isEmpty() || semantic.isEmpty()) {
      return
    }

    items[0] = classic[0]

    var semanticIndex = 0
    var classicIndex = 1
    while (semanticIndex + classicIndex < items.size) {
      if (classicIndex < classic.size) {
        items[semanticIndex + classicIndex] = classic[classicIndex]
        classicIndex++
      }
      if (semanticIndex < semantic.size) {
        items[semanticIndex + classicIndex] = semantic[semanticIndex]
        semanticIndex++
      }
    }
  }
}