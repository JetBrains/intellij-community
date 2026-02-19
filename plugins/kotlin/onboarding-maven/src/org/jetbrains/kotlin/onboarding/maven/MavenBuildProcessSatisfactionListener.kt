// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.maven

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.modules
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled

internal class MavenBuildProcessSatisfactionListener : ExternalSystemTaskNotificationListener {
  override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
    if (id.projectSystemId != MavenUtil.SYSTEM_ID) return
    val project = id.findProject() ?: return
    if (project.modules.any { it.hasKotlinPluginEnabled() }) {
      MavenBuildProcessSatisfactionSurveyStore.getInstance().recordKotlinBuild()
    } else {
      MavenBuildProcessSatisfactionSurveyStore.getInstance().recordNonKotlinBuild()
    }
  }
}
