package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.completion.FeatureManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorsManager

/**
 * @author Vitaliy.Bibaev
 */
class UserFactorsManagerImpl : UserFactorsManager, ProjectComponent {
    private companion object {
        val LOG = Logger.getInstance(UserFactorsManagerImpl::class.java)
    }
    private val userFactors = mutableMapOf<String, UserFactor>()
    init {
        // TODO: register all factors here
        FeatureManager.getInstance() // TODO: register feature-derived factors

        // user factors
        register(ExplicitCompletionRatio())
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
    }

    override fun getAllFactors(): List<UserFactor> = userFactors.values.toList()

    override fun getAllFactorIds(): List<String> = userFactors.keys.toList()

    override fun getFactor(id: String): UserFactor = userFactors[id]!!

    override fun getFeatureFactor(featureName: String): UserFactor.FeatureFactor? {
        return null
    }

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