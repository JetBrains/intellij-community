// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.impl.engine.WebViewEngineAvailability
import com.intellij.ui.webview.impl.engine.WebViewEngineCapabilities
import com.intellij.ui.webview.impl.engine.WebViewEngineId
import com.intellij.ui.webview.impl.engine.WebViewEngineKind
import com.intellij.ui.webview.impl.NativeBridgeLibraryAvailability
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.engine.WebViewEngineCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class WindowsWebView2EngineProvider : WebViewEngineProvider {
  override val id: WebViewEngineId = WebViewEngineId.SYSTEM_WINDOWS
  override val displayName: String = "WebView2"
  override val capabilities = WebViewEngineCapabilities(assetServing = true, messagePassing = true, swingEmbedding = true, interactiveInput = true)

  override fun selectionPriority(preference: WebViewEngineKind): Int? {
    return when (preference) {
      WebViewEngineKind.System -> PRIMARY_PRIORITY
      WebViewEngineKind.Jcef -> null
    }
  }

  override fun availabilityBlocking(): WebViewEngineAvailability {
    if (!SystemInfo.isWindows) return WebViewEngineAvailability.Unavailable("Windows is required")
    return when (val availability = winWebView2BridgeLibrary.availability()) {
      is NativeBridgeLibraryAvailability.Available -> WebViewEngineAvailability.Available
      is NativeBridgeLibraryAvailability.Missing -> WebViewEngineAvailability.Unavailable(availability.message)
    }
  }

  override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
    check(SystemInfo.isWindows) { "System WebView is supported only on Windows" }
    return createWinWebViewEngine(scope, options.debugName)
  }

  override fun createNativeHostPeer(scope: CoroutineScope, engine: WebViewEngineBridge): NativeWebViewHostPeer {
    return WinNativeWebViewHostPeer(engine as WinWebViewEngine)
  }
}

private const val PRIMARY_PRIORITY = 10
