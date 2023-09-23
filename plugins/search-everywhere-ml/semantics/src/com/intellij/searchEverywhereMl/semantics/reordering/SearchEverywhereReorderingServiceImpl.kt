package com.intellij.searchEverywhereMl.semantics.reordering

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereReorderingService
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

class SearchEverywhereReorderingServiceImpl : SearchEverywhereReorderingService {
  override fun isEnabled() = SemanticSearchSettings.getInstance().isEnabled()

  override fun isEnabledInTab(tabID: String): Boolean {
    return tabID in ENABLED_TABS
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

  companion object {
    private val ENABLED_TABS = setOf(
      ActionSearchEverywhereContributor::class.java.simpleName,
      FileSearchEverywhereContributor::class.java.simpleName,
      ClassSearchEverywhereContributor::class.java.simpleName,
      SymbolSearchEverywhereContributor::class.java.simpleName
    )
  }
}