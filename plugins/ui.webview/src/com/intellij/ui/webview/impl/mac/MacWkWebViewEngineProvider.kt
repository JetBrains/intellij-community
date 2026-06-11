// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.mac

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.api.WebViewEngineAvailability
import com.intellij.ui.webview.api.WebViewEngineCapabilities
import com.intellij.ui.webview.api.WebViewEngineId
import com.intellij.ui.webview.api.WebViewEnginePreference
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.engine.WebViewEngineCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class MacWkWebViewEngineProvider : WebViewEngineProvider {
  override val id: WebViewEngineId = WebViewEngineId.SYSTEM_MACOS
  override val displayName: String = "WebKit"
  override val capabilities = WebViewEngineCapabilities(assetServing = true, messagePassing = true, swingEmbedding = true, interactiveInput = true)

  override fun selectionPriority(preference: WebViewEnginePreference): Int? {
    return when (preference) {
      WebViewEnginePreference.System -> PRIMARY_PRIORITY
      WebViewEnginePreference.Jcef -> null
    }
  }

  override fun availabilityBlocking(): WebViewEngineAvailability {
    return if (SystemInfo.isMac) WebViewEngineAvailability.Available else WebViewEngineAvailability.Unavailable("macOS is required")
  }

  override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
    check(SystemInfo.isMac) { "System WebView is supported only on macOS" }
    val engine = createMacWebViewEngine(scope)
    engine.initialize()
    return engine
  }

  override fun createNativeHostPeer(scope: CoroutineScope, engine: WebViewEngineBridge): NativeWebViewHostPeer {
    return MacNativeWebViewHostPeer(scope, engine as MacWebViewEngine)
  }
}

private const val PRIMARY_PRIORITY = 10
