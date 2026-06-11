// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Dimension
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.WindowConstants
import kotlin.coroutines.CoroutineContext

@EnabledOnOs(OS.WINDOWS)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
internal class WinWebViewOverlayBoundsTest {

  private lateinit var scope: CoroutineScope
  private lateinit var bridge: FakeWinWebView2Bridge
  private lateinit var engine: WinWebViewEngine
  private var frame: JFrame? = null

  @BeforeEach
  fun setUp() {
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent fixture scope.
    scope = CoroutineScope(SupervisorJob())
    bridge = FakeWinWebView2Bridge()
    engine = WinWebViewEngine(scope, bridge, debugName = "overlay-test", webViewDispatcher = SyncDispatcher)
  }

  @AfterEach
  fun tearDown() {
    runInEdtAndWait {
      frame?.dispose()
      frame = null
    }
    runBlocking { engine.close() }
    scope.cancel()
  }

  @Test
  fun visibleSwingOverlayDoesNotShrinkInitialBounds() {
    val fixture = createOverlayFixture()

    assertInitialFullBounds(fixture.host)
  }

  @Test
  fun removingSwingOverlayDoesNotForceBoundsChangeWithoutHostResize() {
    val fixture = createOverlayFixture()

    assertInitialFullBounds(fixture.host)
    bridge.clearBounds()
    runInEdtAndWait {
      fixture.contentPane.remove(fixture.overlay)
      fixture.contentPane.revalidate()
    }

    assertTrue(bridge.boundsSnapshot().isEmpty(), bridge.boundsSnapshot().toString())
  }

  @Test
  fun startupUsesFullHostBoundsWhenInitialAnchorIsNarrower() {
    var host: SwingWebViewHostPanel? = null

    runInEdtAndWait {
      val rootPanel = JPanel(null).apply {
        preferredSize = Dimension(220, 320)
      }
      val hostPanel = SwingWebViewHostPanel(scope, engine, nativeHostPeer = WinNativeWebViewHostPeer(engine)).apply {
        setBounds(20, 40, 300, 200)
      }
      rootPanel.add(hostPanel)

      frame = JFrame("WebView2 startup bounds test").apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        contentPane = rootPanel
        pack()
        isVisible = true
        validate()
      }
      host = hostPanel
    }
    assertTrue(bridge.visibilitySnapshot().none { it.visible }, bridge.visibilitySnapshot().toString())
    runInEdtAndWait {
      bridge.callbacks.onCreated(bridge.createdHandles.single())
    }
    assertEquals(Bounds(20, 40, 300, 200, expectedScale(host!!)), bridge.boundsSnapshot().lastOrNull()?.bounds)
    assertVisibilityApplied(Visibility(1L, true))
  }

  private fun createOverlayFixture(): OverlayFixture {
    var host: SwingWebViewHostPanel? = null
    var overlay: JPanel? = null
    var contentPane: JPanel? = null

    runInEdtAndWait {
      val root = JPanel(null).apply {
        preferredSize = Dimension(420, 320)
      }
      val hostPanel = SwingWebViewHostPanel(scope, engine, nativeHostPeer = WinNativeWebViewHostPeer(engine)).apply {
        setBounds(20, 40, 300, 200)
      }
      val overlayPanel = JPanel().apply {
        setBounds(220, 40, 120, 200)
      }
      root.add(hostPanel)
      root.add(overlayPanel)

      frame = JFrame("WebView2 overlay bounds test").apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        this.contentPane = root
        pack()
        isVisible = true
        validate()
      }
      host = hostPanel
      overlay = overlayPanel
      contentPane = root
    }
    runInEdtAndWait {
      bridge.callbacks.onCreated(bridge.createdHandles.single())
    }
    return OverlayFixture(contentPane!!, host!!, overlay!!)
  }

  private fun assertInitialFullBounds(host: SwingWebViewHostPanel) {
    val fullBounds = expectedBounds(host)
    val bounds = bridge.boundsSnapshot()
    assertEquals(fullBounds, bounds.lastOrNull()?.bounds, bounds.toString())
  }

  private fun assertVisibilityApplied(visibility: Visibility) {
    val visibilitySnapshot: List<Visibility> = runBlocking {
      val deadline = System.currentTimeMillis() + 1_000
      var snapshot = bridge.visibilitySnapshot()
      while (snapshot.lastOrNull() != visibility && System.currentTimeMillis() < deadline) {
        delay(10)
        snapshot = bridge.visibilitySnapshot()
      }
      snapshot
    }
    assertEquals(visibility, visibilitySnapshot.lastOrNull(), visibilitySnapshot.toString())
  }

  private data class OverlayFixture(
    val contentPane: JPanel,
    val host: SwingWebViewHostPanel,
    val overlay: JPanel,
  )

  private fun expectedBounds(host: SwingWebViewHostPanel): Bounds {
    var result: Bounds? = null
    runInEdtAndWait {
      val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host)!!
      val nativeBounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
      result = Bounds(nativeBounds.x, nativeBounds.y, nativeBounds.width, nativeBounds.height, WindowsHwndUtil.scale(host))
    }
    return result!!
  }

  private fun expectedScale(host: SwingWebViewHostPanel): Double {
    var result = 1.0
    runInEdtAndWait {
      result = WindowsHwndUtil.scale(host)
    }
    return result
  }

  private object SyncDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      block.run()
    }
  }

  private class FakeWinWebView2Bridge : WinWebView2BridgeApi {
    lateinit var callbacks: WinWebView2Bridge.Callbacks
      private set

    val createdHandles = mutableListOf<Long>()
    private val bounds = CopyOnWriteArrayList<BoundsRecord>()
    private val visibility = CopyOnWriteArrayList<Visibility>()
    private var nextHandle = 1L

    fun clearBounds() {
      bounds.clear()
    }

    fun boundsSnapshot(): List<BoundsRecord> = bounds.toList()

    fun visibilitySnapshot(): List<Visibility> = visibility.toList()

    override fun create(parentHwnd: Long, userDataDir: String, callbacks: WinWebView2Bridge.Callbacks): Long {
      this.callbacks = callbacks
      return nextHandle++.also { createdHandles.add(it) }
    }

    override fun destroy(handle: Long) {
    }

    override fun attachToParent(handle: Long, parentHwnd: Long) {
    }

    override fun detachFromParent(handle: Long) {
    }

    override fun setBounds(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) {
      bounds.add(BoundsRecord(handle, Bounds(x, y, width, height, scale)))
    }

    override fun setVisible(handle: Long, visible: Boolean) {
      visibility.add(Visibility(handle, visible))
    }

    override fun focus(handle: Long) {
    }

    override fun clearFocus(handle: Long) {
    }

    override fun loadUrl(handle: Long, url: String) {
    }

    override fun loadHtml(handle: Long, html: String, baseUrl: String?) {
    }

    override fun evaluateJavaScript(handle: Long, evalId: Long, script: String) {
    }

    override fun transferToJs(handle: Long, rawJson: String) {
    }
  }

  private data class BoundsRecord(
    val handle: Long,
    val bounds: Bounds,
  )

  private data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Double,
  )

  private data class Visibility(
    val handle: Long,
    val visible: Boolean,
  )
}
