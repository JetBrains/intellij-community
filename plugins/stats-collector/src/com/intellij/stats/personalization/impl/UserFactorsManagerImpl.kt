package com.intellij.stats.personalization.impl

import com.intellij.completion.FeatureManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorsManager

/**
 * @author Vitaliy.Bibaev
 */
class UserFactorsManagerImpl(project: Project) : UserFactorsManager, ProjectComponent {
    private companion object {

        val LOG = Logger.getInstance(UserFactorsManagerImpl::class.java)
    }
    private val userFactors = mutableMapOf<String, UserFactor>()
    init {
        // TODO: register all factors here
        FeatureManager.getInstance() // TODO: register feature-derived factors
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