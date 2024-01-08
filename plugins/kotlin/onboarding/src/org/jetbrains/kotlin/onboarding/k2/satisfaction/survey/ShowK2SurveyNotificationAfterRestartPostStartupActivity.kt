// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.feedback.impl.OnDemandFeedbackResolver

internal class ShowK2SurveyNotificationAfterRestartPostStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val propertiesComponent = serviceAsync<PropertiesComponent>()
        val propertiesComponentKey = "K2KotlinSurveyWasProposedOnAppRestart"
        if (!propertiesComponent.getBoolean(propertiesComponentKey)) {
            if (OnDemandFeedbackResolver.getInstance().showFeedbackNotification(K2FeedbackSurvey::class, project)) {
                propertiesComponent.updateValue(propertiesComponentKey, /* newValue = */ true)
            }
        }
    }
}