// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.ui.webview.impl.linux.LinuxWebKitEngineProvider
import com.intellij.ui.webview.impl.mac.MacWkWebViewEngineProvider
import com.intellij.ui.webview.impl.windows.WindowsWebView2EngineProvider

internal fun defaultWebViewEngineProviders(): List<WebViewEngineProvider> {
  return listOf(
    MacWkWebViewEngineProvider(),
    WindowsWebView2EngineProvider(),
    LinuxWebKitEngineProvider(),
  )
}
