// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.onboarding.k2.EnableK2NotificationService

private class K2OnboardingPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        serviceAsync<K2UserTracker>().checkIfKotlinPluginModeWasSwitchedOnRestart()

        val k2UserTrackerState = serviceAsync<K2UserTracker>().state
        serviceAsync<EnableK2NotificationService>().showEnableK2Notification(project, k2UserTrackerState)
    }
}