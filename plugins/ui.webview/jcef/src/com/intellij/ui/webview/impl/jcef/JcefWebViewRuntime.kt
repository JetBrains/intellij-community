// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.jcef

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.webview.api.WebViewEngineAvailability
import com.intellij.ui.webview.impl.WebViewLogger
import org.cef.CefApp

internal object JcefWebViewRuntime {
  private val lock = Any()

  fun availabilityBlocking(): WebViewEngineAvailability {
    if (!JBCefApp.isSupported()) {
      return WebViewEngineAvailability.Unavailable("JCEF is not supported in this environment")
    }

    if (isCefShuttingDown()) {
      return WebViewEngineAvailability.Unavailable("CEF is shutting down or already terminated")
    }

    return WebViewEngineAvailability.Available
  }

  /**
   * Initializes CEF through JBCEF, the IDE-wide owner of CEF startup and native-library loading.
   */
  fun getOrCreateJBCefApp(): JBCefApp {
    synchronized(lock) {
      check(JBCefApp.isSupported()) { "JCEF is not supported in this environment" }
      check(!isCefShuttingDown()) { "CEF is shutting down or already terminated" }

      val jbCefApp = JBCefApp.getInstance()
      WebViewLogger.LOG.info("Created JCEF WebView runtime through JBCEF")
      return jbCefApp
    }
  }

  private fun isCefShuttingDown(): Boolean =
    CefApp.getState() == CefApp.CefAppState.SHUTTING_DOWN || CefApp.getState() == CefApp.CefAppState.TERMINATED
}
