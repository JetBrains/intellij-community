// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.WHOLE_EXACTLY_MATCHED_WORDS
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.WHOLE_LEVENSHTEIN_DISTANCE_CASE_INSENSITIVE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.WHOLE_WORDS_IN_ELEMENT
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.WHOLE_WORDS_IN_QUERY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.FILETYPE_MATCHES_QUERY_DATA_KEY

internal abstract class SearchEverywhereRankingModel(protected val model: DecisionFunction) {
  abstract fun predict(features: Map<String, Any?>): Double
  protected fun rawModelPredict(features: Map<String, Any?>): Double {
    return model.predict(buildArray(model.featuresOrder, withLegacyFeatureNameAliases(features)))
  }

  private fun buildArray(featuresOrder: Array<FeatureMapper>, features: Map<String, Any?>): DoubleArray {
    val array = DoubleArray(featuresOrder.size)
    for (i in featuresOrder.indices) {
      val mapper = featuresOrder[i]
      val value = features[mapper.featureName]
      array[i] = mapper.asArrayValue(value)
    }
    return array
  }
}

internal class SimpleSearchEverywhereRankingModel(model: DecisionFunction) : SearchEverywhereRankingModel(model) {
  override fun predict(features: Map<String, Any?>): Double = rawModelPredict(features)
}

internal class ExactMatchSearchEverywhereRankingModel(model: DecisionFunction) : SearchEverywhereRankingModel(model) {
  private val MINIMUM_EM_WITH_EXTENSION_VALUE = 0.99
  private val MINIMUM_EXACT_MATCH_VALUE = 0.9
  private val EPSILON = 1e-5
  private val simpleModel = SimpleSearchEverywhereRankingModel(model)

  /**
   * Determine if the given features map represents an exact match.
   * An exact match is determined by the following conditions:
   * - If there is just one word in the query, all the letters must match case-insensitively
   * - If there is more than just one word in the query, all words must match case-insensitively
   *
   *
   * Exact match examples (**Q**uery, **I**tem):
   * - Q: filter ... I: filter
   * - Q: filter ... I: Filter
   * - Q: Filter ... I: filter
   * - Q: filtersearch ... I: filterSearch
   * - Q: filter_search ... I: filterSearch
   * - Q: Filters ... I: filters
   * - Q: Filters ... I: filterS
   *
   * Not exact match examples:
   * - Q: FilterS ... I: filters
   * - Whatever doesn't match case-insensitively (Q: filter ... I: filters
   *
   * @return true if the features represent an exact match, false otherwise.
   */
  private fun isExactMatch(features: Map<String, Any?>): Boolean {
    val wordsInQuery = features.getOrDefault(WHOLE_WORDS_IN_QUERY.name, 0)
    val wordsInElement = features.getOrDefault(WHOLE_WORDS_IN_ELEMENT.name, 0)
    val exactlyMatchedWords = features.getOrDefault(WHOLE_EXACTLY_MATCHED_WORDS.name, 0)
    val levenshteinDistance = features.getOrDefault(WHOLE_LEVENSHTEIN_DISTANCE_CASE_INSENSITIVE.name, 1.0)
    val allWordsMatch = (wordsInElement == wordsInQuery
                         && wordsInElement == exactlyMatchedWords
                         && exactlyMatchedWords != 0)
    return if (wordsInQuery == 1) levenshteinDistance == 0.0 else allWordsMatch

  }

  override fun predict(features: Map<String, Any?>): Double {

    val isExactMatch = isExactMatch(features)
    val isExtensionMatch = features.getOrDefault(FILETYPE_MATCHES_QUERY_DATA_KEY.name, false) == true
    val mlPrediction = simpleModel.predict(features)
    val isNameAndExtensionMatch = isExactMatch && isExtensionMatch
    val isNameOnlyMatch = isExactMatch && !isExtensionMatch

    return when {
      // return preference is > 0.99 = MINIMUM_EM_WITH_EXTENSION_VALUE
      isNameAndExtensionMatch -> MINIMUM_EM_WITH_EXTENSION_VALUE + mlPrediction * (1 - MINIMUM_EM_WITH_EXTENSION_VALUE)
      // MINIMUM_EXACT_MATCH_VALUE = 0.9 < return preference < 0.99 = MINIMUM_EM_WITH_EXTENSION_VALUE
      isNameOnlyMatch -> MINIMUM_EXACT_MATCH_VALUE + mlPrediction * (MINIMUM_EM_WITH_EXTENSION_VALUE - MINIMUM_EXACT_MATCH_VALUE - EPSILON)
      // return preference < 0.9 = MINIMUM_EXACT_MATCH_VALUE
      else -> 0.0 + mlPrediction * (MINIMUM_EXACT_MATCH_VALUE - EPSILON)
    }
  }
}
