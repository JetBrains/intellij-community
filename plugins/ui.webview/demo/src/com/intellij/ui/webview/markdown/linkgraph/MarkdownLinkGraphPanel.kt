// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.markdown.linkgraph

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class MarkdownLinkGraphPanel(
  private val project: Project,
  private val scope: CoroutineScope,
) {
  val component: JComponent = JPanel(BorderLayout())
  @Volatile private var panel: WebViewPanel? = null

  init {
    loadWebView()
  }

  fun dispose() {
    val createdPanel = panel
    panel = null
    if (createdPanel == null) {
      return
    }
    runBlocking {
      runCatching { createdPanel.close() }
        .onFailure { LOG.warn("Failed to close Markdown link graph WebView", it) }
    }
  }

  private fun loadWebView() {
    scope.launch {
      try {
        val createdPanel = withContext(Dispatchers.EDT) {
          createWebViewPanel(
            scope = scope,
            options = WebViewPanelOptions(
              assetRoot = ASSET_ROOT,
              debugName = "Markdown link graph",
            ),
          ).also { webViewPanel ->
            webViewPanel.interop.implement(MarkdownLinkGraphHostApi.ID, MarkdownLinkGraphHostApiImpl(project))
            webViewPanel.reload()
            component.add(webViewPanel.component, BorderLayout.CENTER)
            component.revalidate()
            component.repaint()
          }
        }
        panel = createdPanel
      }
      catch (t: Throwable) {
        LOG.warn("Failed to load Markdown link graph WebView", t)
      }
    }
  }

  companion object {
    private val LOG = logger<MarkdownLinkGraphPanel>()
    private val ASSET_ROOT = WebViewAssetRoot.forView("markdown-link-graph")
  }
}
