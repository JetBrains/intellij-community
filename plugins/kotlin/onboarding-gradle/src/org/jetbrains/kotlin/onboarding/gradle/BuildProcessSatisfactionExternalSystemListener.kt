// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.gradle

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled

private class BuildProcessSatisfactionExternalSystemListener: ExternalSystemTaskNotificationListener {
    override fun onSuccess(proojecPath: String, id: ExternalSystemTaskId) {
        if (id.projectSystemId.id != "GRADLE") return
        val project = id.findProject() ?: return
        if (project.modules.any { it.hasKotlinPluginEnabled() }) {
            BuildProcessSatisfactionSurveyStore.getInstance().recordBuild()
        }
    }
}