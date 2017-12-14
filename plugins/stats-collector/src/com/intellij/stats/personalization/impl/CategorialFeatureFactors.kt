package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactorBase
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorReaderBase
import com.intellij.stats.personalization.UserFactorUpdaterBase
import com.jetbrains.completion.ranker.features.CatergorialFeature
import com.jetbrains.completion.ranker.features.impl.FeatureUtils

/**
 * @author Vitaliy.Bibaev
 */
class CategoryFeatureReader(factor: DailyAggregatedDoubleFactor)
    : UserFactorReaderBase(factor) {
    fun calculateRatioByValue(): Map<String, Double> {
        val sums = factor.aggregateSum()
        val total = sums.values.sum()
        if (total == 0.0) return emptyMap()
        return sums.mapValues { e -> e.value / total }
    }
}

class CategoryFeatureUpdater(private val knownCategories: Set<String>, factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun update(value: Any?) {
        if (value == null) {
            factor.incrementOnToday(FeatureUtils.UNDEFINED)
        } else {
            val category = value.toString()
            if (category in knownCategories) {
                factor.incrementOnToday(category)
            } else {
                factor.incrementOnToday(FeatureUtils.OTHER)
            }
        }
    }
}

class CategoryRatio(feature: CatergorialFeature, private val categoryName: String)
    : UserFactorBase<CategoryFeatureReader>("categoryFeature:${feature.name}$:categoryName",
        UserFactorDescriptions.categoriealFeatureDescriptor(feature)) {
    override fun compute(reader: CategoryFeatureReader): String {
        return reader.calculateRatioByValue().getOrDefault(categoryName, -1.0).toString()
    }
}

class MostFrequentCategory(feature: CatergorialFeature)
    : UserFactorBase<CategoryFeatureReader>("mostFrequentCategory:${feature.name}",
        UserFactorDescriptions.categoriealFeatureDescriptor(feature)) {
    override fun compute(reader: CategoryFeatureReader): String? {
        return reader.calculateRatioByValue().maxBy { it.value }?.key
    }
}
