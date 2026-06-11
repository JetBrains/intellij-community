// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ui.webview.api.WebViewEngine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface WebViewJsMessageReceiver {
  fun transferFromJs(rawJson: String)
}

@ApiStatus.Internal
interface WebViewEngineBridge : WebViewEngine {
  val isHeavyweight: Boolean

  suspend fun transferToJs(rawJson: String)

  fun connectMessageBus(receiver: WebViewJsMessageReceiver)
}
