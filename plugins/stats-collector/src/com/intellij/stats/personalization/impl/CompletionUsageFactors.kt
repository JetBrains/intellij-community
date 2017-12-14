package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
class CompletionUsageReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun getTodayCount(): Double = factor.onToday().getOrDefault("count", 0.0)

    fun getTotalCount(): Double = factor.aggregateSum().getOrDefault("count", 0.0)

    fun getWeekAverage(): Double = factor.aggregateAverage().getOrDefault("count", 0.0)
}

class CompletionUsageUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireCompletionUsed() {
        factor.incrementOnToday("count")
    }
}

class TodayCompletionUsageCount : CompletionUsageFactorBase("todayCompletionCount") {
    override fun compute(reader: CompletionUsageReader): Double? = reader.getTodayCount()
}

class WeekAverageUsageCount : CompletionUsageFactorBase("weekAverageDailyCompletionCount") {
    override fun compute(reader: CompletionUsageReader): Double? = reader.getWeekAverage()
}

class TotalUsageCount : CompletionUsageFactorBase("totalCompletionCountInLastDays") {
    override fun compute(reader: CompletionUsageReader): Double? = reader.getTotalCount()
}

abstract class CompletionUsageFactorBase(override val id: String) : UserFactor {
    override final fun compute(storage: UserFactorStorage): String? =
            compute(storage.getFactorReader(UserFactorDescriptions.COMPLETION_USAGE))?.toString()

    abstract fun compute(reader: CompletionUsageReader): Double?
}
