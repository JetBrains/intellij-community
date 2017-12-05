package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.*

/**
 * @author Vitaliy.Bibaev
 */
object UserFactorDescriptions {
    val COMPLETION_TYPE = Descriptor("completionType", ::CompletionTypeUpdater, ::CompletionTypeReader)
    val COMPLETION_FINISH_TYPE =
            Descriptor("completionFinishedType", ::CompletionFinishTypeUpdater, ::CompletionFinishTypeReader)
    val COMPLETION_USAGE = Descriptor("completionUsage", ::CompletionUsageUpdater, ::CompletionUsageReader)
    val PREFIX_LENGTH_ON_COMPLETION = Descriptor("prefixLength", ::PrefixLengthUpdater, ::PrefixLengthReader)
    val SELECTED_ITEM_POSITION = Descriptor("itemPosition", ::ItemPositionUpdater, ::ItemPositionReader)
    val TIME_BETWEEN_TYPING = Descriptor("timeBetweenTyping", ::TimeBetweenTypingUpdater, ::TimeBetweenTypingReader)
    val MNEMONICS_USAGE = Descriptor("mnemonicsUsage", ::MnemonicsUsageUpdater, ::MnemonicsUsageReader)

    class Descriptor<out U : FactorUpdater, out R : FactorReader>(
            override val factorId: String,
            override val updaterFactory: (MutableDoubleFactor) -> U,
            override val readerFactory: (DailyAggregatedDoubleFactor) -> R) : UserFactorDescription<U, R>
}