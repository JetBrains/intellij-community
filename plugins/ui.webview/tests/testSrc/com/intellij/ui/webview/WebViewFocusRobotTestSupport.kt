// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.engine.createWebViewFocusEntrySink
import com.intellij.ui.webview.impl.engine.registerWebViewFocusExitHandler
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.rpc.WebViewMessageBusImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Robot
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal object WebViewFocusRobotTestSupport {
  suspend fun runFocusInteropScenario(
    frame: JFrame,
    scope: CoroutineScope,
    engine: WebViewEngineBridge,
    nativeHostPeer: NativeWebViewHostPeer,
    tempDir: Path,
  ) {
    val robot = createRobotOrSkip()
    val bus = WebViewMessageBusImpl(scope, engine)
    val inputEvents = Collections.synchronizedList(mutableListOf<String>())
    val field = JTextField().apply {
      preferredSize = Dimension(1, 32)
      // AquaCaret starts a Swing timer which the test framework reports as a leak after the test.
      caret = DefaultCaret().apply { blinkRate = 0 }
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          inputEvents.add("focusGained")
        }

        override fun focusLost(e: FocusEvent) {
          inputEvents.add("focusLost")
        }
      })
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          inputEvents.add("keyPressed:${e.keyCode}")
        }

        override fun keyTyped(e: KeyEvent) {
          inputEvents.add("keyTyped:${e.keyChar.code}")
        }

        override fun keyReleased(e: KeyEvent) {
          inputEvents.add("keyReleased:${e.keyCode}")
        }
      })
    }
    val host = SwingWebViewHostPanel(scope, engine, bus.interop.createWebViewFocusEntrySink(), nativeHostPeer)
    val focusRegistration = bus.interop.registerWebViewFocusExitHandler(host)
    engine.connectMessageBus { rawJson -> bus.transferFromJs(rawJson) }
    writeFocusInteropPage(tempDir)

    try {
      SwingUtilities.invokeAndWait {
        frame.contentPane.removeAll()
        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(field, BorderLayout.SOUTH)
        frame.contentPane.add(host, BorderLayout.CENTER)
        frame.toFront()
        frame.revalidate()
        frame.repaint()
      }
      assertTrue(waitUntilShowing(host, 5.seconds), "WebView host component did not become showing")
      assertTrue(waitUntilShowing(field, 5.seconds), "Swing text field did not become showing")

      engine.loadAsset(WebViewAssetRoot.fromDirectory(tempDir), WebViewAssetPath.indexHtml())
      waitForJavaScriptResult(
        webView = engine,
        script = "Boolean(window.__WVI__ && window['__wviFocusInteropReady'])",
        expected = "true",
        description = "Focus interop test page did not load WebView bridge and platform features",
      )

      focusSwingFieldWithRobot(robot, frame, field, "Swing field did not receive initial focus", skipIfUnavailable = true)
      clearText(field)
      clearWebInput(engine)
      typeKey(robot, KeyEvent.VK_1)
      // macOS may deny Robot keyboard injection to non-foreground test runners. Once this preflight passes, later focus failures are real.
      assumeTrue(
        waitForFieldText(field, "1", inputEvents, assertOnFailure = false),
        "AWT Robot key input is not delivered to the focused Swing field; ${buildCurrentFocusDiagnostics(frame, field)}; inputEvents=$inputEvents",
      )
      assertWebInputValue(engine, "", "WebView input received typed input while Swing field was focused")

      clickCenter(robot, host)
      waitForFocusOwnerNot(field, "WebView activation did not clear the previous Swing focus owner")
      waitForJavaScriptResult(
        webView = engine,
        script = "document.activeElement && document.activeElement.id === 'web-input'",
        expected = "true",
        description = "WebView input did not become the active document element",
      )
      typeKey(robot, KeyEvent.VK_2)
      waitForWebInputValue(engine, "2", "WebView input did not receive typed input after activation")
      assertFieldTextRemainsOne(field, inputEvents, "Swing text field received typed input while WebView was focused")

      focusSwingFieldWithRobot(robot, frame, field, "Swing field did not regain focus after WebView activation", skipIfUnavailable = false)
      clearText(field)
      typeKey(robot, KeyEvent.VK_3)
      waitForFieldText(field, "3", inputEvents, assertOnFailure = true)
      assertWebInputValue(engine, "2", "WebView input received typed input after focus returned to Swing")
    }
    finally {
      focusRegistration.close()
      bus.close()
      SwingUtilities.invokeAndWait {
        field.caret.blinkRate = 0
        field.caret.deinstall(field)
        frame.contentPane.removeAll()
      }
    }
  }

  suspend fun runMacFirstResponderFocusTransferScenario(
    frame: JFrame,
    scope: CoroutineScope,
    engine: WebViewEngineBridge,
    nativeHostPeer: NativeWebViewHostPeer,
    tempDir: Path,
    assertNativeFocusReadyForSwingTyping: suspend () -> Unit,
  ) {
    val robot = createRobotOrSkip()
    val bus = WebViewMessageBusImpl(scope, engine)
    val inputEvents = Collections.synchronizedList(mutableListOf<String>())
    val field = JTextField().apply {
      preferredSize = Dimension(1, 32)
      caret = DefaultCaret().apply { blinkRate = 0 }
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          inputEvents.add("focusGained")
        }

        override fun focusLost(e: FocusEvent) {
          inputEvents.add("focusLost")
        }
      })
    }
    val host = SwingWebViewHostPanel(scope, engine, bus.interop.createWebViewFocusEntrySink(), nativeHostPeer)
    val focusRegistration = bus.interop.registerWebViewFocusExitHandler(host)
    engine.connectMessageBus { rawJson -> bus.transferFromJs(rawJson) }
    writeFocusInteropPage(tempDir)

    try {
      SwingUtilities.invokeAndWait {
        frame.contentPane.removeAll()
        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(field, BorderLayout.SOUTH)
        frame.contentPane.add(host, BorderLayout.CENTER)
        frame.toFront()
        frame.revalidate()
        frame.repaint()
      }
      assertTrue(waitUntilShowing(host, 5.seconds), "WebView host component did not become showing")
      assertTrue(waitUntilShowing(field, 5.seconds), "Swing text field did not become showing")

      engine.loadAsset(WebViewAssetRoot.fromDirectory(tempDir), WebViewAssetPath.indexHtml())
      waitForJavaScriptResult(
        webView = engine,
        script = "Boolean(window.__WVI__ && window['__wviFocusInteropReady'])",
        expected = "true",
        description = "Focus interop first responder test page did not load WebView bridge and platform features",
      )

      focusSwingFieldWithRobot(robot, frame, field, "Swing field did not receive initial focus", skipIfUnavailable = true)
      clearWebInput(engine)

      clickCenter(robot, host)
      waitForFocusOwnerNot(field, "WebView activation did not clear the previous Swing focus owner")
      waitForJavaScriptResult(
        webView = engine,
        script = "document.activeElement && document.activeElement.id === 'web-input'",
        expected = "true",
        description = "WebView input did not become the active document element after Robot click",
      )

      focusSwingFieldWithRobot(robot, frame, field, "Swing field did not regain focus after WebView activation", skipIfUnavailable = false)
      assertNativeFocusReadyForSwingTyping()
      assertWebInputValue(engine, "", "WebView input changed during macOS focus transfer scenario")
    }
    finally {
      focusRegistration.close()
      bus.close()
      SwingUtilities.invokeAndWait {
        field.caret.blinkRate = 0
        field.caret.deinstall(field)
        frame.contentPane.removeAll()
      }
    }
  }

  suspend fun runNonTabbableSelectionScenario(
    frame: JFrame,
    scope: CoroutineScope,
    engine: WebViewEngineBridge,
    nativeHostPeer: NativeWebViewHostPeer,
    tempDir: Path,
  ) {
    val robot = createRobotOrSkip()
    val bus = WebViewMessageBusImpl(scope, engine)
    val inputEvents = Collections.synchronizedList(mutableListOf<String>())
    val field = JTextField().apply {
      preferredSize = Dimension(1, 32)
      caret = DefaultCaret().apply { blinkRate = 0 }
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          inputEvents.add("focusGained")
        }

        override fun focusLost(e: FocusEvent) {
          inputEvents.add("focusLost")
        }
      })
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          inputEvents.add("keyPressed:${e.keyCode}")
        }

        override fun keyTyped(e: KeyEvent) {
          inputEvents.add("keyTyped:${e.keyChar.code}")
        }

        override fun keyReleased(e: KeyEvent) {
          inputEvents.add("keyReleased:${e.keyCode}")
        }
      })
    }
    val host = SwingWebViewHostPanel(scope, engine, bus.interop.createWebViewFocusEntrySink(), nativeHostPeer)
    val focusRegistration = bus.interop.registerWebViewFocusExitHandler(host)
    engine.connectMessageBus { rawJson -> bus.transferFromJs(rawJson) }
    writeNonTabbableSelectionPage(tempDir)

    try {
      SwingUtilities.invokeAndWait {
        frame.contentPane.removeAll()
        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(field, BorderLayout.SOUTH)
        frame.contentPane.add(host, BorderLayout.CENTER)
        frame.toFront()
        frame.revalidate()
        frame.repaint()
      }
      assertTrue(waitUntilShowing(host, 5.seconds), "WebView host component did not become showing")
      assertTrue(waitUntilShowing(field, 5.seconds), "Swing text field did not become showing")

      engine.loadAsset(WebViewAssetRoot.fromDirectory(tempDir), WebViewAssetPath.indexHtml())
      waitForJavaScriptResult(
        webView = engine,
        script = "Boolean(window.__WVI__ && window['__wviFocusInteropReady'])",
        expected = "true",
        description = "Non-tabbable selection test page did not load WebView bridge and platform features",
      )

      focusSwingFieldWithRobot(robot, frame, field, "Swing field did not receive initial focus", skipIfUnavailable = true)
      clearText(field)
      typeKey(robot, KeyEvent.VK_1)
      assumeTrue(
        waitForFieldText(field, "1", inputEvents, assertOnFailure = false),
        "AWT Robot key input is not delivered to the focused Swing field; ${buildCurrentFocusDiagnostics(frame, field)}; inputEvents=$inputEvents",
      )

      inputEvents.clear()
      dragSelection(robot, host)
      waitForFocusOwnerNot(field, "WebView text selection did not clear the previous Swing focus owner")
      waitForJavaScriptResult(
        webView = engine,
        script = "Boolean(window.getSelection()?.toString())",
        expected = "true",
        description = "WebView text was not selected by Robot drag",
      )
      assertFocusOwnerStaysAwayFrom(field, 1.seconds, inputEvents, "WebView text selection bounced focus back to the Swing editor")

      typeKey(robot, KeyEvent.VK_2)
      assertFieldTextRemainsOne(field, inputEvents, "Swing editor received typed input while non-tabbable WebView preview was focused")
    }
    finally {
      focusRegistration.close()
      bus.close()
      SwingUtilities.invokeAndWait {
        field.caret.blinkRate = 0
        field.caret.deinstall(field)
        frame.contentPane.removeAll()
      }
    }
  }

  private fun createRobotOrSkip(): Robot {
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

  private fun writeFocusInteropPage(root: Path) {
    Files.writeString(root.resolve("index.html"), focusInteropHtml())
  }

  private fun writeNonTabbableSelectionPage(root: Path) {
    Files.writeString(root.resolve("index.html"), nonTabbableSelectionHtml())
  }

  private fun focusInteropHtml(): String = """
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
          display: flex;
          align-items: center;
          justify-content: center;
        }

        input {
          width: 180px;
          height: 32px;
        }
      </style>
      <script src="/__webview/wvi-bridge.js"></script>
      <script src="/__webview/wvi-platform-features.js"></script>
      <script>window.__wviFocusInteropReady = true;</script>
    </head>
    <body>
      <input id="web-input">
    </body>
    </html>
  """.trimIndent()

  private fun nonTabbableSelectionHtml(): String = """
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
          box-sizing: border-box;
          padding: 48px;
          display: grid;
          place-items: center;
          font: 16px/1.5 sans-serif;
          -webkit-user-select: text;
          user-select: text;
        }

        main {
          width: 520px;
          max-width: 520px;
        }
      </style>
      <script src="/__webview/wvi-bridge.js"></script>
      <script src="/__webview/wvi-platform-features.js"></script>
      <script>window.__wviFocusInteropReady = true;</script>
    </head>
    <body>
      <main id="content">
        Markdown preview selectable text without any focusable controls. Drag across this sentence to select text.
      </main>
    </body>
    </html>
  """.trimIndent()

  private suspend fun waitForJavaScriptResult(
    webView: WebViewEngineBridge,
    @Language("JavaScript") script: String,
    expected: String,
    description: String,
  ) {
    var lastResult: String? = null
    val matched = withTimeoutOrNull(5.seconds) {
      while (true) {
        lastResult = webView.evaluateJavaScript(script)
        if (lastResult == expected) return@withTimeoutOrNull true
        delay(100.milliseconds)
      }
    } == true
    assertTrue(matched, "$description, lastJsResult=$lastResult")
  }

  private suspend fun clearWebInput(webView: WebViewEngineBridge) {
    waitForJavaScriptResult(
      webView = webView,
      script = "const input = document.getElementById('web-input'); if (input) { input.value = ''; true } else false",
      expected = "true",
      description = "WebView input was not available for clearing",
    )
  }

  private suspend fun waitForWebInputValue(webView: WebViewEngineBridge, expected: String, description: String) {
    waitForJavaScriptResult(
      webView = webView,
      script = "document.getElementById('web-input')?.value === '${expected}'",
      expected = "true",
      description = description,
    )
  }

  private suspend fun assertWebInputValue(webView: WebViewEngineBridge, expected: String, description: String) {
    val actual = webView.evaluateJavaScript("document.getElementById('web-input')?.value === '${expected}'")
    assertEquals("true", actual, "$description, expected=$expected, jsResult=$actual")
  }

  private suspend fun waitUntilShowing(component: Component, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (component.isShowing) return@withTimeoutOrNull true
        delay(100.milliseconds)
      }
    } == true
  }

  private suspend fun waitForFrameActive(frame: JFrame, timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
      while (true) {
        if (frame.isActive && frame.isFocused) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
  }

  private suspend fun focusSwingFieldWithRobot(robot: Robot, frame: JFrame, field: JTextField, description: String, skipIfUnavailable: Boolean) {
    var lastFocusDiagnostics = ""
    var lastRequestFocusInWindowResult = false
    val deadlineNanos = timeoutDeadlineNanos(5.seconds)
    var matched = false
    while (true) {
      SwingUtilities.invokeAndWait {
        requestForeground(frame)
        frame.toFront()
      }
      waitForFrameActive(frame, 1.seconds)
      clickCenter(robot, field)
      SwingUtilities.invokeAndWait {
        lastRequestFocusInWindowResult = field.requestFocusInWindow()
        if (!lastRequestFocusInWindowResult) {
          field.requestFocus()
        }
      }
      if (waitForFieldFocus(field, 500.milliseconds) { focusOwner, permanentFocusOwner ->
          lastFocusDiagnostics = buildFocusDiagnostics(frame, field, focusOwner, permanentFocusOwner, lastRequestFocusInWindowResult)
        }) {
        matched = true
        break
      }
      if (System.nanoTime() >= deadlineNanos) {
        break
      }
      delay(100.milliseconds)
    }
    if (skipIfUnavailable) {
      assumeTrue(matched, "$description; Robot-driven focus is unavailable in this environment; $lastFocusDiagnostics")
    }
    else {
      assertTrue(matched, "$description; $lastFocusDiagnostics")
    }
  }

  private suspend fun waitForFocusOwnerNot(component: Component, description: String) {
    val matched = withTimeoutOrNull(5.seconds) {
      while (true) {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner
        if (focusOwner !== component) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
    assertTrue(matched, description)
  }

  private suspend fun assertFocusOwnerStaysAwayFrom(component: Component, duration: Duration, inputEvents: List<String>, description: String) {
    val deadlineNanos = timeoutDeadlineNanos(duration)
    while (System.nanoTime() < deadlineNanos) {
      val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner
      assertTrue(focusOwner !== component, "$description; focusOwner=${focusOwner?.javaClass?.name}; inputEvents=$inputEvents")
      assertTrue("focusGained" !in inputEvents, "$description; inputEvents=$inputEvents")
      delay(50.milliseconds)
    }
  }

  private suspend fun waitForFieldFocus(
    field: JTextField,
    timeout: Duration,
    updateDiagnostics: (focusOwner: Component?, permanentFocusOwner: Component?) -> Unit,
  ): Boolean {
    val deadlineNanos = timeoutDeadlineNanos(timeout)
    while (true) {
      val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      val permanentFocusOwner = focusManager.permanentFocusOwner
      val focusOwner = focusManager.focusOwner
      val fieldFocusOwnerBeforeDiagnostics = isFieldFocusOwner(field)
      updateDiagnostics(focusOwner, permanentFocusOwner)
      if (fieldFocusOwnerBeforeDiagnostics || isFieldFocusOwner(field) || permanentFocusOwner === field || focusOwner === field) return true
      if (System.nanoTime() >= deadlineNanos) return false
      delay(20.milliseconds)
    }
  }

  private fun timeoutDeadlineNanos(timeout: Duration): Long = System.nanoTime() + timeout.inWholeNanoseconds

  private fun isFieldFocusOwner(field: JTextField): Boolean {
    var result = false
    SwingUtilities.invokeAndWait {
      result = field.isFocusOwner
    }
    return result
  }

  private fun clickCenter(robot: Robot, component: Component) {
    val point = arrayOfNulls<Point>(1)
    SwingUtilities.invokeAndWait {
      val location = component.locationOnScreen
      point[0] = Point(location.x + component.width / 2, location.y + component.height / 2)
    }
    val center = point[0]!!
    robot.mouseMove(center.x, center.y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    robot.waitForIdle()
  }

  private fun dragSelection(robot: Robot, component: Component) {
    val points = arrayOfNulls<Point>(2)
    SwingUtilities.invokeAndWait {
      val location = component.locationOnScreen
      val y = location.y + component.height / 2
      points[0] = Point(location.x + component.width / 4, y)
      points[1] = Point(location.x + component.width * 3 / 4, y)
    }
    val start = points[0]!!
    val end = points[1]!!
    robot.mouseMove(start.x, start.y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.delay(100)
    repeat(12) { step ->
      val ratio = (step + 1).toDouble() / 12.0
      val x = start.x + ((end.x - start.x) * ratio).toInt()
      val y = start.y + ((end.y - start.y) * ratio).toInt()
      robot.mouseMove(x, y)
      robot.delay(20)
    }
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    robot.waitForIdle()
  }

  private fun requestForeground(frame: JFrame) {
    runCatching {
      Desktop.getDesktop().requestForeground(true)
    }
    frame.isAlwaysOnTop = true
    frame.toFront()
    frame.isAlwaysOnTop = false
  }

  private fun buildFocusDiagnostics(
    frame: JFrame,
    field: JTextField,
    focusOwner: Component?,
    permanentFocusOwner: Component?,
    requestFocusInWindowResult: Boolean,
  ): String {
    return "frameShowing=${frame.isShowing}, frameActive=${frame.isActive}, frameFocused=${frame.isFocused}, " +
           "frameLocation=${locationOnScreenOrUnavailable(frame)}, fieldShowing=${field.isShowing}, fieldBounds=${field.bounds}, " +
           "fieldFocusable=${field.isFocusable}, fieldFocusOwner=${field.isFocusOwner}, " +
           "requestFocusInWindow=$requestFocusInWindowResult, focusOwner=${focusOwner?.javaClass?.name}, " +
           "permanentFocusOwner=${permanentFocusOwner?.javaClass?.name}"
  }

  private fun locationOnScreenOrUnavailable(component: Component): String {
    return runCatching { component.locationOnScreen.toString() }
      .getOrElse { "<unavailable: ${it.javaClass.simpleName}: ${it.message}>" }
  }

  private fun buildCurrentFocusDiagnostics(frame: JFrame, field: JTextField): String {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return buildFocusDiagnostics(frame, field, focusManager.focusOwner, focusManager.permanentFocusOwner, requestFocusInWindowResult = false)
  }

  private fun typeKey(robot: Robot, keyCode: Int) {
    robot.keyPress(keyCode)
    robot.keyRelease(keyCode)
    robot.waitForIdle()
  }

  private fun clearText(field: JTextField) {
    SwingUtilities.invokeAndWait {
      field.text = ""
    }
  }

  private suspend fun waitForFieldText(
    field: JTextField,
    expected: String,
    inputEvents: List<String>,
    assertOnFailure: Boolean,
  ): Boolean {
    var lastText = ""
    val matched = withTimeoutOrNull(5.seconds) {
      while (true) {
        SwingUtilities.invokeAndWait {
          lastText = field.text
        }
        if (lastText == expected) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
    if (assertOnFailure) {
      assertTrue(matched, "Swing text field did not receive typed input, expected=$expected, actual=$lastText, inputEvents=$inputEvents")
    }
    return matched
  }

  private fun assertFieldTextRemainsOne(field: JTextField, inputEvents: List<String>, description: String) {
    var actual = ""
    SwingUtilities.invokeAndWait {
      actual = field.text
    }
    assertEquals("1", actual, "$description, inputEvents=$inputEvents")
  }
}
