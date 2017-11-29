package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.CompletionTypeReader
import com.intellij.stats.personalization.impl.CompletionTypeUpdater
import com.intellij.stats.personalization.impl.DailyAggregatedDoubleFactor
import com.intellij.stats.personalization.impl.MutableDoubleFactor

/**
 * @author Vitaliy.Bibaev
 */
object UserFactorDescriptions {
    val COMPLETION_FINISH_TYPE = Descriptor("completionFinishedType", ::CompletionTypeUpdater, ::CompletionTypeReader)


    class Descriptor<out U : FactorUpdater, out R : FactorReader>(
            override val factorId: String,
            override val updaterFactory: (MutableDoubleFactor) -> U,
            override val readerFactory: (DailyAggregatedDoubleFactor) -> R) : UserFactorDescription<U, R>
}