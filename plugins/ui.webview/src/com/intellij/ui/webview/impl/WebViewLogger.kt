// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus

/**
 * Dedicated logger category for the WebView runtime.
 *
 * Usage: `WebViewLogger.LOG.info("lifecycle: created webview")`
 *
 * Category: `#com.intellij.ui.webview`
 */
@ApiStatus.Internal
object WebViewLogger {
  val LOG: Logger = Logger.getInstance("#com.intellij.ui.webview")

  fun logLifecycle(event: String, details: String = "") {
    if (details.isNotEmpty()) {
      LOG.info("lifecycle: $event — $details")
    }
    else {
      LOG.info("lifecycle: $event")
    }
  }

  fun logPerf(metric: String, valueMs: Long) {
    LOG.info("perf: $metric = ${valueMs}ms")
  }
}
