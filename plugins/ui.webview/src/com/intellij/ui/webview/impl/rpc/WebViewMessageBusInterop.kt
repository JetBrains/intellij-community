// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.rpc

import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewInterop
import com.intellij.ui.webview.api.WebViewMessageRegistration

internal class WebViewMessageBusInterop(
  override val messageBus: WebViewMessageBusImpl,
) : WebViewInterop {
  override fun <T : WebViewImplementable> implement(id: WebViewApiId<T>, implementation: T): WebViewMessageRegistration {
    return messageBus.bindApiImplementation(id.apiClass, implementation, id.namespace)
  }

  override fun <T : WebViewCallable> callable(id: WebViewApiId<T>): T {
    return messageBus.createCallableProxy(id.apiClass, id.namespace)
  }
}
