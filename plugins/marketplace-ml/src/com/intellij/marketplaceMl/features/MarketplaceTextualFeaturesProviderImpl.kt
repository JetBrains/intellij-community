package com.intellij.marketplaceMl.features

import com.intellij.ide.plugins.marketplace.statistics.features.MarketplaceTextualFeaturesProvider
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.textMatching.PrefixMatchingType
import com.intellij.textMatching.PrefixMatchingUtil
import kotlin.math.round

class MarketplaceTextualFeaturesProviderImpl : MarketplaceTextualFeaturesProvider {
  object Fields {
    internal val SAME_START_COUNT = EventFields.Int("prefixSameStartCount")
    internal val GREEDY_SCORE = EventFields.Double("prefixGreedyScore")
    internal val GREEDY_WITH_CASE_SCORE = EventFields.Double("prefixGreedyWithCaseScore")
    internal val MATCHED_WORDS_SCORE = EventFields.Double("prefixMatchedWordsScore")
    internal val MATCHED_WORDS_RELATIVE = EventFields.Double("prefixMatchedWordsRelative")
    internal val MATCHED_WORDS_WITH_CASE_SCORE = EventFields.Double("prefixMatchedWordsWithCaseScore")
    internal val MATCHED_WORDS_WITH_CASE_RELATIVE = EventFields.Double("prefixMatchedWordsWithCaseRelative")
    internal val SKIPPED_WORDS = EventFields.Int("prefixSkippedWords")
    internal val MATCHING_TYPE = EventFields.Enum<PrefixMatchingType>("prefixMatchingType")
    internal val EXACT = EventFields.Boolean("prefixExact")
    internal val MATCHED_LAST_WORD = EventFields.Boolean("prefixMatchedLastWord")
  }

  override fun getFeaturesDefinition(): Array<EventField<*>> {
    return Fields.run {
      arrayOf(
        SAME_START_COUNT, GREEDY_SCORE, GREEDY_WITH_CASE_SCORE, MATCHED_WORDS_SCORE, MATCHED_WORDS_RELATIVE, MATCHED_WORDS_WITH_CASE_SCORE,
        MATCHED_WORDS_WITH_CASE_RELATIVE, SKIPPED_WORDS, MATCHING_TYPE, EXACT, MATCHED_LAST_WORD
      )
    }
  }

  override fun getTextualFeatures(query: String, match: String): List<EventPair<*>> {
    if (query.isEmpty() || match.isEmpty()) return emptyList()
    val scores = PrefixMatchingUtil.PrefixMatchingScores.Builder().build(query, match)
    return Fields.run {
      listOf(
        SAME_START_COUNT.with(scores.start),
        GREEDY_SCORE.with(roundDouble(scores.greedy)),
        GREEDY_WITH_CASE_SCORE.with(roundDouble(scores.greedyWithCase)),
        MATCHED_WORDS_SCORE.with(roundDouble(scores.words)),
        MATCHED_WORDS_RELATIVE.with(roundDouble(scores.wordsRelative)),
        MATCHED_WORDS_WITH_CASE_SCORE.with(roundDouble(scores.wordsWithCase)),
        MATCHED_WORDS_WITH_CASE_RELATIVE.with(roundDouble(scores.wordsWithCaseRelative)),
        SKIPPED_WORDS.with(scores.skippedWords),
        MATCHING_TYPE.with(scores.type),
        EXACT.with(scores.exact),
        MATCHED_LAST_WORD.with(scores.exactFinal)
      )
    }
  }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }
}