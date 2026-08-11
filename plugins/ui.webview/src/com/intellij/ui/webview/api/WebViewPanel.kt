// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ui.webview.api

import com.intellij.ui.webview.impl.CONSOLE_LOG_CATEGORY
import com.intellij.ui.webview.impl.engine.WebView
import com.intellij.ui.webview.impl.engine.WebViewRuntime
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
class WebViewPanel internal constructor(
  @get:ApiStatus.Internal val webView: WebView,
  val component: JComponent,
  private val reloadPage: suspend (WebView) -> Unit,
) {
  val interop: WebViewInterop
    get() = webView.interop

  suspend fun reload() {
    reloadPage(webView)
  }

  suspend fun close() {
    webView.close()
  }
}

@ApiStatus.Experimental
data class WebViewPanelOptions(
  val assetRoot: WebViewAssetRoot,
  val indexPath: WebViewAssetPath = WebViewAssetPath.indexHtml(),
  val query: String? = null,
  val debugName: String? = null,
  val consoleLogCategory: String = CONSOLE_LOG_CATEGORY,
)

/**
 * Creates a Swing-hosted WebView panel for a bundled web UI. This is the recommended entry point for feature/plugin code.
 *
 * Build the [WebViewPanelOptions.assetRoot] with [WebViewAssetRoot.forView], create the panel here, mount
 * [WebViewPanel.component] into the Swing hierarchy, and talk to the page through [WebViewPanel.interop]
 * ([WebViewInterop.implement] / [WebViewInterop.callable] with typed [WebViewApiId] protocols).
 *
 * Must be called on the EDT. The given [scope] owns the WebView lifetime; cancel/complete it to dispose the panel.
 */
@ApiStatus.Experimental
@RequiresEdt
suspend fun createWebViewPanel(
  scope: CoroutineScope,
  options: WebViewPanelOptions,
): WebViewPanel {
  return WebViewRuntime.getInstance().createWebViewPanel(scope, options)
}
