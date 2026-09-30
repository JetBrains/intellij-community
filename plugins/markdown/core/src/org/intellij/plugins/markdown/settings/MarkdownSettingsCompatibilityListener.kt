// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.util.application

@Suppress("DEPRECATION")
internal class MarkdownSettingsCompatibilityListener: MarkdownSettings.ChangeListener {
  override fun beforeSettingsChanged(settings: MarkdownSettings) {
    for (project in getOpenedProjects()) {
      project.messageBus.syncPublisher(MarkdownSettings.ChangeListener.TOPIC).beforeSettingsChanged(MarkdownSettings.getInstance(project))
    }
  }

  override fun settingsChanged(settings: MarkdownSettings) {
    for (project in getOpenedProjects()) {
      project.messageBus.syncPublisher(MarkdownSettings.ChangeListener.TOPIC).settingsChanged(MarkdownSettings.getInstance(project))
    }
    application.messageBus.syncPublisher(MarkdownPreviewSettings.ChangeListener.TOPIC).settingsChanged(service<MarkdownPreviewSettings>())
  }
}
