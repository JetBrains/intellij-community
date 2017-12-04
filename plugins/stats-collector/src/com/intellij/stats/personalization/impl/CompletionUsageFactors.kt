package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage

/**
 * @author Vitaliy.Bibaev
 */
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
