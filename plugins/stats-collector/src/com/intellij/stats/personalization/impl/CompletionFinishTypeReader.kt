package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.DateUtil
import com.intellij.stats.personalization.FactorReader

/**
 * @author Vitaliy.Bibaev
 */
class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getTotalExplicitSelectCount(): Int =
            factor.availableDates()
                    .sumByDouble { factor.onDate(it)?.get(CompletionFinishTypeUpdater.EXPLICIT_SELECT_KEY) ?: 0.0 }.toInt()

    fun getTotalTypedSelectCount(): Int =
            factor.availableDates()
                    .sumByDouble { factor.onDate(it)?.get(CompletionFinishTypeUpdater.TYPED_SELECT_KEY) ?: 0.0 }.toInt()

    fun getExplicitSelectCountOnToday(): Int = factor.onDate(DateUtil.today())
            ?.get(CompletionFinishTypeUpdater.EXPLICIT_SELECT_KEY)?.toInt() ?: 0

    fun getTypedSelectCountOnToday(): Int = factor.onDate(DateUtil.today())
            ?.get(CompletionFinishTypeUpdater.TYPED_SELECT_KEY)?.toInt() ?: 0
}