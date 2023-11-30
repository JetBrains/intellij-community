package com.intellij.searchEverywhereMl.semantics.reordering

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor

class SearchEverywhereReorderingServiceImpl : SearchEverywhereReorderingService {
  private val enabledTabs = setOf(
    ActionSearchEverywhereContributor::class.java.simpleName,
    FileSearchEverywhereContributor::class.java.simpleName,
    ClassSearchEverywhereContributor::class.java.simpleName,
    SymbolSearchEverywhereContributor::class.java.simpleName
  )

  override fun isEnabled() = false

  override fun isEnabledInTab(tabID: String): Boolean {
    return tabID in enabledTabs
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