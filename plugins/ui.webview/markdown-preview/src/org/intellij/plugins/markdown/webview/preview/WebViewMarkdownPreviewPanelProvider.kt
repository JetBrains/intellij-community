// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider

class WebViewMarkdownPreviewPanelProvider : MarkdownHtmlPanelProvider() {
  override fun createHtmlPanel(): MarkdownHtmlPanel {
    return WebViewMarkdownPreviewPanel()
  }

  override fun createHtmlPanel(project: Project, virtualFile: VirtualFile): MarkdownHtmlPanel {
    return WebViewMarkdownPreviewPanel(project, virtualFile)
  }

  override fun isAvailable(): AvailabilityInfo {
    return AvailabilityInfo.AVAILABLE
  }

  override fun getProviderInfo(): ProviderInfo {
    return ProviderInfo("System WebView (Experimental)", WebViewMarkdownPreviewPanelProvider::class.java.name)
  }
}
