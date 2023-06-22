// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.getOpenedProjects

internal class SettingsChangeLafListener: LafManagerListener {
  override fun lookAndFeelChanged(source: LafManager) {
    if (!LoadingState.APP_STARTED.isOccurred) {
      return
    }
    for (project in getOpenedProjects()) {
      project.serviceIfCreated<MarkdownSettings>()?.let { settings ->
        val publisher = project.messageBus.syncPublisher(MarkdownSettings.ChangeListener.TOPIC)
        publisher.beforeSettingsChanged(settings)
        publisher.settingsChanged(settings)
      }
    }
  }
}
