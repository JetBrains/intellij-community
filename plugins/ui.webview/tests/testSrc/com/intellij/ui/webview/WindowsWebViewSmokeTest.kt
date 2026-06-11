// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewEngine
import com.intellij.ui.webview.api.WebViewEngineFactory
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.impl.NativeBridgeLibraryAvailability
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.windows.WinNativeWebViewHostPeer
import com.intellij.ui.webview.impl.windows.WinWebViewEngine
import com.intellij.ui.webview.impl.windows.winWebView2BridgeLibrary
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Dimension
import java.nio.file.Files
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@EnabledOnOs(OS.WINDOWS)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
@Suppress("JSUnresolvedVariable")
class WindowsWebViewSmokeTest {

  private var frame: JFrame? = null
  private var scope: CoroutineScope? = null

  @BeforeEach
  fun setUp() {
    assumeTrue(nativeBridgeAvailable(), "WinWebView2Bridge DLL is not built; run community/platform/ui.webview/native/WinWebView2Bridge/build.ps1")

    @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without IJ platform
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    SwingUtilities.invokeAndWait {
      frame = JFrame("Windows WebView2 Smoke Test").apply {
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
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
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
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
    try {
      facade.loadHtml(/*language=HTML*/ "<html><body>queued-before-attach</body></html>")

      attach(facade)
      delay(1.seconds)

      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim() === 'queued-before-attach'"))
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun loadAsset_servesDirectoryBundle(): Unit = runSmokeTest {
    val tempDir = Files.createTempDirectory("win-webview-assets")
    Files.writeString(tempDir.resolve("index.html"), /*language=HTML*/ """
      <html>
      <body>
        <!--suppress HtmlUnknownTarget -->
        <script src="/__webview/wvi-bridge.js"></script>
        <!--suppress HtmlUnknownTarget -->
        <script src="./view.js"></script>
      </body>
      </html>
    """.trimIndent())
    Files.writeString(tempDir.resolve("view.js"), /*language=JavaScript*/ "window.__assetValue = 'from-asset';")

    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
    try {
      attach(facade)
      delay(1.seconds)

      facade.loadAsset(WebViewAssetRoot.fromDirectory(tempDir))

      waitForJavaScript(
        facade,
        /*language=JavaScript*/ """
          window.__assetValue === 'from-asset' && window.__WVI__ && window.__WVI__.transport() === 'webview2'
        """.trimIndent(),
        "true",
      )
    }
    finally {
      facade.close()
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun close_isIdempotent(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
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
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
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
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
    try {
      attach(facade)
      delay(1.seconds)

      facade.loadHtml(/*language=HTML*/ "<html><body>phase1</body></html>")
      delay(500.milliseconds)
      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim() === 'phase1'"))
      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "window.__wviReattachMarker = 'alive'; true"))

      SwingUtilities.invokeAndWait {
        frame!!.contentPane.removeAll()
        frame!!.revalidate()
      }
      delay(300.milliseconds)

      attach(facade)
      delay(1.seconds)

      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim() === 'phase1'"))
      assertEquals("true", facade.evaluateJavaScript(/*language=JavaScript*/ "window.__wviReattachMarker === 'alive'"))
      assertEquals("4", facade.evaluateJavaScript(/*language=JavaScript*/ "2 + 2"))
    }
    finally {
      facade.close()
    }
  }

  @Test
  fun webMessageReceived_reachesBus(): Unit = runSmokeTest {
    val facade = WebViewEngineFactory.createWindowsEngine(scope!!)
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
            window.chrome.webview.postMessage(JSON.stringify({jsonrpc: "2.0", method: "${ReadyNotification.method}", params: {}}));
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

  private fun attach(facade: WebViewEngine) {
    SwingUtilities.invokeAndWait {
      val engine = facade as WinWebViewEngine
      frame!!.contentPane.add(SwingWebViewHostPanel(scope!!, engine, nativeHostPeer = WinNativeWebViewHostPeer(engine)))
      frame!!.revalidate()
    }
  }

  private fun runSmokeTest(action: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
    withTimeout(15.seconds) {
      action()
    }
  }

  private suspend fun waitForJavaScript(facade: WebViewEngine, script: String, expected: String) {
    withTimeout(5.seconds) {
      while (facade.evaluateJavaScript(script) != expected) {
        delay(100.milliseconds)
      }
    }
  }

  private fun nativeBridgeAvailable(): Boolean {
    return winWebView2BridgeLibrary.availability() is NativeBridgeLibraryAvailability.Available
  }

  @Serializable
  private class EmptyWebViewPayload

  private object ReadyNotification : WebViewNotification<EmptyWebViewPayload> {
    override val method: String = "test/ready"
    override val paramsSerializer = EmptyWebViewPayload.serializer()
  }
}
