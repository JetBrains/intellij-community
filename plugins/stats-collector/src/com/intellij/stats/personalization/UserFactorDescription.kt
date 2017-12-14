package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.DailyAggregatedDoubleFactor
import com.intellij.stats.personalization.impl.MutableDoubleFactor
import com.intellij.stats.personalization.impl.UserFactorStorageBase

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorDescription<out U : FactorUpdater, out R : FactorReader> {
    val factorId: String
    val updaterFactory: (MutableDoubleFactor) -> U
    val readerFactory: (DailyAggregatedDoubleFactor) -> R
}