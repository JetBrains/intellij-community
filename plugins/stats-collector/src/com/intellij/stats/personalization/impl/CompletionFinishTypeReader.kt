package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.DateUtil
import com.intellij.stats.personalization.FactorReader

/**
 * @author Vitaliy.Bibaev
 */
class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getTotalExplicitSelectCount(): Double =
            factor.aggregateSum()[CompletionFinishTypeUpdater.EXPLICIT_SELECT_KEY] ?: 0.0

    fun getTotalTypedSelectCount(): Double =
            factor.aggregateSum()[CompletionFinishTypeUpdater.TYPED_SELECT_KEY] ?: 0.0

    fun getExplicitSelectCountOnToday(): Double =
            factor.onToday()[CompletionFinishTypeUpdater.EXPLICIT_SELECT_KEY] ?: 0.0

    fun getTypedSelectCountOnToday(): Double = factor.onToday()[CompletionFinishTypeUpdater.TYPED_SELECT_KEY] ?: 0.0
}