// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

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
internal class LinuxWebKitEngineProvider : WebViewEngineProvider {
  override val id: WebViewEngineId = WebViewEngineId.SYSTEM_LINUX
  override val displayName: String = "WebKit"
  override val capabilities = WebViewEngineCapabilities(assetServing = false, messagePassing = true, swingEmbedding = true, interactiveInput = false)

  override fun selectionPriority(preference: WebViewEnginePreference): Int? {
    return when (preference) {
      WebViewEnginePreference.System -> null
      WebViewEnginePreference.Jcef -> null
    }
  }

  override fun availabilityBlocking(): WebViewEngineAvailability {
    if (!SystemInfo.isLinux) return WebViewEngineAvailability.Unavailable("Linux is required")
    return WebViewEngineAvailability.Unavailable("Linux WebKitGTK WebView is disabled")
  }

  override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
    error("Linux WebKitGTK WebView is disabled")
  }

  override fun createNativeHostPeer(scope: CoroutineScope, engine: WebViewEngineBridge): NativeWebViewHostPeer {
    return LinuxNativeWebViewHostPeer(engine as LinuxWebKitWebViewEngine)
  }

}
