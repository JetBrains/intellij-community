package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.openapi.project.Project

interface SearchEverywhereItemSelectedListener {
  fun onItemSelected(project: Project?,
                     tabId: String,
                     indexes: IntArray,
                     selectedItems: List<Any>,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                     closePopup: Boolean)
}
