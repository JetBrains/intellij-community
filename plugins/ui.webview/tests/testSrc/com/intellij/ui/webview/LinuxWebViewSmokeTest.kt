// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewEngine
import com.intellij.ui.webview.api.WebViewEngineFactory
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.impl.NativeBridgeLibraryAvailability
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.linux.LinuxNativeWebViewHostPeer
import com.intellij.ui.webview.impl.linux.LinuxWaylandWindowUtil
import com.intellij.ui.webview.impl.linux.LinuxWebKitGtkBridge
import com.intellij.ui.webview.impl.linux.LinuxWebKitWebViewEngine
import com.intellij.ui.webview.impl.linux.linuxWebKitGtkBridgeLibrary
import com.intellij.ui.webview.impl.rpc.WebViewMessageBusImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@EnabledOnOs(OS.LINUX)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
@Suppress("JSUnresolvedVariable")
class LinuxWebViewSmokeTest {

  private var frame: JFrame? = null
  private var scope: CoroutineScope? = null

  companion object {
    private val longRunningThreadsDisposable = Disposer.newDisposable("LinuxWebViewSmokeTest long-running threads")

    @JvmStatic
    @BeforeAll
    fun setUpClass() {
      registerLongRunningThreads()
    }

    @JvmStatic
    @AfterAll
    fun tearDownClass() {
      if (nativeBridgeAvailable()) {
        LinuxWebKitGtkBridge.shutdownRuntimeForTests()
      }
      Disposer.dispose(longRunningThreadsDisposable)
    }

    private fun nativeBridgeAvailable(): Boolean {
      return linuxWebKitGtkBridgeLibrary.availability() is NativeBridgeLibraryAvailability.Available
    }

    private fun registerLongRunningThreads() {
      val trackerClass = Class.forName("com.intellij.testFramework.common.ThreadLeakTracker")
      val method = trackerClass.getMethod("longRunningThreadCreated", Disposable::class.java, Array<String>::class.java)
      val threadNamePrefixes = arrayOf("AWT-Wayland", "WLKeyboard.KeyRepeatManager")
      method.invoke(null, longRunningThreadsDisposable, threadNamePrefixes)
    }
  }

  @BeforeEach
  fun setUp() {
    assumeTrue(
      LinuxWaylandWindowUtil.isSupportedToolkit(),
      "Linux WebKitGTK WebView smoke tests require WLToolkit/Wayland",
    )
    assumeTrue(nativeBridgeAvailable(), "LinuxWebKitGtkBridge is not built; run cargo build in community/platform/ui.webview/native/LinuxWebKitGtkBridge")

    @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without IJ platform
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    SwingUtilities.invokeAndWait {
      frame = JFrame("Linux WebKitGTK Smoke Test").apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        size = Dimension(400, 300)
        isVisible = true
      }
    }
  }

  @AfterEach
  fun tearDown() {
    scope?.cancel()
    SwingUtilities.invokeAndWait { frame?.dispose() }
    frame = null
    scope = null
  }

  @Test
  fun evaluateJavaScript_returnsResult(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    try {
      attach(facade)

      delay(1.seconds)
      facade.loadHtml(/*language=HTML*/ "<html><body>test</body></html>")
      delay(500.milliseconds)

      assertEquals("2", facade.evaluateJavaScript(/*language=JavaScript*/ "1 + 1"))
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun loadHtml_beforeAttach_isAppliedAfterAttach(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    try {
      facade.loadHtml(/*language=HTML*/ "<html><body>queued-before-attach</body></html>")

      attach(facade)

      waitForJavaScript(facade, "document.body.textContent.trim() === 'queued-before-attach'", "true")
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun close_isIdempotent(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    try {
      attach(facade)

      delay(500.milliseconds)
      facade.close()
      facade.close()
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun evaluateJavaScript_afterClose_returnsNull(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    try {
      attach(facade)

      delay(500.milliseconds)
      facade.close()
      delay(200.milliseconds)

      assertNull(facade.evaluateJavaScript(/*language=JavaScript*/ "1 + 1"))
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun facade_survives_host_detach_reattach(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    try {
      attach(facade)

      facade.loadHtml(/*language=HTML*/ "<html><body>phase1</body></html>")
      waitForJavaScript(facade, "document.body.textContent.trim() === 'phase1'", "true")
      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "(() => { window.__wviReattachMarker = 'alive'; return true; })()"))

      SwingUtilities.invokeAndWait {
        frame!!.contentPane.removeAll()
        frame!!.revalidate()
      }
      delay(300.milliseconds)

      attach(facade)

      waitForJavaScript(facade, "document.body.textContent.trim() === 'phase1'", "true")
      waitForJavaScript(facade, "window.__wviReattachMarker === 'alive'", "true")
      assertEquals("4", facade.evaluateJavaScript(/*language=JavaScript*/ "2 + 2"))
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun webMessageReceived_reachesBus(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    val bridge = facade as WebViewEngineBridge
    val bus = WebViewMessageBusImpl(scope!!, bridge)
    bridge.connectMessageBus { rawJson -> bus.transferFromJs(rawJson) }
    val ready = CompletableDeferred<Unit>()
    bus.registerNotificationHandler(ReadyNotification) { _, _ ->
      ready.complete(Unit)
    }

    try {
      attach(facade)
      delay(1.seconds)

      facade.loadHtml(/*language=HTML*/ """
        <html>
        <body>
          <script>
            window.webkit.messageHandlers.webviewIpc.postMessage(JSON.stringify({jsonrpc: "2.0", method: "${ReadyNotification.method}", params: {}}));
          </script>
        </body>
        </html>
      """.trimIndent())

      withTimeout(5.seconds) {
        ready.await()
      }
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun waylandSnapshotHost_paintsLoadedHtml(): Unit = runSmokeTest {
    assumeTrue(LinuxWaylandWindowUtil.isSupportedToolkit(), "Wayland snapshot rendering test requires WLToolkit/Wayland")

    val facade = WebViewEngineFactory.createLinuxEngine(scope!!)
    val host = createHost(facade)
    try {
      SwingUtilities.invokeAndWait {
        frame!!.contentPane.removeAll()
        frame!!.contentPane.layout = BorderLayout()
        frame!!.contentPane.add(host, BorderLayout.CENTER)
        frame!!.revalidate()
        frame!!.repaint()
      }

      facade.loadHtml(greenHtml())

      waitForPaintedRgb(host, 0x12AB34)
    }
    finally {
      facade.close()
    }
  }

  private fun attach(facade: WebViewEngine) {
    SwingUtilities.invokeAndWait {
      frame!!.contentPane.add(createHost(facade))
      frame!!.revalidate()
    }
  }

  private fun createHost(facade: WebViewEngine): SwingWebViewHostPanel {
    val engine = facade as LinuxWebKitWebViewEngine
    return SwingWebViewHostPanel(scope!!, engine, nativeHostPeer = LinuxNativeWebViewHostPeer(engine))
  }

  private fun runSmokeTest(action: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
    withTimeout(15.seconds) {
      action()
    }
  }

  private suspend fun waitForJavaScript(facade: WebViewEngine, @Language("JavaScript") script: String, expected: String) {
    withTimeout(5.seconds) {
      while (true) {
        if (facade.evaluateJavaScript(script) == expected) return@withTimeout
        delay(100.milliseconds)
      }
    }
  }

  private suspend fun waitForPaintedRgb(host: Component, expectedRgb: Int) {
    withTimeout(5.seconds) {
      while (true) {
        val rgb = CompletableDeferred<Int>()
        SwingUtilities.invokeLater {
          val image = BufferedImage(host.width, host.height, BufferedImage.TYPE_INT_ARGB)
          val graphics = image.createGraphics()
          try {
            host.paint(graphics)
            rgb.complete(image.getRGB(host.width / 2, host.height / 2) and 0x00FFFFFF)
          }
          finally {
            graphics.dispose()
          }
        }
        if (rgb.await() == expectedRgb) return@withTimeout
        delay(100.milliseconds)
      }
    }
  }

  @Language("HTML")
  private fun greenHtml(): String = """
    <html>
    <body style="margin: 0; width: 100vw; height: 100vh; background: rgb(18, 171, 52);"></body>
    </html>
  """.trimIndent()

  @Serializable
  private class EmptyWebViewPayload

  private object ReadyNotification : WebViewNotification<EmptyWebViewPayload> {
    override val method: String = "test/ready"
    override val paramsSerializer = EmptyWebViewPayload.serializer()
  }

}
