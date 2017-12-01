package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.*

/**
 * @author Vitaliy.Bibaev
 */
object UserFactorDescriptions {
    val COMPLETION_TYPE = Descriptor("completionType", ::CompletionTypeUpdater, ::CompletionTypeReader)
    val COMPLETION_FINISH_TYPE =
            Descriptor("completionFinishedType", ::CompletionFinishTypeUpdater, ::CompletionFinishTypeReader)


    class Descriptor<out U : FactorUpdater, out R : FactorReader>(
            override val factorId: String,
            override val updaterFactory: (MutableDoubleFactor) -> U,
            override val readerFactory: (DailyAggregatedDoubleFactor) -> R) : UserFactorDescription<U, R>
}