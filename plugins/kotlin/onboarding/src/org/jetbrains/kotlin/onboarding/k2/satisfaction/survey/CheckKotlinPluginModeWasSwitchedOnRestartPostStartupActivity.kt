// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class CheckKotlinPluginModeWasSwitchedOnRestartPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        serviceAsync<K2UserTracker>().checkIfKotlinPluginModeWasSwitchedOnRestart()
    }
}