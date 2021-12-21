package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl

internal class SearchEverywhereAllFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID) {
  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    return emptyMap()
  }
}