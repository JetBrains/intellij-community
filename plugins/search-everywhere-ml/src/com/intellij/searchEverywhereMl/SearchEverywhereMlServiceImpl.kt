package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

val RANKING_EP_NAME = ExtensionPointName.create<SearchEverywhereMlService>("com.intellij.searchEverywhereMl.rankingService")

private val RANKING_SERVICE: SearchEverywhereMlService
  get() = RANKING_EP_NAME.extensionList.first()

val ITEM_SELECTED_LISTENERS_EP_NAME = ExtensionPointName.create<SearchEverywhereItemSelectedListener>("com.intellij.searchEverywhereMl.itemSelectedListener")

private val ITEM_SELECTED_LISTENERS
  get() = ITEM_SELECTED_LISTENERS_EP_NAME.extensionList

class SearchEverywhereMlServiceImpl : SearchEverywhereMlService by RANKING_SERVICE {
  override fun onItemSelected(project: Project?,
                              tabId: String,
                              indexes: IntArray,
                              selectedItems: List<Any>,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                              closePopup: Boolean,
                              query: String) {
    RANKING_SERVICE.onItemSelected(project, tabId, indexes, selectedItems, elementsProvider, closePopup, query)
    ITEM_SELECTED_LISTENERS.forEach { it.onItemSelected(project, tabId, indexes, selectedItems, elementsProvider, closePopup, query) }
  }
}
