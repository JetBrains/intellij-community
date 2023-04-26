// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.textMatching.PrefixMatchingType
import com.intellij.textMatching.PrefixMatchingUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

@ApiStatus.Internal
abstract class SearchEverywhereElementFeaturesProvider(private val supportedContributorIds: List<String>) {
  constructor(vararg supportedTabs: Class<out SearchEverywhereContributor<*>>) : this(supportedTabs.map { it.simpleName })

  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList
    }

    fun getFeatureProvidersForContributor(contributorId: String): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter { it.isContributorSupported(contributorId) }
    }

    internal val NAME_LENGTH = EventFields.RoundedInt("nameLength")

    internal val nameFeatureToField = hashMapOf<String, EventField<*>>(
      "prefix_same_start_count" to EventFields.Int("${PrefixMatchingUtil.baseName}SameStartCount"),
      "prefix_greedy_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyScore"),
      "prefix_greedy_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyWithCaseScore"),
      "prefix_matched_words_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsScore"),
      "prefix_matched_words_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsRelative"),
      "prefix_matched_words_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseScore"),
      "prefix_matched_words_with_case_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseRelative"),
      "prefix_skipped_words" to EventFields.Int("${PrefixMatchingUtil.baseName}SkippedWords"),
      "prefix_matching_type" to EventFields.String(
        "${PrefixMatchingUtil.baseName}MatchingType", PrefixMatchingType.values().map { it.name }
      ),
      "prefix_exact" to EventFields.Boolean("${PrefixMatchingUtil.baseName}Exact"),
      "prefix_matched_last_word" to EventFields.Boolean("${PrefixMatchingUtil.baseName}MatchedLastWord"),
    )

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
                                  cache: FeaturesProviderCache?): List<EventPair<*>>

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  /**
   * Associates the specified key with the value, only if the value is not null.
   */
  protected fun <T> MutableList<EventPair<*>>.putIfValueNotNull(key: EventField<T>, value: T?) {
    value?.let {
      add(key.with(it))
    }
  }

  protected fun getNameMatchingFeatures(nameOfFoundElement: String, searchQuery: String): Collection<EventPair<*>> {
    val features = mutableMapOf<String, Any>()
    PrefixMatchingUtil.calculateFeatures(nameOfFoundElement, searchQuery, features)
    val result = arrayListOf<EventPair<*>>(
      NAME_LENGTH.with(nameOfFoundElement.length)
    )
    features.forEach { (key, value) ->
      val field = nameFeatureToField[key]
      if (value is Boolean && field is BooleanEventField) {
        result.add(field.with(value))
      }
      else if (value is Double && field is DoubleEventField) {
        result.add(field.with(roundDouble(value)))
      }
      else if (value is Int && field is IntEventField) {
        result.add(field.with(value))
      }
      else if (value is Enum<*> && field is StringEventField) {
        result.add(field.with(value.toString()))
      }
    }
    return result
  }
}

@ApiStatus.Internal
fun MutableCollection<EventPair<*>>.addIfTrue(key: BooleanEventField, value: Boolean) {
  if (value) {
    this.add(key.with(true))
  }
}
