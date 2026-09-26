// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.DoubleEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMlFacade
import com.intellij.textMatching.PrefixMatchingType
import com.intellij.textMatching.PrefixMatchingUtil
import com.intellij.textMatching.WholeTextMatchUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

@ApiStatus.Internal
abstract class SearchEverywhereElementFeaturesProvider(private val supportedProviderIds: Set<String>) {
  @Deprecated("Specify providerIds as a set of string instead")
  constructor(vararg supportedTabs: Class<out SearchEverywhereContributor<*>>) : this(supportedTabs.map { it.simpleName }.toSet())

  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider> = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
    }

    fun getFeatureProvidersForContributor(providerId: String): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter {
        getPluginInfo(it.javaClass).isDevelopedByJetBrains() && it.isSearchResultsProviderSupported(providerId)
      }
    }

    internal val NAME_LENGTH = EventFields.RoundedInt("name_length")
    internal val ML_SCORE_KEY = EventFields.Double("ml_score")
    internal val SIMILARITY_SCORE = EventFields.Double("similarity_score")
    internal val IS_SEMANTIC_ONLY = EventFields.Boolean("is_semantic_only")

    internal val PREFIX_SAME_START_COUNT = EventFields.Int("prefix_same_start_count")
    internal val PREFIX_GREEDY_SCORE = EventFields.Double("prefix_greedy_score")
    internal val PREFIX_GREEDY_WITH_CASE_SCORE = EventFields.Double("prefix_greedy_with_case_score")
    internal val PREFIX_MATCHED_WORDS_SCORE = EventFields.Double("prefix_matched_words_score")
    internal val PREFIX_MATCHED_WORDS_RELATIVE = EventFields.Double("prefix_matched_words_relative")
    internal val PREFIX_MATCHED_WORDS_WITH_CASE_SCORE = EventFields.Double("prefix_matched_words_with_case_score")
    internal val PREFIX_MATCHED_WORDS_WITH_CASE_RELATIVE = EventFields.Double("prefix_matched_words_with_case_relative")
    internal val PREFIX_SKIPPED_WORDS = EventFields.Int("prefix_skipped_words")
    internal val PREFIX_MATCHING_TYPE = EventFields.String(
      "prefix_matching_type", PrefixMatchingType.entries.map { it.name }
    )
    internal val PREFIX_EXACT = EventFields.Boolean("prefix_exact")
    internal val PREFIX_MATCHED_LAST_WORD = EventFields.Boolean("prefix_matched_last_word")

    internal val WHOLE_LEVENSHTEIN_DISTANCE = EventFields.Double("levenshtein_distance",
                                                                 "Levenshtein distance normalized by query lengths")
    internal val WHOLE_LEVENSHTEIN_DISTANCE_CASE_INSENSITIVE =
      EventFields.Double("levenshtein_distance_case_insensitive",
                         "Levenshtein distance with case insensitive matching, normalized by query length")
    internal val WHOLE_WORDS_IN_QUERY = EventFields.Int("words_in_query", "Number of words in the query")
    internal val WHOLE_WORDS_IN_ELEMENT = EventFields.Int("words_in_element", "Number of words in the element text")
    internal val WHOLE_EXACTLY_MATCHED_WORDS = EventFields.Int("exactly_matched_words")

    fun getDefaultFields(): List<EventField<*>> {
      return listOf(
        NAME_LENGTH, ML_SCORE_KEY, SIMILARITY_SCORE, IS_SEMANTIC_ONLY,
        PREFIX_SAME_START_COUNT, PREFIX_GREEDY_SCORE, PREFIX_GREEDY_WITH_CASE_SCORE,
        PREFIX_MATCHED_WORDS_SCORE, PREFIX_MATCHED_WORDS_RELATIVE, PREFIX_MATCHED_WORDS_WITH_CASE_SCORE,
        PREFIX_MATCHED_WORDS_WITH_CASE_RELATIVE, PREFIX_SKIPPED_WORDS, PREFIX_MATCHING_TYPE, PREFIX_EXACT,
        PREFIX_MATCHED_LAST_WORD,
        WHOLE_LEVENSHTEIN_DISTANCE, WHOLE_LEVENSHTEIN_DISTANCE_CASE_INSENSITIVE,
        WHOLE_WORDS_IN_QUERY, WHOLE_WORDS_IN_ELEMENT, WHOLE_EXACTLY_MATCHED_WORDS
      )
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
  open fun isSearchResultsProviderSupported(providerId: String): Boolean {
    return supportedProviderIds.contains(providerId)
  }

  abstract fun getFeaturesDeclarations(): List<EventField<*>>

  abstract fun getElementFeatures(
    element: Any,
    currentTime: Long,
    searchQuery: String,
    elementPriority: Int,
    cache: FeaturesProviderCache?,
    correction: SearchEverywhereSpellCheckResult,
  ): List<EventPair<*>>

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  protected fun getNameMatchingFeatures(nameOfFoundElement: String, searchQuery: String): Collection<EventPair<*>> {
    if (nameOfFoundElement.isBlank() || searchQuery.isBlank()) {
      return emptyList()
    }

    // For a quicker lookup
    val nameToFeature = getDefaultFields()
      .associateBy { it.name }

    return buildList {
      buildMap {
        PrefixMatchingUtil.calculateFeatures(nameOfFoundElement, searchQuery, this)

        putAll(
          WholeTextMatchUtil.calculateFeatures(nameOfFoundElement, searchQuery)
        )
      }.map { (featureName, value) ->
        val field = nameToFeature.getValue(featureName)
        field.tryWith(value)
      }.forEach {
        add(it)
      }


      add(NAME_LENGTH.with(nameOfFoundElement.length))
    }
  }

  protected fun getQueryEmbedding(queryText: String, split: Boolean = false): FloatTextEmbedding? {
    return SearchEverywhereMlFacade.activeSession?.getSearchQueryEmbedding(queryText, split)
  }

  fun EventField<*>.tryWith(value: Any): EventPair<*> {
    return when (this) {
      is BooleanEventField -> this.with(value as Boolean)
      is DoubleEventField -> this.with(value as Double)
      is IntEventField -> this.with(value as Int)
      is StringEventField -> this.with(value.toString())
      else -> throw IllegalArgumentException("Could not associate a value with unsupported field type: ${this::class.java}")
    }
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
