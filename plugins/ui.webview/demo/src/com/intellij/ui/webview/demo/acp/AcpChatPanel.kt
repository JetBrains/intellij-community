// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewIconSet
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Hosts the assistant-ui ACP chat view. Mirrors `MarkdownLinkGraphPanel`: creates the WebView, registers the
 * typed host API the page calls, and resolves the page-API proxy used to stream agent output back.
 */
internal class AcpChatPanel(
  private val project: Project,
  private val scope: CoroutineScope,
) {
  val component: JComponent = JPanel(BorderLayout())

  @Volatile private var bridge: AcpProcessBridge? = null

  init {
    loadWebView()
  }

  /** The WebView is owned by [scope]; cancelling the scope closes it. Only our own process resource is released here. */
  fun dispose() {
    bridge?.stop()
    bridge = null
  }

  private fun loadWebView() {
    scope.launch {
      try {
        withContext(Dispatchers.EDT) {
          createWebViewPanel(
            scope = scope,
            options = WebViewPanelOptions(
              assetRoot = ASSET_ROOT,
              debugName = "ACP chat",
            ),
          ).also { webViewPanel ->
            val pageApi = webViewPanel.interop.callable(AcpBridgePageApi.ID)
            val processBridge = AcpProcessBridge(project, scope, pageApi)
            bridge = processBridge
            webViewPanel.interop.implement(AcpBridgeHostApi.ID, AcpBridgeHostApiImpl(project, processBridge))
            webViewPanel.reload()
            component.add(webViewPanel.component, BorderLayout.CENTER)
            component.revalidate()
            component.repaint()
          }
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to load ACP chat WebView", t)
      }
    }
  }

  private companion object {
    private const val RESOURCE_ROOT = "webview/views/acp-chat"
    private val LOG = logger<AcpChatPanel>()
    private val ASSET_ROOT = WebViewAssetRoot
      .fromClasspath(AcpChatPanel::class.java, WebViewAssetPath.of(RESOURCE_ROOT))
      .withIconSets(WebViewIconSet.of("AcpChatIcons", AcpChatPanel::class.java))
  }
}
