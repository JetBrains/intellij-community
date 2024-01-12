// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.OnDemandFeedbackResolver

internal class ShowK2SurveyNotificationAfterRestartPostStartupActivity : ProjectActivity {

    companion object {
        private val LOG = Logger.getInstance(ShowK2SurveyNotificationAfterRestartPostStartupActivity::class.java)
    }

    override suspend fun execute(project: Project) {
        K2UserTracker.getInstance().checkIfKotlinPluginModeWasSwitchedOnRestart()
        val propertiesComponent = serviceAsync<PropertiesComponent>()
        val propertiesComponentKey = "K2KotlinSurveyWasProposedOnAppRestart"
        LOG.debug("propertiesComponent.getBoolean($propertiesComponentKey) " + propertiesComponent.getBoolean(propertiesComponentKey))
        if (Registry.`is`("test.k2.feedback.survey", false) || !propertiesComponent.getBoolean(propertiesComponentKey)) {
            if (OnDemandFeedbackResolver.getInstance().showFeedbackNotification(K2FeedbackSurvey::class, project)) {
                propertiesComponent.updateValue(propertiesComponentKey, /* newValue = */ true)
            }
        }
    }
}