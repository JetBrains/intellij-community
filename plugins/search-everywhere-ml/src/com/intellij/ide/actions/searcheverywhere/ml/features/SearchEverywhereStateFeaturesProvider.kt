package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.usages.impl.ScopeRuleValidator


class SearchEverywhereStateFeaturesProvider {
  companion object {
    internal val QUERY_LENGTH_DATA_KEY = EventFields.Int("queryLength")
    internal val IS_EMPTY_QUERY_DATA_KEY = EventFields.Boolean("isEmptyQuery")
    internal val QUERY_CONTAINS_PATH_DATA_KEY = EventFields.Boolean("queryContainsPath")
    internal val QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY = EventFields.Boolean("queryContainsCommandChar")
    internal val QUERY_CONTAINS_SPACES_DATA_KEY = EventFields.Boolean("queryContainsSpaces")
    internal val QUERY_IS_CAMEL_CASE_DATA_KEY = EventFields.Boolean("queryIsCamelCase")
    internal val QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY = EventFields.Boolean("queryContainsAbbreviations")
    internal val QUERY_IS_ALL_UPPERCASE_DATA_KEY = EventFields.Boolean("queryIsAllUppercase")
    internal val IS_DUMB_MODE = EventFields.Boolean("isDumbMode")
    private val SEARCH_SCOPE_DATA_KEY = EventFields.StringValidatedByCustomRule("searchScope", ScopeRuleValidator::class.java)
    private val IS_SEARCH_EVERYWHERE_DATA_KEY = EventFields.Boolean("isSearchEverywhere")

    fun getFeaturesDefinition(): List<EventField<*>> {
      return arrayListOf(
        QUERY_LENGTH_DATA_KEY, IS_EMPTY_QUERY_DATA_KEY,
        QUERY_CONTAINS_PATH_DATA_KEY, QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY,
        QUERY_CONTAINS_SPACES_DATA_KEY, QUERY_IS_CAMEL_CASE_DATA_KEY,
        QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY, QUERY_IS_ALL_UPPERCASE_DATA_KEY,
        IS_DUMB_MODE, SEARCH_SCOPE_DATA_KEY, IS_SEARCH_EVERYWHERE_DATA_KEY,
      )
    }
  }

  fun getSearchStateFeatures(tabId: String, query: String, isDumb: Boolean?,
                             searchScope: ScopeDescriptor?, isSearchEverywhere: Boolean): List<EventPair<*>> {
    val features = arrayListOf<EventPair<*>>(
      QUERY_LENGTH_DATA_KEY.with(query.length),
      IS_EMPTY_QUERY_DATA_KEY.with(query.isEmpty()),
      QUERY_CONTAINS_SPACES_DATA_KEY.with(query.contains(" ")),
      QUERY_IS_CAMEL_CASE_DATA_KEY.with(query.isCamelCase()),
      QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY.with(query.containsAbbreviations()),
      QUERY_IS_ALL_UPPERCASE_DATA_KEY.with(query.all { it.isUpperCase() }),
      IS_SEARCH_EVERYWHERE_DATA_KEY.with(isSearchEverywhere)
    )

    isDumb?.let {
      features.add(IS_DUMB_MODE.with(it))
    }

    if (hasSuitableContributor(tabId, FileSearchEverywhereContributor::class.java.simpleName)) features.addAll(getFileQueryFeatures(query))
    if (hasSuitableContributor(tabId, SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)) features.addAll(getAllTabQueryFeatures(query))

    searchScope?.displayName?.let { searchScopeDisplayName ->
      val scopeId = ScopeIdMapper.instance.getScopeSerializationId(searchScopeDisplayName)
      features.add(SEARCH_SCOPE_DATA_KEY.with(scopeId))
    }

    return features
  }

  private fun getFileQueryFeatures(query: String) = listOf(
    QUERY_CONTAINS_PATH_DATA_KEY.with(query.indexOfLast { it == '/' || it == '\\' } in 1 until query.lastIndex)
  )

  private fun getAllTabQueryFeatures(query: String) = listOf(
    QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY.with(query.indexOfLast { it == '/' } == 0)
  )

  private fun hasSuitableContributor(currentTabId: String, featuresTab: String): Boolean {
    return currentTabId == featuresTab || currentTabId == SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  }

  private fun CharSequence.isCamelCase(): Boolean {
    this.forEachIndexed { index, c ->
      if (index == 0) return@forEachIndexed

      // Check if there's a case change between this character and the previous one
      if (c.isUpperCase() != this[index - 1].isUpperCase()) return true
    }

    return false
  }

  private fun CharSequence.containsAbbreviations(): Boolean = this.filter { it.isLetter() }.all { it.isUpperCase() }
}
