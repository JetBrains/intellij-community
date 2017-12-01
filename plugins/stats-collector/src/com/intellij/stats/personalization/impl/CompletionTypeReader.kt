package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.stats.personalization.FactorReader

/**
 * @author Vitaliy.Bibaev
 */
class CompletionTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getCompletionCountByType(type: CompletionType): Double =
            factor.aggregateSum().getOrDefault(type.toString(), 0.0)

    fun getComletionCountByTypeOnToday(type: CompletionType): Double =
            factor.onToday().getOrDefault(type.toString(), 0.0)

    fun getTotalCompletionCount(): Double = factor.aggregateSum().values.sum()
}