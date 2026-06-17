// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Platform-independent runtime engine for a native system WebView instance.
 *
 * All methods must be called from the EDT or a coroutine scope bound to the EDT,
 * unless documented otherwise. [evaluateJavaScript] is a suspend function that
 * internally dispatches to the native main thread.
 */
@ApiStatus.Internal
interface WebViewEngine {
  suspend fun loadFile(file: Path)

  /**
   * Loads [entry] from [root] through the platform WebView asset handler and a virtual origin.
   */
  suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath = WebViewAssetPath.indexHtml(), query: String? = null)

  suspend fun loadHtml(@Language("HTML") html: String, baseFile: Path? = null)

  /**
   * Evaluates [script] in the WebView's JavaScript context and returns the result as a string,
   * or `null` if the evaluation produces no result or the WebView is closed.
   */
  suspend fun evaluateJavaScript(@Language("JavaScript") script: String): String?

  suspend fun close()
}
