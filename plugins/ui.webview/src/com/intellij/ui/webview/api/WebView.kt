// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WebView {
  /**
   * Typed protocol facade for this WebView instance.
   */
  val interop: WebViewInterop
  val runtimeInfo: WebViewRuntimeInfo

  suspend fun loadFile(file: VirtualFile)

  suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath = WebViewAssetPath.indexHtml(), query: String? = null)

  suspend fun loadHtml(@Language("HTML") html: String)

  suspend fun evaluateJavaScript(@Language("JavaScript") script: String): WebViewScriptResult

  suspend fun close()
}

@ApiStatus.Experimental
data class WebViewScriptResult(val value: String?)
