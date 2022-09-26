// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.UserActivityListener
import com.intellij.ui.UserActivityWatcher
import java.awt.Component
import javax.swing.JCheckBox

interface MavenRCSettingsWatcher: Disposable {
  fun registerComponent(settingId: String, component: Component)
  fun registerUseProjectSettingsCheckbox(component: JCheckBox)
}

internal class MavenRCSettingsWatcherImpl(val project: Project,
                                          private val nameSpace: String,
                                          private val projectSettingsAvailable: Boolean) : MavenRCSettingsWatcher {

  private val configurationId: String = MavenRunConfigurationType.getInstance().id
  val event = AnActionEvent.createFromDataContext("MavenRCSettingsWatcher_event", null, DataContext.EMPTY_CONTEXT)
  private var useProjectSettings: Boolean = false

  override fun registerUseProjectSettingsCheckbox(component: JCheckBox) {
    val settingId = "$nameSpace.useProjectSettings"
    val listener = UserActivityListener {
      useProjectSettings = component.isSelected
      if (component.isFocusOwner) {
        RunConfigurationOptionUsagesCollector.logModifyOption(project, settingId, configurationId, projectSettingsAvailable,
                                                              useProjectSettings, FusInputEvent.from(event))
      }
    }

    val watcher = UserActivityWatcher()
    watcher.addUserActivityListener(listener)
    watcher.register(component)
  }

  override fun registerComponent(settingId: String, component: Component) {
    val fqId = "$nameSpace.$settingId"
    val focusableComponent = if (component is LabeledComponent<*>) {
      component.component
    } else {
      component
    }
    val listener = UserActivityListener {
      if (focusableComponent.isFocusOwner) {
        RunConfigurationOptionUsagesCollector.logModifyOption(project, fqId, configurationId, projectSettingsAvailable, useProjectSettings,
                                                              FusInputEvent.from(event))
      }
    }

    val watcher = UserActivityWatcher()
    watcher.addUserActivityListener(listener)
    watcher.register(focusableComponent)
  }

  override fun dispose() {
  }
}