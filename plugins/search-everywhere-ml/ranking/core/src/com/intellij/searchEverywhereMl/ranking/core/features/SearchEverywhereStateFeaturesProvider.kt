package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.find.FindManager
import com.intellij.find.impl.TextSearchContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.usages.impl.ScopeRuleValidator

internal class SearchEverywhereStateFeaturesProvider {
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

    private val IS_CASE_SENSITIVE = EventFields.Boolean("isCaseSensitive")
    private val IS_WHOLE_WORDS_ONLY = EventFields.Boolean("isWholeWordsOnly")
    private val IS_REGULAR_EXPRESSIONS = EventFields.Boolean("isRegularExpressions")

    fun getFeaturesDefinition(): List<EventField<*>> {
      return listOf(
        QUERY_LENGTH_DATA_KEY, IS_EMPTY_QUERY_DATA_KEY,
        QUERY_CONTAINS_PATH_DATA_KEY, QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY,
        QUERY_CONTAINS_SPACES_DATA_KEY, QUERY_IS_CAMEL_CASE_DATA_KEY,
        QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY, QUERY_IS_ALL_UPPERCASE_DATA_KEY,
        IS_DUMB_MODE, SEARCH_SCOPE_DATA_KEY, IS_SEARCH_EVERYWHERE_DATA_KEY,
        IS_CASE_SENSITIVE, IS_WHOLE_WORDS_ONLY, IS_REGULAR_EXPRESSIONS,
      )
    }
  }

  fun getSearchStateFeatures(project: Project?, tab: SearchEverywhereTab, query: String,
                             searchScope: ScopeDescriptor?, isSearchEverywhere: Boolean): List<EventPair<*>> {
    return buildList {
      add(QUERY_LENGTH_DATA_KEY.with(query.length))
      add(IS_EMPTY_QUERY_DATA_KEY.with(query.isEmpty()))
      add(QUERY_CONTAINS_SPACES_DATA_KEY.with(query.contains(" ")))
      add(QUERY_IS_CAMEL_CASE_DATA_KEY.with(query.isCamelCase()))
      add(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY.with(query.containsAbbreviations()))
      add(QUERY_IS_ALL_UPPERCASE_DATA_KEY.with(query.all { it.isUpperCase() }))
      add(IS_SEARCH_EVERYWHERE_DATA_KEY.with(isSearchEverywhere))

      project?.let {
        val isDumb = DumbService.isDumb(project)
        add(IS_DUMB_MODE.with(isDumb))
      }

      if (hasSuitableContributor(tab, SearchEverywhereTab.Files)) {
        addAll(getFileQueryFeatures(query))
      }
      if (hasSuitableContributor(tab, SearchEverywhereTab.All)) {
        addAll(getAllTabQueryFeatures(query))
      }

      searchScope?.displayName?.let { searchScopeDisplayName ->
        val scopeId = ScopeIdMapper.instance.getScopeSerializationId(searchScopeDisplayName)
        add(SEARCH_SCOPE_DATA_KEY.with(scopeId))
      }

      if (project != null && isTabWithTextContributor(tab)) {
        addAll(getTextContributorFeatures(project))
      }
    }
  }

  private fun getFileQueryFeatures(query: String) = listOf(
    QUERY_CONTAINS_PATH_DATA_KEY.with(query.indexOfLast { it == '/' || it == '\\' } in 1 until query.lastIndex)
  )

  private fun getAllTabQueryFeatures(query: String) = listOf(
    QUERY_CONTAINS_COMMAND_CHAR_DATA_KEY.with(query.indexOfLast { it == '/' } == 0)
  )

  private fun getTextContributorFeatures(project: Project) = FindManager.getInstance(project).findInProjectModel.let { findModel ->
    listOf(
      IS_CASE_SENSITIVE.with(findModel.isCaseSensitive),
      IS_WHOLE_WORDS_ONLY.with(findModel.isWholeWordsOnly),
      IS_REGULAR_EXPRESSIONS.with(findModel.isRegularExpressions),
    )
  }

  private fun hasSuitableContributor(currentTab: SearchEverywhereTab, featuresTab: SearchEverywhereTab): Boolean {
    return currentTab == featuresTab || currentTab == SearchEverywhereTab.All
  }

  private fun isTabWithTextContributor(tab: SearchEverywhereTab) = when(tab.tabId) {
    TextSearchContributor::class.java.simpleName -> true
    SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID -> true
    else -> false
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
