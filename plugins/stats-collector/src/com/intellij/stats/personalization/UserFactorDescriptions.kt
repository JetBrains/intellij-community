package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.*
import com.jetbrains.completion.ranker.features.BinaryFeature
import com.jetbrains.completion.ranker.features.CatergorialFeature
import com.jetbrains.completion.ranker.features.DoubleFeature

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

    fun binaryFeatureDescriptor(feature: BinaryFeature): Descriptor<BinaryFeatureUpdater, BinaryFeatureReader> {
        return Descriptor("binaryFeature:${feature.name}", ::BinaryFeatureUpdater, ::BinaryFeatureReader)
    }

    fun doubleFeatureDescriptor(feature: DoubleFeature): Descriptor<DoubleFeatureUpdater, DoubleFeatureReader> {
        return Descriptor("doudleFeature:${feature.name}", ::DoubleFeatureUpdater, ::DoubleFeatureReader)
    }

    fun categoriealFeatureDescriptor(feature: CatergorialFeature): Descriptor<CategoryFeatureUpdater, CategoryFeatureReader> {
        return Descriptor("categorialFeature:${feature.name}",
                { CategoryFeatureUpdater(feature.categories, it) },
                ::CategoryFeatureReader)
    }

    class Descriptor<out U : FactorUpdater, out R : FactorReader>(
            override val factorId: String,
            override val updaterFactory: (MutableDoubleFactor) -> U,
            override val readerFactory: (DailyAggregatedDoubleFactor) -> R) : UserFactorDescription<U, R>
}