// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.engine.WebView
import com.intellij.ui.webview.impl.engine.WebViewEngineAvailability
import com.intellij.ui.webview.impl.engine.WebViewEngineCapabilities
import com.intellij.ui.webview.impl.engine.WebViewEngineCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineId
import com.intellij.ui.webview.impl.engine.WebViewEngineKind
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import com.intellij.ui.webview.impl.engine.WebViewRuntime
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacNativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacWebViewEngine
import com.intellij.ui.webview.impl.mac.MacWebViewFirstResponderState
import com.intellij.ui.webview.impl.mac.MacWkWebViewEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@EnabledOnOs(OS.MAC)
internal class MacWebViewHotkeysInIdeTest {
  @TempDir
  private lateinit var tempDir: Path

  @Test
  @EnabledIf("isUiEnvironmentAvailable", disabledReason = "AWT Robot requires a non-headless graphics environment")
  fun copyShortcutInsideWebViewUsesNativeSelection(): Unit = runBlocking {
    val robot = createRobotOrSkip()
    writeCopyPage(tempDir)

    val runtime = WebViewRuntime.getInstance()
    val savedProviders = runtime.providers
    val provider = CapturingMacProvider()
    @Suppress("RAW_SCOPE_CREATION") // The test owns a short-lived WebView scope.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var panel: WebViewPanel? = null
    var frame: JFrame? = null
    var webViewHost: SwingWebViewHostPanel? = null
    var lastClipboard: String? = null

    try {
      runtime.providers = listOf(provider)
      panel = createPanelOrSkip(scope)
      val field = JTextField().apply {
        preferredSize = Dimension(1, 32)
        caret = DefaultCaret().apply { blinkRate = 0 }
      }
      frame = createFrame(panel.component, field)
      val host = findWebViewHost(panel.component)
      webViewHost = host

      assertTrue(waitUntilShowing(host, 5.seconds), failureMessage("WebView host component did not become showing"))
      waitForJavaScriptResult(
        webView = panel.webView,
        script = PAGE_READY_SCRIPT,
        expected = "true",
        description = "Copy test page did not load WebView bridge, platform features, and selectable text",
      )

      assumeRobotCanTypeIntoSwing(robot, frame, field)
      clickCenter(robot, host)
      assertTrue(waitForHostFocus(host, 5.seconds), failureMessage("WebView host did not become the Swing shortcut focus owner"))
      assertTrue(waitForHostWebViewActive(host, 5.seconds), failureMessage("WebView host did not mark WebView focus as inside host"))
      val lastFirstResponder = waitForFirstResponderInsideWebView(provider.engine)

      val lastSelection = waitForJavaScriptResult(
        webView = panel.webView,
        script = SELECT_COPY_TEXT_SCRIPT,
        expected = COPY_TEXT,
        description = "WebView text selection was not established before pressing Cmd+C",
      )

      val clipboard = Toolkit.getDefaultToolkit().systemClipboard
      clipboard.setContents(StringSelection(CLIPBOARD_SENTINEL), null)
      pressCommandC(robot)
      val copied = waitForClipboardText(clipboardText = {
        readClipboardText().also { lastClipboard = it }
      })

      assertEquals(
        COPY_TEXT,
        copied,
        failureMessage(
          reason = "Cmd+C inside the macOS WebView did not copy the native WebView selection",
          lastSelection = lastSelection,
          lastFirstResponder = lastFirstResponder,
          lastClipboard = lastClipboard,
        ),
      )
    }
    finally {
      runCatching { webViewHost?.let { clearWebViewFocus(it) } }
      runCatching { panel?.close() }
      runCatching { disposeFrame(frame) }
      runtime.providers = savedProviders
      scope.cancel()
    }
  }

  private suspend fun createPanelOrSkip(scope: CoroutineScope): WebViewPanel {
    return runCatching {
      withContext(Dispatchers.EDT) {
        createWebViewPanel(
          scope = scope,
          options = WebViewPanelOptions(
            assetRoot = WebViewAssetRoot.fromDirectory(tempDir),
            debugName = "MacWebViewHotkeysInIdeTest",
          ),
        )
      }
    }.getOrElse { error ->
      assumeTrue(false, failureMessage("No macOS WebView engine is available", lastError = error))
      throw error
    }
  }

  private suspend fun createFrame(component: Component, field: JTextField): JFrame {
    return withContext(Dispatchers.EDT) {
      JFrame("Mac WebView Hotkeys In IDE Test").apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        contentPane.layout = BorderLayout()
        contentPane.add(component, BorderLayout.CENTER)
        contentPane.add(field, BorderLayout.SOUTH)
        size = Dimension(640, 420)
        setLocation(80, 80)
        isVisible = true
        requestForeground(this)
        toFront()
      }
    }
  }

  private suspend fun assumeRobotCanTypeIntoSwing(robot: Robot, frame: JFrame, field: JTextField) {
    withContext(Dispatchers.EDT) {
      requestForeground(frame)
      frame.toFront()
      field.text = ""
    }
    waitForFrameActive(frame, 1.seconds)
    clickCenter(robot, field)
    withContext(Dispatchers.EDT) {
      if (!field.requestFocusInWindow()) {
        field.requestFocus()
      }
    }
    assumeTrue(waitForFieldFocus(field, 2.seconds), "AWT Robot could not focus the Swing preflight field")

    robot.keyPress(KeyEvent.VK_1)
    robot.keyRelease(KeyEvent.VK_1)
    robot.waitForIdle()
    assumeTrue(
      waitForFieldText(field, "1", 2.seconds),
      "AWT Robot key input is not delivered to the focused Swing field; ${focusDiagnostics(frame, field)}",
    )
  }

  private suspend fun waitForFirstResponderInsideWebView(engine: MacWebViewEngine): MacWebViewFirstResponderState? {
    var lastState: MacWebViewFirstResponderState? = null
    val matched = withTimeoutOrNull(5.seconds) {
      while (true) {
        lastState = engine.firstResponderState()
        if (lastState?.isInsideWebView == true) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
    assertTrue(matched, failureMessage("macOS first responder did not move inside WKWebView", lastFirstResponder = lastState))
    return lastState
  }

  private suspend fun waitForJavaScriptResult(
    webView: WebView,
    @Language("JavaScript") script: String,
    expected: String,
    description: String,
  ): String? {
    var lastResult: String? = null
    var lastError: Throwable? = null
    val matched = withTimeoutOrNull(5.seconds) {
      while (true) {
        runCatching { webView.evaluateJavaScript(script).value }
          .onSuccess { result ->
            lastError = null
            lastResult = result
            if (result == expected) return@withTimeoutOrNull true
          }
          .onFailure { error ->
            lastError = error
            lastResult = null
          }
        delay(100.milliseconds)
      }
    } == true
    assertTrue(matched, failureMessage(description, lastSelection = lastResult, lastError = lastError))
    return lastResult
  }

  private suspend fun waitForClipboardText(clipboardText: () -> String?): String? {
    var lastText: String? = null
    withTimeoutOrNull(5.seconds) {
      while (true) {
        lastText = clipboardText()
        if (lastText == COPY_TEXT) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    }
    return lastText
  }

  private fun readClipboardText(): String? {
    return runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
  }

  private suspend fun waitUntilShowing(component: Component, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { component.isShowing && component.width > 0 && component.height > 0 }) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun waitForHostFocus(host: SwingWebViewHostPanel, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { isFocusOwner(host) }) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun waitForHostWebViewActive(host: SwingWebViewHostPanel, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { FOCUS_INSIDE_HOST_FIELD.getBoolean(host) }) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun waitForFrameActive(frame: JFrame, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { frame.isActive && frame.isFocused }) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun waitForFieldFocus(field: JTextField, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { isFocusOwner(field) }) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun waitForFieldText(field: JTextField, expected: String, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (withContext(Dispatchers.EDT) { field.text } == expected) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private fun isFocusOwner(component: Component): Boolean {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return component.isFocusOwner || focusManager.focusOwner === component || focusManager.permanentFocusOwner === component
  }

  private fun clickCenter(robot: Robot, component: Component) {
    val point = centerOnScreen(component)
    robot.mouseMove(point.x, point.y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    robot.waitForIdle()
  }

  private fun centerOnScreen(component: Component): Point {
    val result = arrayOfNulls<Point>(1)
    SwingUtilities.invokeAndWait {
      val location = component.locationOnScreen
      result[0] = Point(location.x + component.width / 2, location.y + component.height / 2)
    }
    return result[0]!!
  }

  private fun pressCommandC(robot: Robot) {
    robot.keyPress(KeyEvent.VK_META)
    robot.keyPress(KeyEvent.VK_C)
    robot.keyRelease(KeyEvent.VK_C)
    robot.keyRelease(KeyEvent.VK_META)
    robot.waitForIdle()
  }

  private fun findWebViewHost(component: Component): SwingWebViewHostPanel {
    if (component is SwingWebViewHostPanel) return component
    if (component is Container) {
      component.components.forEach { child ->
        runCatching { findWebViewHost(child) }.getOrNull()?.let { return it }
      }
    }
    error("SwingWebViewHostPanel was not found under ${component.javaClass.name}")
  }

  private suspend fun clearWebViewFocus(host: SwingWebViewHostPanel) {
    withContext(Dispatchers.EDT) {
      host.clearWebViewFocus()
    }
  }

  private suspend fun disposeFrame(frame: JFrame?) {
    if (frame == null) return
    withContext(Dispatchers.EDT) {
      (frame.contentPane.getComponent(1) as? JTextField)?.let { field ->
        field.caret.blinkRate = 0
        field.caret.deinstall(field)
      }
      frame.dispose()
    }
  }

  private fun createRobotOrSkip(): Robot {
    assumeTrue(!GraphicsEnvironment.isHeadless(), "AWT Robot requires a non-headless graphics environment")
    return runCatching {
      Robot().apply {
        autoDelay = 20
        isAutoWaitForIdle = true
      }
    }.getOrElse { error ->
      assumeTrue(false, "AWT Robot is unavailable: ${error.message}")
      throw error
    }
  }

  private fun requestForeground(frame: JFrame) {
    runCatching {
      Desktop.getDesktop().requestForeground(true)
    }
    frame.isAlwaysOnTop = true
    frame.toFront()
    frame.isAlwaysOnTop = false
  }

  private fun focusDiagnostics(frame: JFrame, field: JTextField): String {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return "frameShowing=${frame.isShowing}, frameActive=${frame.isActive}, frameFocused=${frame.isFocused}, " +
           "fieldShowing=${field.isShowing}, fieldFocusOwner=${field.isFocusOwner}, " +
           "focusOwner=${focusManager.focusOwner?.javaClass?.name}, permanentFocusOwner=${focusManager.permanentFocusOwner?.javaClass?.name}"
  }

  private fun failureMessage(
    reason: String,
    lastSelection: String? = null,
    lastFirstResponder: MacWebViewFirstResponderState? = null,
    lastClipboard: String? = null,
    lastError: Throwable? = null,
  ): String {
    return buildString {
      append(reason)
      append(" (engine=")
      append(WebViewEngineId.SYSTEM_MACOS)
      append(", os=")
      append(System.getProperty("os.name"))
      append(' ')
      append(System.getProperty("os.version"))
      if (lastSelection != null) {
        append(", lastSelection=")
        append(lastSelection)
      }
      if (lastFirstResponder != null) {
        append(", lastFirstResponder=")
        append(lastFirstResponder)
      }
      if (lastClipboard != null) {
        append(", lastClipboard=")
        append(lastClipboard)
      }
      if (lastError != null) {
        append(", lastError=")
        append(lastError::class.java.name)
        append(": ")
        append(lastError.message)
      }
      append(')')
    }
  }

  private fun writeCopyPage(root: Path) {
    Files.writeString(root.resolve("index.html"), COPY_PAGE_HTML)
  }

  private class CapturingMacProvider : WebViewEngineProvider {
    private val delegate = MacWkWebViewEngineProvider()
    lateinit var engine: MacWebViewEngine
      private set

    override val id: WebViewEngineId = delegate.id
    override val displayName: String = delegate.displayName
    override val capabilities: WebViewEngineCapabilities = delegate.capabilities

    override fun selectionPriority(preference: WebViewEngineKind): Int? = delegate.selectionPriority(preference)

    override fun availabilityBlocking(): WebViewEngineAvailability = delegate.availabilityBlocking()

    override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
      return (delegate.createEngine(scope, options) as MacWebViewEngine).also { engine = it }
    }

    override fun createNativeHostPeer(scope: CoroutineScope, engine: WebViewEngineBridge): NativeWebViewHostPeer {
      return MacNativeWebViewHostPeer(scope, engine as MacWebViewEngine)
    }
  }

  private companion object {
    private const val COPY_TEXT = "COPYME"
    private const val CLIPBOARD_SENTINEL = "__webview_clipboard_sentinel__"
    private val FOCUS_INSIDE_HOST_FIELD = SwingWebViewHostPanel::class.java.getDeclaredField("focusInsideHost").apply { isAccessible = true }

    @Language("JavaScript")
    private val PAGE_READY_SCRIPT = """
      Boolean(
        window.__WVI__ &&
        window['__wviFocusInteropReady'] &&
        document.getElementById('copy-target') &&
        document.getElementById('copy-target').textContent === 'COPYME'
      )
    """.trimIndent()

    @Suppress("HtmlUnknownTarget")
    @Language("HTML")
    private val COPY_PAGE_HTML = """
      <!doctype html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          html, body {
            margin: 0;
            width: 100vw;
            height: 100vh;
          }

          body {
            display: grid;
            place-items: center;
            font: 48px/1.2 sans-serif;
            -webkit-user-select: text;
            user-select: text;
          }
        </style>
        <script src="/__webview/wvi-bridge.js"></script>
        <script src="/__webview/wvi-platform-features.js"></script>
        <script>window.__wviFocusInteropReady = true;</script>
      </head>
      <body>
        <main id="copy-target">COPYME</main>
      </body>
      </html>
    """.trimIndent()

    @Language("JavaScript")
    private val SELECT_COPY_TEXT_SCRIPT = """
      (function() {
        const target = document.getElementById('copy-target');
        const range = document.createRange();
        range.selectNodeContents(target);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        return selection.toString();
      })()
    """.trimIndent()

    @JvmStatic
    fun isUiEnvironmentAvailable(): Boolean = !GraphicsEnvironment.isHeadless()

    @JvmStatic
    @BeforeAll
    fun ensureJna() {
      if (!JnaLoader.isLoaded()) {
        JnaLoader.load(Logger.getInstance(MacWebViewHotkeysInIdeTest::class.java))
      }
    }
  }
}
