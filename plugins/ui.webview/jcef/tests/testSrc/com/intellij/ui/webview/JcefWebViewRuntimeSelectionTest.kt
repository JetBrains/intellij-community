// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.webview.impl.engine.WebView
import com.intellij.ui.webview.impl.engine.WebViewEngineId
import com.intellij.ui.webview.impl.engine.WebViewEngineKind
import com.intellij.ui.webview.impl.engine.WebViewRuntime
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import com.intellij.ui.webview.impl.jcef.JcefEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.CefApp
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class JcefWebViewRuntimeSelectionTest {
  @Test
  fun jcefProvider_participatesInSystemEngineSelectionWithPlatformPriority() {
    val provider = JcefEngineProvider()

    assertEquals(10, provider.selectionPriority(WebViewEngineKind.Jcef))
    assertEquals(if (SystemInfo.isLinux) 0 else 100, provider.selectionPriority(WebViewEngineKind.System))
  }

  @Test
  fun createWebView_usesJcefProviderWhenRegistryOverrideIsEnabledProgrammatically(@TestDisposable disposable: Disposable): Unit = runBlocking {
    registerJcefWebViewEngineProviders(disposable)
    withProgrammaticJcefOverride {
      assumeTrue(JBCefApp.isSupported(), "JCEF is not supported in this environment")

      @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without product code.
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      var webView: WebView? = null
      try {
        webView = WebViewRuntime.getInstance().createWebView(scope)

        assertEquals(WebViewEngineId.JCEF, webView.runtimeInfo.engineId)
      }
      finally {
        webView?.close()
        scope.cancel()
      }
    }
  }

  private fun registerJcefWebViewEngineProviders(parentDisposable: Disposable) {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    val shouldDeclareWebViewEngineProviderEp = !extensionArea.hasExtensionPoint(WebViewEngineProvider.EP_NAME)
    if (shouldDeclareWebViewEngineProviderEp) {
      extensionArea.registerExtensionPoint(
        WebViewEngineProvider.EP_NAME.name,
        WebViewEngineProvider::class.java.name,
        ExtensionPoint.Kind.INTERFACE,
        true,
      )
    }

    val providersDisposable = Disposer.newDisposable("JCEF WebView engine providers")
    Disposer.register(parentDisposable) {
      Disposer.dispose(providersDisposable)
      if (shouldDeclareWebViewEngineProviderEp) {
        extensionArea.unregisterExtensionPoint(WebViewEngineProvider.EP_NAME.name)
      }
    }

    extensionArea.getExtensionPoint(WebViewEngineProvider.EP_NAME)
      .registerExtension(JcefEngineProvider(), providersDisposable)
  }

  private suspend fun withProgrammaticJcefOverride(action: suspend () -> Unit) {
    val webViewEngine = Registry.get(WEBVIEW_ENGINE_REGISTRY_KEY)
    val headless = Registry.get(JCEF_HEADLESS_REGISTRY_KEY)
    val osr = Registry.get(JCEF_OFFSCREEN_REGISTRY_KEY)
    val testMode = Registry.get(JCEF_TEST_MODE_REGISTRY_KEY)
    try {
      webViewEngine.setValue(JCEF_ENGINE_VALUE)
      headless.setValue(true)
      osr.setValue(true)
      testMode.setValue(true)
      action()
    }
    finally {
      webViewEngine.resetToDefault()
      headless.resetToDefault()
      osr.resetToDefault()
      testMode.resetToDefault()
    }
  }

  private companion object {
    private const val WEBVIEW_ENGINE_REGISTRY_KEY = "ide.webview.engine"
    private const val JCEF_ENGINE_VALUE = "JCEF"
    private const val JCEF_HEADLESS_REGISTRY_KEY = "ide.browser.jcef.headless.enabled"
    private const val JCEF_OFFSCREEN_REGISTRY_KEY = "ide.browser.jcef.osr.enabled"
    private const val JCEF_TEST_MODE_REGISTRY_KEY = "ide.browser.jcef.testMode.enabled"

    @JvmStatic
    @AfterAll
    fun shutdownJcefApp(): Unit = runBlocking {
      val cefApp = CefApp.getInstanceIfAny() ?: return@runBlocking
      cefApp.dispose()
      withTimeoutOrNull(2.seconds) {
        while (CefApp.getState() != CefApp.CefAppState.TERMINATED) {
          delay(50.milliseconds)
        }
      }
    }
  }
}
