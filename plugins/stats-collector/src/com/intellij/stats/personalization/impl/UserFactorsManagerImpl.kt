/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.completion.FeatureManagerImpl
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorsManager
import com.jetbrains.completion.feature.BinaryFeature
import com.jetbrains.completion.feature.CategoricalFeature
import com.jetbrains.completion.feature.DoubleFeature
import com.jetbrains.completion.feature.impl.FeatureUtils

/**
 * @author Vitaliy.Bibaev
 */
class UserFactorsManagerImpl : UserFactorsManager, ProjectComponent {
    private companion object {
        val LOG = Logger.getInstance(UserFactorsManagerImpl::class.java)
    }
    private val userFactors = mutableMapOf<String, UserFactor>()
    init {
        // user factors
        register(TypedSelectRatio())
        register(ExplicitSelectRatio())
        register(LookupCancelledRatio())

        register(CompletionTypeRatio(CompletionType.BASIC))
        register(CompletionTypeRatio(CompletionType.SMART))
        register(CompletionTypeRatio(CompletionType.CLASS_NAME))

        register(TodayCompletionUsageCount())
        register(TotalUsageCount())
        register(WeekAverageUsageCount())

        register(MostFrequentPrefixLength())
        register(AveragePrefixLength())

        register(AverageSelectedItemPosition())
        register(MaxSelectedItemPosition())
        register(MostFrequentSelectedItemPosition())

        register(AverageTimeBetweenTyping())

        register(MnemonicsRatio())

        // feature-derived factors
        val featureManager = FeatureManagerImpl.getInstance()
        featureManager.binaryFactors.forEach(this::registerBinaryFeatureDerivedFactors)
        featureManager.doubleFactors.forEach(this::registerDoubleFeatureDerivedFactors)
        featureManager.categoricalFactors.forEach(this::registerCategoricalFeatureDerivedFactors)
    }

    private fun registerBinaryFeatureDerivedFactors(feature: BinaryFeature) {
        register(BinaryValueRatio(feature, feature.availableValues.first))
        register(BinaryValueRatio(feature, feature.availableValues.second))
    }

    private fun registerDoubleFeatureDerivedFactors(feature: DoubleFeature) {
        register(MaxDoubleFeatureValue(feature))
        register(MinDoubleFeatureValue(feature))
        register(AverageDoubleFeatureValue(feature))
        register(UndefinedDoubleFeatureValueRatio(feature))
        register(VarianceDoubleFeatureValue(feature))
    }

    private fun registerCategoricalFeatureDerivedFactors(feature: CategoricalFeature) {
        feature.categories.forEach { register(CategoryRatio(feature, it)) }
        register(CategoryRatio(feature, FeatureUtils.OTHER))
        register(MostFrequentCategory(feature))
    }

    override fun getAllFactors(): List<UserFactor> = userFactors.values.toList()

    override fun getAllFactorIds(): List<String> = userFactors.keys.toList()

    override fun getFactor(id: String): UserFactor = userFactors[id]!!

    private fun register(factor: UserFactor) {
        val old = userFactors.put(factor.id, factor)
        if (old != null) {
            if (old === factor) {
                LOG.warn("The same factor was registered twice")
            } else {
                LOG.warn("Two different factors with the same id found: id = ${old.id}, " +
                        "classes = ${listOf(factor.javaClass.canonicalName, old.javaClass.canonicalName)}")
            }
        }
    }
}