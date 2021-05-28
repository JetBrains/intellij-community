// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.stats

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.plugins.pipelines.services.SpaceKtsFileDetector
import com.intellij.space.settings.CloneType
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.stats.SpaceStatsCounterCollector.Companion.LOGIN_STATUS
import com.intellij.space.vcs.SpaceProjectContext

internal class SpaceStatsStateCollector : ProjectUsagesCollector() {
  override fun getGroup() = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val result = HashSet<MetricEvent>()
    val workspace = SpaceWorkspaceComponent.getInstance()
    result.add(LOGIN_STATE.metric(SpaceStatsCounterCollector.LoginState.convert(workspace.loginState.value)))

    val context = SpaceProjectContext.getInstance(project)
    result.add(ASSOCIATED_REPOS_STATE.metric(
      context.currentContext.isAssociatedWithSpaceRepository,
      context.probablyContainsSpaceRepo.value
    ))

    val automationScriptDetector = project.service<SpaceKtsFileDetector>()
    result.add(AUTOMATION_FILE_STATE.metric(automationScriptDetector.dslFile.value != null))

    val cloneType = SpaceSettings.getInstance().cloneType
    result.add(GIT_CLONE_TYPE_STATE.metric(cloneType))

    return result
  }

  companion object {
    @JvmField
    val GROUP = EventLogGroup("space.state", 1)

    @JvmField
    val IS_ASSOCIATED_WITH_SPACE_REPO = EventFields.Boolean("is_associated_with_space_repo")

    @JvmField
    val IS_PROBABLY_CONTAINS_SPACE_REPO = EventFields.Boolean("is_probably_contains_space_repo")

    @JvmField
    val AUTOMATION_FILE_EXISTS = EventFields.Boolean("automation_file_exists")

    @JvmField
    val GIT_CLONE_TYPE = EventFields.Enum<CloneType>("type")

    @JvmField
    val LOGIN_STATE = GROUP.registerEvent("login_status", LOGIN_STATUS)

    @JvmField
    val ASSOCIATED_REPOS_STATE = GROUP.registerEvent(
      "associated_repos_state",
      IS_ASSOCIATED_WITH_SPACE_REPO,
      IS_PROBABLY_CONTAINS_SPACE_REPO
    )

    @JvmField
    val AUTOMATION_FILE_STATE = GROUP.registerEvent("automation_file_state", AUTOMATION_FILE_EXISTS)

    @JvmField
    val GIT_CLONE_TYPE_STATE = GROUP.registerEvent("git_clone_type", GIT_CLONE_TYPE)
  }
}