// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.ui.FragmentStatisticsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.UserActivityListener
import com.intellij.ui.UserActivityWatcher
import java.awt.Component

interface MavenRCSettingsWatcher: Disposable {
  fun registerComponent(settingId: String, component: Component)
}

internal class MavenRCSettingsWatcherImpl(val project: Project, val configurationId: String) : MavenRCSettingsWatcher {

  val event = AnActionEvent.createFromDataContext("MavenRCSettingsWatcher_event", null, DataContext.EMPTY_CONTEXT)

  override fun registerComponent(settingId: String, component: Component) {
    val listener = UserActivityListener {
      FragmentStatisticsService.getInstance().logOptionModified( project, settingId, configurationId, event)
    }

    val watcher = UserActivityWatcher()
    watcher.addUserActivityListener(listener)
    watcher.register(component)
  }

  override fun dispose() {
  }
}