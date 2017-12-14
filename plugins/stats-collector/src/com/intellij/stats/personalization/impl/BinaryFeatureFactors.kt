package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactorBase
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorReaderBase
import com.intellij.stats.personalization.UserFactorUpdaterBase
import com.jetbrains.completion.ranker.features.BinaryFeature
import com.jetbrains.completion.ranker.features.impl.FeatureUtils

/**
 * @author Vitaliy.Bibaev
 */
class BinaryFeatureReader(factor: DailyAggregatedDoubleFactor)
    : UserFactorReaderBase(factor) {
    fun calculateRatioByValue(): Map<String, Double> {
        val sums = factor.aggregateSum()
        val total = sums.values.sum()
        if (total == 0.0) return emptyMap()
        return sums.mapValues { e -> e.value / total }
    }
}

class BinaryFeatureUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun update(value: Any?) {
        factor.incrementOnToday(value?.toString() ?: FeatureUtils.UNDEFINED)
    }
}

class BinaryValueRatio(feature: BinaryFeature, private val valueName: String)
    : UserFactorBase<BinaryFeatureReader>("BinaryValueRatio:${feature.name}:$valueName",
        UserFactorDescriptions.binaryFeatureDescriptor(feature)) {
    override fun compute(reader: BinaryFeatureReader): String {
        return reader.calculateRatioByValue().getOrDefault(valueName, -1.0).toString()
    }
}
