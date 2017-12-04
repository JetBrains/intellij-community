package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactorReaderBase

/**
 * @author Vitaliy.Bibaev
 */
class CompletionUsageReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun getTodayCount(): Double = factor.onToday().getOrDefault("count", 0.0)

    fun getTotalCount(): Double = factor.aggregateSum().getOrDefault("count", 0.0)

    fun getWeekAverage(): Double = factor.aggregateAverage().getOrDefault("count", 0.0)
}