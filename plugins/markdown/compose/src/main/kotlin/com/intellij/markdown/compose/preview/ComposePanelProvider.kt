// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.compose.preview

import com.intellij.idea.AppMode
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

@OptIn(ExperimentalJewelApi::class)
private class ComposePanelProvider : MarkdownHtmlPanelProvider() {
  override fun createHtmlPanel(): MarkdownHtmlPanel {
    return MarkdownComposePanel()
  }

  override fun createHtmlPanel(project: Project, virtualFile: VirtualFile): MarkdownHtmlPanel {
    return MarkdownComposePanel(project, virtualFile)
  }

  override fun isAvailable(): AvailabilityInfo {
    if (Registry.`is`("enable.markdown.compose.preview.renderer.choice", false) && AppModeAssertions.isMonolith()) {
      return AvailabilityInfo.AVAILABLE
    }
    return AvailabilityInfo.UNAVAILABLE
  }

  override fun getProviderInfo() = ProviderInfo("Compose (experimental)", ComposePanelProvider::class.java.name)
}
