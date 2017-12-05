package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.stats.personalization.*

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

class CompletionTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    fun fireCompletionPerformed(type: CompletionType) {
        factor.incrementOnToday(type.toString())
    }
}

class CompletionTypeRatio(private val type: CompletionType) : UserFactor {

    override val id: String = "CompletionTypeRatioOf$type"
    override fun compute(storage: UserFactorStorage): String? {
        val reader = storage.getFactorReader(UserFactorDescriptions.COMPLETION_TYPE)
        val total = reader.getTotalCompletionCount()
        return if (total == 0.0) "0.0" else (reader.getCompletionCountByType(type) / total).toString()
    }
}

