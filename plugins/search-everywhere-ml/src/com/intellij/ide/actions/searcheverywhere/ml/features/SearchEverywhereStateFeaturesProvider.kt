package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl


class SearchEverywhereStateFeaturesProvider {
  companion object {
    internal const val QUERY_LENGTH_DATA_KEY = "queryLength"
    internal const val IS_EMPTY_QUERY_DATA_KEY = "isEmptyQuery"
    internal const val QUERY_CONTAINS_PATH_DATA_KEY = "queryContainsPath"
    internal const val QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY = "queryContainsCommandChar"
  }

  fun getSearchStateFeatures(tabId: String, query: String): Map<String, Any> {
    val features = hashMapOf<String, Any>(
      QUERY_LENGTH_DATA_KEY to query.length,
      IS_EMPTY_QUERY_DATA_KEY to query.isEmpty(),
    )

    if (hasSuitableContributor(tabId, FileSearchEverywhereContributor::class.java.simpleName)) features.putAll(getFileQueryFeatures(query))
    if (hasSuitableContributor(tabId, SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)) features.putAll(getAllTabQueryFeatures(query))

    return features
  }

  private fun getFileQueryFeatures(query: String) = mapOf(
    QUERY_CONTAINS_PATH_DATA_KEY to (query.indexOfLast { it == '/' || it == '\\' } in 1 until query.lastIndex)
  )

  private fun getAllTabQueryFeatures(query: String) = mapOf(
    QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY to (query.indexOfLast { it == '/' } == 0)
  )

  private fun hasSuitableContributor(currentTabId: String, featuresTab: String): Boolean {
    return currentTabId == featuresTab || currentTabId == SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  }
}
