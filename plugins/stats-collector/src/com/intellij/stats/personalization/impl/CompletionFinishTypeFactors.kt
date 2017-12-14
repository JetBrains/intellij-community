package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
private val explicitSelectKey = "explicitSelect"
private val typedSelectKey = "typedSelect"

class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getTotalExplicitSelectCount(): Double =
            factor.aggregateSum()[explicitSelectKey] ?: 0.0

    fun getTotalTypedSelectCount(): Double =
            factor.aggregateSum()[typedSelectKey] ?: 0.0

}

class CompletionFinishTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    fun fireExplicitCompletionPerformed() {
        factor.incrementOnToday(explicitSelectKey)
    }

    fun fireTypedSelectPerformed() {
        factor.incrementOnToday(typedSelectKey)
    }
}

class ExplicitCompletionRatio : UserFactor {
    override val id: String = "explicitSelectRatio"

    override fun compute(storage: UserFactorStorage): String? {
        val factorReader = storage.getFactorReader(UserFactorDescriptions.COMPLETION_FINISH_TYPE)
        val total = factorReader.getTotalExplicitSelectCount() + factorReader.getTotalTypedSelectCount()
        if (total == 0.0) return null
        return (factorReader.getTotalExplicitSelectCount() / total).toString()
    }
}