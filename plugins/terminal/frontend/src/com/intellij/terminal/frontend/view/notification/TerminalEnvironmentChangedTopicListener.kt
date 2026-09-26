package com.intellij.terminal.frontend.view.notification

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import org.jetbrains.plugins.terminal.TerminalEnvironmentChanged

internal class TerminalEnvironmentChangedTopicListener : ProjectRemoteTopicListener<TerminalEnvironmentChanged.EnvironmentChange> {
  override val topic: ProjectRemoteTopic<TerminalEnvironmentChanged.EnvironmentChange>
    get() = TerminalEnvironmentChanged.TOPIC

  override fun handleEvent(project: Project, event: TerminalEnvironmentChanged.EnvironmentChange) {
    TerminalSessionReloadNotificationManager.getInstance(project).show(event)
  }
}