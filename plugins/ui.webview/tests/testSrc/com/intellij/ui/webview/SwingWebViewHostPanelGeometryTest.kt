// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.engine.WebViewFocusDirection
import com.intellij.ui.webview.impl.ComponentBackedWebViewEngine
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.WebViewFocusEntrySink
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class SwingWebViewHostPanelGeometryTest {

  @Test
  fun calculateNativeFrame_usesAnchorCoordinatesForNestedHost() {
    val fakeWindow = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 24, 500, 300)
    }
    fakeWindow.add(contentPane)

    val topFiller = JPanel().apply {
      setBounds(0, 0, 500, 40)
    }
    val centerPanel = JPanel(null).apply {
      setBounds(0, 40, 500, 220)
    }
    val bottomFiller = JPanel().apply {
      setBounds(0, 260, 500, 40)
    }
    contentPane.add(topFiller)
    contentPane.add(centerPanel)
    contentPane.add(bottomFiller)

    val nestedPanel = JPanel(null).apply {
      setBounds(30, 12, 260, 140)
    }
    centerPanel.add(nestedPanel)

    val host = JPanel().apply {
      setBounds(17, 9, 123, 67)
    }
    nestedPanel.add(host)

    val frame = SwingWebViewHostPanel.calculateNativeFrame(host, contentPane)
    assertEquals(SwingWebViewHostPanel.NativeFrame(47.0, 172.0, 123.0, 67.0), frame)

    val hostOriginInWindow = SwingUtilities.convertPoint(host, 0, 0, fakeWindow)
    val buggyY = contentPane.height.toDouble() - hostOriginInWindow.y.toDouble() - host.height.toDouble()
    assertEquals(148.0, buggyY)
    assertNotEquals(frame.y, buggyY)
  }

  @Test
  fun calculateWindowsBounds_usesTopLeftWindowClientCoordinatesForNestedHost() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val toolbar = JPanel().apply {
      setBounds(0, 0, 500, 40)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 40, 500, 300)
    }
    rootPane.add(toolbar)
    rootPane.add(contentPane)

    val centerPanel = JPanel(null).apply {
      setBounds(0, 40, 500, 220)
    }
    contentPane.add(centerPanel)

    val nestedPanel = JPanel(null).apply {
      setBounds(30, 12, 260, 140)
    }
    centerPanel.add(nestedPanel)

    val host = JPanel().apply {
      setBounds(17, 9, 123, 67)
    }
    nestedPanel.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(47, 101, 123, 67), bounds)
  }

  @Test
  fun calculateWindowsBounds_doesNotClipToAnchorBounds() {
    val rootPane = JPanel(null).apply {
      size = Dimension(220, 320)
    }
    val host = JPanel().apply {
      setBounds(20, 40, 300, 200)
    }
    rootPane.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 40, 300, 200), bounds)
  }

  @Test
  fun calculateWindowsBounds_doesNotClipToRootPaneContentBounds() {
    val contentPane = JPanel(null).apply {
      setBounds(0, 0, 220, 320)
    }
    val rootPane = JRootPane().apply {
      this.contentPane = contentPane
      size = Dimension(220, 320)
    }
    val host = JPanel().apply {
      setBounds(20, 40, 300, 200)
    }
    contentPane.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 40, 300, 200), bounds)
  }

  @Test
  fun calculateWindowsBounds_clipsRightAndBottomToAncestorBounds() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 40, 500, 260)
    }
    val bottomToolbar = JPanel().apply {
      setBounds(0, 300, 500, 40)
    }
    rootPane.add(contentPane)
    rootPane.add(bottomToolbar)

    val host = JPanel().apply {
      setBounds(30, 220, 500, 100)
    }
    contentPane.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(30, 260, 470, 40), bounds)
  }

  @Test
  fun calculateWindowsBounds_clipsLeftAndTopToAncestorBounds() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(20, 40, 460, 260)
    }
    rootPane.add(contentPane)

    val host = JPanel().apply {
      setBounds(-15, -25, 100, 80)
    }
    contentPane.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 40, 85, 55), bounds)
  }

  @Test
  fun calculateWindowsBounds_doesNotClipRightAndBottomToTrailingSiblings() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 0, 500, 340)
    }
    rootPane.add(contentPane)

    val host = JPanel().apply {
      setBounds(20, 40, 430, 260)
    }
    val rightToolbar = JPanel().apply {
      setBounds(360, 40, 40, 260)
    }
    val bottomToolbar = JPanel().apply {
      setBounds(20, 240, 340, 60)
    }
    contentPane.add(host)
    contentPane.add(rightToolbar)
    contentPane.add(bottomToolbar)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 40, 430, 260), bounds)
  }

  @Test
  fun calculateWindowsBounds_doesNotClipLeftAndTopToLeadingSiblings() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 0, 500, 340)
    }
    rootPane.add(contentPane)

    val host = JPanel().apply {
      setBounds(20, 20, 260, 220)
    }
    val leftToolbar = JPanel().apply {
      setBounds(0, 20, 40, 220)
    }
    val topToolbar = JPanel().apply {
      setBounds(40, 0, 240, 60)
    }
    contentPane.add(host)
    contentPane.add(leftToolbar)
    contentPane.add(topToolbar)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 20, 260, 220), bounds)
  }

  @Test
  fun calculateWindowsBounds_ignoresInvisibleSiblings() {
    val rootPane = JPanel(null).apply {
      size = Dimension(500, 340)
    }
    val contentPane = JPanel(null).apply {
      setBounds(0, 0, 500, 340)
    }
    rootPane.add(contentPane)

    val host = JPanel().apply {
      setBounds(20, 40, 430, 260)
    }
    val overlay = JPanel().apply {
      setBounds(360, 40, 40, 260)
      isVisible = false
    }
    contentPane.add(host)
    contentPane.add(overlay)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, rootPane)
    assertEquals(SwingWebViewHostPanel.NativeBounds(20, 40, 430, 260), bounds)
  }

  @Test
  fun componentBackedEngine_isMountedDirectlyAndReceivesFocusDelegation() {
    val engine = FakeComponentBackedEngine()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(scope, engine)

      assertEquals(1, host.componentCount)
      assertSame(engine.component, host.getComponent(0))
      assertTrue(host.isFocusable)
      assertTrue(host.isRequestFocusEnabled)
      assertTrue(host.isFocusCycleRoot)
      assertTrue(host.isFocusTraversalPolicyProvider)
      assertSame(engine.component, host.focusTraversalPolicy.getDefaultComponent(host))

      host.requestWebViewFocus()
      host.clearWebViewFocus()

      assertEquals(1, engine.requestFocusCount)
      assertEquals(1, engine.clearFocusCount)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun swingFocusTransfer_clearsComponentBackedEngineFocus() {
    val engine = FakeComponentBackedEngine()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(scope, engine)

      host.clearWebViewFocusForSwingFocusTransfer()

      assertEquals(1, engine.clearFocusCount)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun swingFocusTransfer_usesNativePeerTransferHookWithoutExplicitClear() {
    val engine = FakeNativeEngine()
    val peer = RecordingNativePeer()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(
        scope = scope,
        engine = engine,
        nativeHostPeer = peer,
      )

      host.clearWebViewFocusForSwingFocusTransfer()
      host.clearWebViewFocus()

      assertEquals(1, peer.clearFocusForSwingTransferCount)
      assertEquals(1, peer.clearFocusCount)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun traversalFocusEntry_requestsWebViewFocusAndNotifiesPageDirection() {
    val engine = FakeComponentBackedEngine()
    val focusEntrySink = RecordingFocusEntrySink()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(scope, engine, focusEntrySink)

      host.focusListeners.forEach { listener ->
        listener.focusGained(FocusEvent(host, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD))
      }

      assertEquals(1, engine.requestFocusCount)
      assertEquals(listOf(WebViewFocusDirection.FORWARD), focusEntrySink.entries)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun repeatedFocusEntry_requestsWebViewFocusWithoutRepeatingPageBoundaryEntry() {
    val engine = FakeComponentBackedEngine()
    val focusEntrySink = RecordingFocusEntrySink()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(scope, engine, focusEntrySink)

      host.focusListeners.forEach { listener ->
        listener.focusGained(FocusEvent(host, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD))
      }
      host.focusListeners.forEach { listener ->
        listener.focusGained(FocusEvent(host, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_BACKWARD))
      }

      assertEquals(2, engine.requestFocusCount)
      assertEquals(listOf(WebViewFocusDirection.FORWARD), focusEntrySink.entries)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun mouseFocusEntry_doesNotForceNativeWebViewFocusOrPageBoundary() {
    val engine = FakeComponentBackedEngine()
    val focusEntrySink = RecordingFocusEntrySink()
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent in this pure Swing geometry test.
    val scope = CoroutineScope(SupervisorJob())
    try {
      val host = SwingWebViewHostPanel(scope, engine, focusEntrySink)

      host.focusListeners.forEach { listener ->
        listener.focusGained(FocusEvent(host, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.MOUSE_EVENT))
      }

      assertEquals(0, engine.requestFocusCount)
      assertEquals(emptyList<WebViewFocusDirection>(), focusEntrySink.entries)
    }
    finally {
      scope.cancel()
    }
  }

  private class RecordingFocusEntrySink : WebViewFocusEntrySink {
    val entries = ArrayList<WebViewFocusDirection>()

    override fun enterWebViewFocus(direction: WebViewFocusDirection) {
      entries += direction
    }
  }

  private class RecordingNativePeer(
    private val attachResult: Boolean = true,
  ) : NativeWebViewHostPeer {
    var attachCount = 0
      private set
    var detachCount = 0
      private set
    var clearFocusCount = 0
      private set
    var clearFocusForSwingTransferCount = 0
      private set

    override fun attach(host: Component): Boolean {
      attachCount++
      return attachResult
    }

    override fun detach() {
      detachCount++
    }

    override fun scheduleFrameUpdate(host: Component) {
    }

    override fun updateVisibility(host: Component, hidden: Boolean) {
    }

    override fun requestFocus() {
    }

    override fun clearFocus() {
      clearFocusCount++
    }

    override fun clearFocusForSwingFocusTransfer() {
      clearFocusForSwingTransferCount++
    }
  }

  private class FakeNativeEngine : WebViewEngineBridge {
    override val isHeavyweight: Boolean = false

    override suspend fun loadFile(file: Path) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    }

    override suspend fun loadHtml(html: String, baseFile: Path?) {
    }

    override suspend fun evaluateJavaScript(script: String): String? = null

    override suspend fun transferToJs(rawJson: String) {
    }

    override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    }

    override suspend fun close() {
    }
  }

  private class FakeComponentBackedEngine : ComponentBackedWebViewEngine {
    override val component: JComponent = JPanel()
    var requestFocusCount = 0
      private set
    var clearFocusCount = 0
      private set

    override suspend fun loadFile(file: Path) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    }

    override suspend fun loadHtml(html: String, baseFile: Path?) {
    }

    override suspend fun evaluateJavaScript(script: String): String? = null

    override suspend fun transferToJs(rawJson: String) {
    }

    override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    }

    override suspend fun close() {
    }

    override fun requestWebViewFocus() {
      requestFocusCount++
    }

    override fun clearWebViewFocus() {
      clearFocusCount++
    }
  }
}
