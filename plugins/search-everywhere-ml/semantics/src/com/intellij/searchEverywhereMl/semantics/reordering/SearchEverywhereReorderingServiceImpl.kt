package com.intellij.searchEverywhereMl.semantics.reordering

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereReorderingService
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

class SearchEverywhereReorderingServiceImpl : SearchEverywhereReorderingService {
  override fun isEnabled(): Boolean {
    return SemanticSearchSettings.getInstance().enabledInActionsTab
  }

  override fun isEnabledInTab(tabID: String): Boolean {
    return tabID == ActionSearchEverywhereContributor::class.java.simpleName
  }

  override fun reorder(tabID: String, items: MutableList<SearchEverywhereFoundElementInfo>) {
    if (!isEnabled() || !isEnabledInTab(tabID)) return

    val (semantic, classic) = items.partition {
      val contributor = it.contributor
      contributor is SemanticSearchEverywhereContributor && contributor.isElementSemantic(it.element)
    }
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