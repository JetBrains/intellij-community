// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
class WebViewPanel internal constructor(
  val webView: WebView,
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
  val enginePreference: WebViewEnginePreference = WebViewEnginePreference.System,
  val requirements: WebViewEngineRequirements = WebViewEngineRequirements(
    assetServing = true,
    messagePassing = true,
    swingEmbedding = true,
  ),
  val debugName: String? = null,
)

@ApiStatus.Experimental
@RequiresEdt
suspend fun createWebViewPanel(
  scope: CoroutineScope,
  options: WebViewPanelOptions,
): WebViewPanel {
  return createWebViewPanel(scope, options, WebViewRuntime.getInstance())
}

@RequiresEdt
internal suspend fun createWebViewPanel(
  scope: CoroutineScope,
  options: WebViewPanelOptions,
  runtime: WebViewRuntime,
): WebViewPanel {
  return runtime.createWebViewPanel(scope, options)
}
