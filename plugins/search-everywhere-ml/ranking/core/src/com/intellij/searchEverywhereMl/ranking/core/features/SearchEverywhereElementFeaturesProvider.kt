// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.searchEverywhereMl.ranking.core.searchEverywhereMlRankingService
import com.intellij.textMatching.PrefixMatchingType
import com.intellij.textMatching.PrefixMatchingUtil
import com.intellij.textMatching.WholeTextMatchUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

@ApiStatus.Internal
abstract class SearchEverywhereElementFeaturesProvider(private val supportedContributorIds: List<String>) {
  constructor(vararg supportedTabs: Class<out SearchEverywhereContributor<*>>) : this(supportedTabs.map { it.simpleName })

  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider> = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
    }

    fun getFeatureProvidersForContributor(contributorId: String): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter {
        getPluginInfo(it.javaClass).isDevelopedByJetBrains() && it.isContributorSupported(contributorId)
      }
    }

    internal val NAME_LENGTH = EventFields.RoundedInt("nameLength")
    internal val ML_SCORE_KEY = EventFields.Double("mlScore")
    internal val SIMILARITY_SCORE = EventFields.Double("similarityScore")
    internal val IS_SEMANTIC_ONLY = EventFields.Boolean("isSemanticOnly")
    internal val BUFFERED_TIMESTAMP = EventFields.Long("bufferedTimestamp")


    internal val prefixMatchingNameFeatureToField = hashMapOf<String, EventField<*>>(
      "prefix_same_start_count" to EventFields.Int("${PrefixMatchingUtil.baseName}SameStartCount"),
      "prefix_greedy_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyScore"),
      "prefix_greedy_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyWithCaseScore"),
      "prefix_matched_words_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsScore"),
      "prefix_matched_words_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsRelative"),
      "prefix_matched_words_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseScore"),
      "prefix_matched_words_with_case_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseRelative"),
      "prefix_skipped_words" to EventFields.Int("${PrefixMatchingUtil.baseName}SkippedWords"),
      "prefix_matching_type" to EventFields.String(
        "${PrefixMatchingUtil.baseName}MatchingType", PrefixMatchingType.entries.map { it.name }
      ),
      "prefix_exact" to EventFields.Boolean("${PrefixMatchingUtil.baseName}Exact"),
      "prefix_matched_last_word" to EventFields.Boolean("${PrefixMatchingUtil.baseName}MatchedLastWord"),
    )
    internal val wholeMatchingNameFeatureToField = hashMapOf<String, EventField<*>>(
      "levenshtein_distance" to EventFields.Double("${WholeTextMatchUtil.baseName}LevenshteinDistance",
                                                   "Levenshtein distance normalized by query lengths"),
      "levenshtein_distance_case_insensitive" to
        EventFields.Double("${WholeTextMatchUtil.baseName}LevenshteinDistanceCaseInsensitive",
                           "Levenshtein distance with case insensitive matching, normalized by query length"),
      "words_in_query" to EventFields.Int("${WholeTextMatchUtil.baseName}WordsInQuery", "Number of words in the query"),
      "words_in_element" to EventFields.Int("${WholeTextMatchUtil.baseName}WordsInElement", "Number of words in the element text"),
      "exactly_matched_words" to EventFields.Int("${WholeTextMatchUtil.baseName}ExactlyMatchedWords")
    )

    fun getDefaultFields(): List<EventField<*>> {
      return listOf(NAME_LENGTH, ML_SCORE_KEY, SIMILARITY_SCORE, IS_SEMANTIC_ONLY, BUFFERED_TIMESTAMP) +
             prefixMatchingNameFeatureToField.values + wholeMatchingNameFeatureToField.values
    }



    internal fun roundDouble(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }

  /**
   * Returns true if the Search Everywhere contributor is supported by the feature provider.
   *
   * By default, every feature provider has a list of supported contributors, but it's possible to override this logic.
   */
  open fun isContributorSupported(contributorId: String): Boolean {
    return supportedContributorIds.contains(contributorId)
  }

  abstract fun getFeaturesDeclarations(): List<EventField<*>>

  abstract fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>>

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  protected fun getNameMatchingFeatures(nameOfFoundElement: String, searchQuery: String): Collection<EventPair<*>> {
    return buildList {
      buildMap {
        PrefixMatchingUtil.calculateFeatures(nameOfFoundElement, searchQuery, this)
      }.map { (featureName, value) ->
        val field = prefixMatchingNameFeatureToField[featureName]
        setMatchValueToField(value, field)
      }.filterNotNull()
        .forEach {
        add(it)
      }


      add(NAME_LENGTH.with(nameOfFoundElement.length))

      addAll(WholeTextMatchUtil.calculateFeatures(nameOfFoundElement, searchQuery).map { (key, value) ->
        setMatchValueToField(value, wholeMatchingNameFeatureToField[key])
      }.filterNotNull())
    }
  }

  protected fun getQueryEmbedding(queryText: String, split: Boolean = false): FloatTextEmbedding? {
    return searchEverywhereMlRankingService?.getCurrentSession()?.getSearchQueryEmbedding(queryText, split)
  }

  internal fun setMatchValueToField(matchValue: Any,
                                    field: EventField<*>?): EventPair<*>? {
    if (matchValue is Boolean && field is BooleanEventField) {
      return field.with(matchValue)
    }
    else if (matchValue is Double && field is DoubleEventField) {
      return field.with(roundDouble(matchValue))
    }
    else if (matchValue is Int && field is IntEventField) {
      return field.with(matchValue)
    }
    else if (matchValue is Enum<*> && field is StringEventField) {
      return field.with(matchValue.toString())
    }
    return null
  }
}

@ApiStatus.Internal
fun <T> MutableList<EventPair<*>>.putIfValueNotNull(key: EventField<T>, value: T?) {
  value?.let {
    add(key.with(it))
  }
}

@ApiStatus.Internal
fun MutableCollection<EventPair<*>>.addIfTrue(key: BooleanEventField, value: Boolean) {
  if (value) {
    this.add(key.with(true))
  }
}
