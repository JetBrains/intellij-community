// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.webview.impl.engine.WebView
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.createWebViewPanel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
@Suppress("JSUnresolvedVariable")
internal class WebViewInIdeSmokeTest {
  private val projectFixture = projectFixture()
  private val project get() = projectFixture.get()

  @Test
  fun toolWindow_loadsResourcePage_andExecutesJavaScript(): Unit = runBlocking {
    assumeFalse(GraphicsEnvironment.isHeadless(), "java.awt.headless=true")

    @Suppress("RAW_SCOPE_CREATION") // Smoke test owns a short-lived WebView scope.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val toolWindowManager = ToolWindowManager.getInstance(project)
    var panel: WebViewPanel? = null
    var fallbackFrame: JFrame? = null

    try {
      val smokePanel = createPanelOrSkip(scope)
      panel = smokePanel
      val host = withContext(Dispatchers.EDT) {
        smokePanel.component.apply {
          preferredSize = Dimension(480, 320)
        }
      }
      val toolWindow = registerSmokeToolWindow(toolWindowManager)
      addSmokeContent(toolWindow, host)

      assertSame(toolWindow, toolWindowManager.getToolWindow(TOOL_WINDOW_ID), smokeFailureMessage("Tool window is not registered"))
      assertEquals(1, toolWindow.contentManager.contentCount, smokeFailureMessage("Tool window content was not added"))

      activateToolWindow(toolWindow)
      fallbackFrame = ensureHostShowing(host)

      smokePanel.reload()

      waitForJavaScriptResult(
        webView = smokePanel.webView,
        script = "window.__WEBVIEW_SMOKE_EXECUTED__ === true ? 'ok' : 'pending'",
        expected = "ok",
        description = "Smoke page script did not execute",
      )
      waitForJavaScriptResult(
        webView = smokePanel.webView,
        script = "(function() { const root = document.getElementById('smoke-root'); return root ? root.textContent : 'missing'; })()",
        expected = "webview smoke ready",
        description = "Smoke page DOM marker did not update",
      )
    }
    finally {
      runCatching { panel?.close() }
      runCatching { disposeFrame(fallbackFrame) }
      runCatching { unregisterSmokeToolWindow(toolWindowManager) }
      scope.cancel()
    }
  }

  private suspend fun createPanelOrSkip(scope: CoroutineScope): WebViewPanel {
    val assetRoot = WebViewAssetRoot.forView("smoke")
    return runCatching {
      withContext(Dispatchers.EDT) {
        createWebViewPanel(
          scope = scope,
          options = WebViewPanelOptions(
            assetRoot = assetRoot,
            debugName = "WebViewInIdeSmokeTest",
          ),
        )
      }
    }
      .getOrElse { t ->
        assumeTrue(false, smokeFailureMessage("No WebView engine is available", lastError = t))
        throw t
      }
  }

  private suspend fun registerSmokeToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    return withContext(Dispatchers.EDT) {
      check(toolWindowManager.getToolWindow(TOOL_WINDOW_ID) == null) { "Tool window is already registered: $TOOL_WINDOW_ID" }
      toolWindowManager.registerToolWindow(
        RegisterToolWindowTask(
          id = TOOL_WINDOW_ID,
          anchor = ToolWindowAnchor.RIGHT,
          canCloseContent = false,
          canWorkInDumbMode = true,
          shouldBeAvailable = true,
        )
      )
    }
  }

  private suspend fun addSmokeContent(toolWindow: ToolWindow, host: JComponent) {
    withContext(Dispatchers.EDT) {
      val contentManager = toolWindow.contentManager
      val content = contentManager.factory.createContent(host, "Smoke", false)
      contentManager.addContent(content)
      contentManager.setSelectedContent(content)
    }
  }

  private suspend fun activateToolWindow(toolWindow: ToolWindow) {
    val activated = CompletableDeferred<Unit>()
    withContext(Dispatchers.EDT) {
      toolWindow.show()
      toolWindow.activate(Runnable { activated.complete(Unit) }, true, true)
    }
    withTimeout(5.seconds) {
      activated.await()
    }
  }

  private suspend fun ensureHostShowing(host: Component): JFrame? {
    if (waitUntilShowing(host, 2.seconds)) return null

    val frame = withContext(Dispatchers.EDT) {
      JFrame(TOOL_WINDOW_ID).apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        contentPane.layout = BorderLayout()
        contentPane.add(host, BorderLayout.CENTER)
        size = Dimension(480, 320)
        isVisible = true
      }
    }
    assertTrue(waitUntilShowing(host, 5.seconds), smokeFailureMessage("WebView host component did not become showing"))
    return frame
  }

  private suspend fun waitForJavaScriptResult(
    webView: WebView,
    @Language("JavaScript") script: String,
    expected: String,
    description: String,
  ): String {
    var lastResult: String? = null
    var lastError: Throwable? = null
    val matched = withTimeoutOrNull(SMOKE_TIMEOUT) {
      while (true) {
        runCatching { webView.evaluateJavaScript(script).value }
          .onSuccess { result ->
            lastError = null
            lastResult = result
            if (result == expected) return@withTimeoutOrNull true
          }
          .onFailure { t ->
            lastError = t
            lastResult = null
          }
        delay(100.milliseconds)
      }
    } == true

    assertTrue(matched, smokeFailureMessage(description, lastResult, lastError))
    return lastResult ?: ""
  }

  private suspend fun disposeFrame(frame: JFrame?) {
    if (frame == null) return
    withContext(Dispatchers.EDT) { frame.dispose() }
  }

  @Suppress("DEPRECATION")
  private suspend fun unregisterSmokeToolWindow(toolWindowManager: ToolWindowManager) {
    withContext(Dispatchers.EDT) {
      toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID)
    }
  }

  private suspend fun waitUntilShowing(component: Component, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (readOnEdt { component.isShowing }) return@withTimeoutOrNull true
        delay(100.milliseconds)
      }
    } == true
  }

  private suspend fun <T> readOnEdt(action: () -> T): T {
    return withContext(Dispatchers.EDT) { action() }
  }

  private fun smokeFailureMessage(reason: String, lastResult: String? = null, lastError: Throwable? = null): String {
    return buildString {
      append(reason)
      append(" (engine=")
      append(selectedEngineId())
      append(", os=")
      append(System.getProperty("os.name"))
      append(' ')
      append(System.getProperty("os.version"))
      append(", toolWindowId=")
      append(TOOL_WINDOW_ID)
      if (lastResult != null) {
        append(", lastJsResult=")
        append(lastResult)
      }
      if (lastError != null) {
        append(", lastJsError=")
        append(lastError::class.java.name)
        append(": ")
        append(lastError.message)
      }
      append(')')
    }
  }

  private fun selectedEngineId(): String {
    return runCatching {
      val registryValue = RegistryManager.getInstance().get(WEBVIEW_ENGINE_REGISTRY_KEY)
      (registryValue.selectedOption ?: registryValue.asString()).ifBlank { "AUTO" }
    }.getOrDefault("AUTO")
  }

  private companion object {
    private const val TOOL_WINDOW_ID = "WebView Smoke Test"
    private const val WEBVIEW_ENGINE_REGISTRY_KEY = "ide.webview.engine"
    private val SMOKE_TIMEOUT = 20.seconds

    @JvmStatic
    @BeforeAll
    fun ensureJnaForMacWebView() {
      if (SystemInfo.isMac && !JnaLoader.isLoaded()) {
        JnaLoader.load(Logger.getInstance(WebViewInIdeSmokeTest::class.java))
      }
    }
  }
}
