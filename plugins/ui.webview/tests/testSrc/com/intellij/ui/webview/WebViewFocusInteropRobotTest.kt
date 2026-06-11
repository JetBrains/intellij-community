// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.jna.JnaLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.webview.impl.NativeBridgeLibraryAvailability
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacNativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacWebViewEngine
import com.intellij.ui.webview.impl.mac.MacWebViewFirstResponderState
import com.intellij.ui.webview.impl.mac.createMacWebViewEngine
import com.intellij.ui.webview.impl.windows.WinNativeWebViewHostPeer
import com.intellij.ui.webview.impl.windows.WinWebViewEngine
import com.intellij.ui.webview.impl.windows.createWinWebViewEngine
import com.intellij.ui.webview.impl.windows.winWebView2BridgeLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.awt.Desktop
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@EnabledOnOs(OS.MAC, OS.WINDOWS)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
class WebViewFocusInteropRobotTest {

  private var frame: JFrame? = null
  private var scope: CoroutineScope? = null

  companion object {
    private val longRunningThreadsDisposable = Disposer.newDisposable("WebViewFocusInteropRobotTest long-running threads")

    @JvmStatic
    @BeforeAll
    fun setUpClass() {
      registerLongRunningThreads()
    }

    @JvmStatic
    @AfterAll
    fun tearDownClass() {
      Disposer.dispose(longRunningThreadsDisposable)
    }

    private fun registerLongRunningThreads() {
      val trackerClass = Class.forName("com.intellij.testFramework.common.ThreadLeakTracker")
      val method = trackerClass.getMethod("longRunningThreadCreated", Disposable::class.java, Array<String>::class.java)
      method.invoke(null, longRunningThreadsDisposable, arrayOf("WebView2-Thread"))
    }
  }

  @BeforeEach
  fun setUp() {
    @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without IJ platform
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    SwingUtilities.invokeAndWait {
      frame = JFrame("WebView Focus Interop Robot Test").apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        size = Dimension(640, 420)
        setLocation(80, 80)
        isVisible = true
        requestForeground(this)
        toFront()
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
  fun clickingWebViewClearsSwingFocusAndAllowsTypingBackInSwing(@TempDir tempDir: Path): Unit = runBlocking {
    val facade = createPlatformEngine(scope!!)
    try {
      WebViewFocusRobotTestSupport.runFocusInteropScenario(
        frame!!,
        scope!!,
        facade,
        createNativeHostPeer(scope!!, facade),
        tempDir,
      )
    }
    finally {
      facade.close()
    }
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun returningToSwingMovesMacFirstResponderOutsideWebView(@TempDir tempDir: Path): Unit = runBlocking {
    ensureJna()
    val facade = createMacWebViewEngine(scope!!)
    try {
      WebViewFocusRobotTestSupport.runMacFirstResponderFocusTransferScenario(
        frame = frame!!,
        scope = scope!!,
        engine = facade,
        nativeHostPeer = MacNativeWebViewHostPeer(scope!!, facade),
        tempDir = tempDir,
        assertNativeFocusReadyForSwingTyping = { assertFirstResponderOutsideWebView(facade) },
      )
    }
    finally {
      facade.close()
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun selectingTextInWebViewWithoutTabbablesDoesNotBounceFocusBackToSwing(@TempDir tempDir: Path): Unit = runBlocking {
    val facade = createPlatformEngine(scope!!)
    try {
      WebViewFocusRobotTestSupport.runNonTabbableSelectionScenario(
        frame!!,
        scope!!,
        facade,
        createNativeHostPeer(scope!!, facade),
        tempDir,
      )
    }
    finally {
      facade.close()
    }
  }

  private fun createPlatformEngine(scope: CoroutineScope): WebViewEngineBridge {
    val osName = System.getProperty("os.name", "")
    return when {
      osName.startsWith("Mac", ignoreCase = true) -> {
        ensureJna()
        createMacWebViewEngine(scope)
      }
      osName.startsWith("Windows", ignoreCase = true) -> {
        assumeTrue(nativeBridgeAvailable(), "WinWebView2Bridge DLL is not built; run community/platform/ui.webview/native/WinWebView2Bridge/build.ps1")
        createWinWebViewEngine(scope)
      }
      else -> error("Unsupported OS for WebView focus interop Robot test: $osName")
    }
  }

  private fun createNativeHostPeer(scope: CoroutineScope, engine: WebViewEngineBridge): NativeWebViewHostPeer {
    return when (engine) {
      is MacWebViewEngine -> MacNativeWebViewHostPeer(scope, engine)
      is WinWebViewEngine -> WinNativeWebViewHostPeer(engine)
      else -> error("Unsupported WebView engine for focus interop Robot test: ${engine.javaClass.name}")
    }
  }

  private fun ensureJna() {
    if (!JnaLoader.isLoaded()) {
      JnaLoader.load(Logger.getInstance(WebViewFocusInteropRobotTest::class.java))
    }
  }

  private fun nativeBridgeAvailable(): Boolean {
    return winWebView2BridgeLibrary.availability() is NativeBridgeLibraryAvailability.Available
  }

  private suspend fun assertFirstResponderOutsideWebView(engine: MacWebViewEngine) {
    var lastState: MacWebViewFirstResponderState? = null
    val matched = withTimeoutOrNull(2.seconds) {
      while (true) {
        lastState = engine.firstResponderState()
        val state = lastState
        if (state != null && state.hasResponder && !state.isInsideWebView) return@withTimeoutOrNull true
        delay(50.milliseconds)
      }
    } == true
    assertTrue(matched, "macOS first responder did not move back outside WKWebView; lastState=$lastState")
  }

  private fun requestForeground(frame: JFrame) {
    runCatching {
      Desktop.getDesktop().requestForeground(true)
    }
    frame.isAlwaysOnTop = true
    frame.toFront()
    frame.isAlwaysOnTop = false
  }
}
