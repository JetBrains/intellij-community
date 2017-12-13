package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
class TimeBetweenTypingReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun averageTime(): Double? {
        return FactorsUtil.calculateAverageByAllDays(factor)
    }
}

class TimeBetweenTypingUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireTypingPerformed(delayMs: Int) {
        factor.updateOnDate(DateUtil.today()) {
            FactorsUtil.updateAverageValue(this, delayMs.toDouble())
        }
    }
}

class AverageTimeBetweenTyping
    : UserFactorBase<TimeBetweenTypingReader>("averageTimeBetweenTyping", UserFactorDescriptions.TIME_BETWEEN_TYPING) {
    override fun compute(reader: TimeBetweenTypingReader): String? = reader.averageTime()?.toString()
}