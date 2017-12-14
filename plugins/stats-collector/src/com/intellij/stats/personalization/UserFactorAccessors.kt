package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.DailyAggregatedDoubleFactor
import com.intellij.stats.personalization.impl.MutableDoubleFactor

/**
 * @author Vitaliy.Bibaev
 */
abstract class UserFactorReaderBase(protected val factor: DailyAggregatedDoubleFactor) : FactorReader

abstract class UserFactorUpdaterBase(protected val factor: MutableDoubleFactor) : FactorUpdater