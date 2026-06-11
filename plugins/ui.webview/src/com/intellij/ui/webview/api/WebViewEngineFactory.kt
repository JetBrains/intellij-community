// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Factory for creating platform-specific [WebViewEngine] instances.
 */
@ApiStatus.Experimental
object WebViewEngineFactory {
  @JvmStatic
  @Suppress("unused")
  fun createEngine(
    scope: CoroutineScope,
    engineKind: WebViewEngineKind = WebViewEngineKind.System,
    jcefNativeBundlePath: Path? = null,
  ): WebViewEngine {
    return WebViewRuntime.getInstance().createEngine(scope, engineKind, jcefNativeBundlePath)
  }

  /**
   * Creates a macOS [WebViewEngine] backed by WKWebView.
   */
  @JvmStatic
  fun createMacOsEngine(scope: CoroutineScope): WebViewEngine {
    check(SystemInfo.isMac) { "System WebView is supported only on macOS" }
    return WebViewRuntime.getInstance().createEngine(
      scope = scope,
      preference = WebViewEnginePreference.System,
      strictPreference = true,
    )
  }

  /**
   * Creates a Windows [WebViewEngine] backed by WebView2.
   */
  @JvmStatic
  fun createWindowsEngine(scope: CoroutineScope): WebViewEngine {
    check(SystemInfo.isWindows) { "System WebView is supported only on Windows" }
    return WebViewRuntime.getInstance().createEngine(
      scope = scope,
      preference = WebViewEnginePreference.System,
      strictPreference = true,
    )
  }

  /**
   * Creates a Linux [WebViewEngine] backed by WebKitGTK.
   *
   * Local Wayland/WLToolkit sessions use an offscreen WebKitGTK renderer with Swing snapshots
   * until a JBR child-surface API is available.
   */
  @JvmStatic
  fun createLinuxEngine(scope: CoroutineScope): WebViewEngine {
    check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
    return WebViewRuntime.getInstance().createEngine(
      scope = scope,
      preference = WebViewEnginePreference.System,
      strictPreference = true,
    )
  }
}
