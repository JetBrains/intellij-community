// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.jcef

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.engine.WebViewEngineAvailability
import com.intellij.ui.webview.impl.engine.WebViewEngineCapabilities
import com.intellij.ui.webview.impl.engine.WebViewEngineCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineId
import com.intellij.ui.webview.impl.engine.WebViewEngineKind
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import kotlinx.coroutines.CoroutineScope

internal class JcefEngineProvider : WebViewEngineProvider {
  override val id: WebViewEngineId = WebViewEngineId.JCEF
  override val displayName: String = "JCEF"
  override val capabilities = JCEF_CAPABILITIES

  override fun selectionPriority(preference: WebViewEngineKind): Int? {
    return when (preference) {
      WebViewEngineKind.Jcef -> JCEF_EXPLICIT_PRIORITY
      WebViewEngineKind.System -> if (SystemInfo.isLinux) LINUX_DEFAULT_PRIORITY else SYSTEM_FALLBACK_PRIORITY
    }
  }

  override fun availabilityBlocking(): WebViewEngineAvailability = JcefWebViewRuntime.availabilityBlocking()

  override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
    return createJcefWebViewEngine(parentScope = scope, documentStartScripts = options.documentStartScripts)
  }
}

private val JCEF_CAPABILITIES = WebViewEngineCapabilities(assetServing = true, messagePassing = true, swingEmbedding = true, interactiveInput = true)
private const val JCEF_EXPLICIT_PRIORITY = 10
private const val LINUX_DEFAULT_PRIORITY = 0
private const val SYSTEM_FALLBACK_PRIORITY = 100
