// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.webview.api.WebViewInterop
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewFocusEntrySink

private val LOG = fileLogger()

internal fun WebViewInterop.createWebViewFocusEntrySink(): WebViewFocusEntrySink {
  val pageApi = callable(WebViewFocusPageApi.ID)
  return object : WebViewFocusEntrySink {
    override fun enterWebViewFocus(direction: WebViewFocusDirection) {
      runCatching {
        pageApi.enter(WebViewFocusEntry(direction))
      }.onFailure { error ->
        LOG.debug("[wvi-focus] host page.enter.failed; direction=$direction", error)
      }
    }

    override fun leaveWebViewFocus() {
      runCatching {
        pageApi.leave()
      }.onFailure { error ->
        LOG.debug("[wvi-focus] host page.leave.failed", error)
      }
    }
  }
}

internal fun WebViewInterop.registerWebViewFocusExitHandler(host: SwingWebViewHostPanel): WebViewMessageRegistration {
  return implement(WebViewFocusHostApi.ID, object : WebViewFocusHostApi {
    override fun activated() {
      host.activateWebViewFocus()
    }

    override fun exit(params: WebViewFocusExit) {
      host.exitWebViewFocus(params.direction)
    }
  })
}
