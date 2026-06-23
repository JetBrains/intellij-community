// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.MacMainThreadDispatcher
import com.intellij.ui.webview.impl.WebViewEditCommand
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.host.WebViewEditShortcutPolicy
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.util.ui.update.UpdateQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
internal class MacNativeWebViewHostPeer(
    private val scope: CoroutineScope,
    private val engine: MacWebViewEngine,
) : NativeWebViewHostPeer {

  private companion object {
    private const val FRAME_RETRY_LIMIT = 20
    private const val FRAME_RETRY_DELAY_MS = 50
    private const val FRAME_BOUNDS_EPSILON = 1.0
    private const val PARENT_CHAIN_LIMIT = 8
  }

  private enum class FrameRejectionReason(val logName: String) {
    NON_POSITIVE("non-positive"),
    ANCHOR_UNAVAILABLE("anchor unavailable"),
    OUTSIDE_ANCHOR_BOUNDS("outside anchor bounds"),
  }

  @Volatile
  private var attached = false
  @Volatile
  private var hostHidden = true
  @Volatile
  private var positiveFrameApplied = false
  @Volatile
  private var frameTemporarilyInvalid = true

  private var lastAppliedFrame: SwingWebViewHostPanel.NativeFrame? = null
  private var parentContentView: ID = ID.NIL
  private var frameRetryCount = 0
  private var frameRetryTimer: Timer? = null
  private var parentChainDumped = false

  /**
   * Throttle queue for resize/move coalescing. The first event arms a 16 ms timer;
   * subsequent events collapse into the latest frame without starving continuous drags.
   */
  private val resizeUpdates: UpdateQueue<SwingWebViewHostPanel.NativeFrame> = DebouncedUpdates
    .forScope<SwingWebViewHostPanel.NativeFrame>(scope, "webview-native-frame", 16.milliseconds)
    .withContext(MacMainThreadDispatcher)
    .runLatest { frame -> applyFrame(frame) }

  /**
   * WKWebView edit shortcuts are AppKit actions, not key events that can be safely replayed after
   * the IDE dispatcher sees them. The peer therefore dispatches commands through the responder chain.
   */
  override val editShortcutPolicy: WebViewEditShortcutPolicy = WebViewEditShortcutPolicy.HANDLE_IN_NATIVE_PEER

  override fun attach(host: Component): Boolean {
    if (attached) return true

    engine.initialize()

    val nsWindow = resolveNSWindow(host) ?: return false
    val contentView = Foundation.invoke(nsWindow, "contentView")
    if (Foundation.isNil(contentView)) return false
    parentContentView = contentView

    val anchor = SwingWebViewHostPanel.resolveAnchor(host) ?: return false
    val initialFrame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)
    WebViewLogger.LOG.info("Attaching WKWebView host: frame=$initialFrame, showing=${host.isShowing}")
    hostHidden = true
    positiveFrameApplied = false
    frameTemporarilyInvalid = true

    scope.launch(MacMainThreadDispatcher) {
      engine.setHidden(true)
      engine.attachToParent(contentView)
      attached = true
      engine.setHidden(true)

      SwingUtilities.invokeLater {
        (host as? SwingWebViewHostPanel)?.let { hostPanel ->
          hostPanel.syncNativePeerWithSwingState()
          hostPanel.syncWebViewFocusWithSwingFocusOwner()
        }
      }
    }
    return true
  }

  override fun detach() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      engine.detachFromParent()
    }
    resetFrameRetries()
    attached = false
    hostHidden = true
    positiveFrameApplied = false
    frameTemporarilyInvalid = true
    lastAppliedFrame = null
    parentContentView = ID.NIL
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveAnchor(host)
    if (anchor == null) {
      rejectFrame(host, null, null, FrameRejectionReason.ANCHOR_UNAVAILABLE)
      return
    }

    val frame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)
    val rejectionReason = validateFrame(anchor, frame)
    if (rejectionReason != null) {
      rejectFrame(host, anchor, frame, rejectionReason)
      return
    }

    resetFrameRetries()
    resizeUpdates.queue(frame)
  }

  override fun hasNonEmptyNativeBounds(host: Component): Boolean {
    val anchor = SwingWebViewHostPanel.resolveAnchor(host) ?: return false
    val frame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)
    return validateFrame(anchor, frame) == null
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    hostHidden = hidden

    if (!hidden) {
      if (!positiveFrameApplied) {
        frameTemporarilyInvalid = true
      }
      scheduleFrameUpdate(host)
    }

    scope.launch(MacMainThreadDispatcher) {
      updateNativeVisibility()
    }
  }

  override fun requestFocus() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      engine.requestFocus()
    }
  }

  override fun clearFocus() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      engine.clearFocus()
    }
  }

  override fun clearFocusForSwingFocusTransfer() {
    if (!attached) return
    val focusTarget = parentContentView
    if (Foundation.isNil(focusTarget)) return

    scope.launch(MacMainThreadDispatcher) {
      engine.makeFirstResponder(focusTarget)
    }
  }

  /**
   * Accepts the key press that matched the IDE shortcut and lets AppKit route the edit command to
   * WKWebView or its current private editor responder.
   */
  override fun handleWebViewShortcut(event: KeyEvent, command: WebViewEditCommand): Boolean {
    return attached && event.id == KeyEvent.KEY_PRESSED && engine.performEditCommand(command)
  }

  private fun applyFrame(frame: SwingWebViewHostPanel.NativeFrame) {
    val firstPositiveFrame = !positiveFrameApplied
    if (frame != lastAppliedFrame) {
      lastAppliedFrame = frame
      logFrame(frame, firstPositiveFrame)
      engine.setFrame(frame.x, frame.y, frame.width, frame.height)
    }

    positiveFrameApplied = true
    frameTemporarilyInvalid = false
    updateNativeVisibility()
  }

  private fun updateNativeVisibility() {
    engine.setHidden(hostHidden || !positiveFrameApplied || frameTemporarilyInvalid)
  }

  private fun validateFrame(
    anchor: Component,
    frame: SwingWebViewHostPanel.NativeFrame,
  ): FrameRejectionReason? {
    if (frame.width <= 0.0 || frame.height <= 0.0) return FrameRejectionReason.NON_POSITIVE

    val outsideAnchor =
      frame.x < -FRAME_BOUNDS_EPSILON ||
      frame.y < -FRAME_BOUNDS_EPSILON ||
      frame.x + frame.width > anchor.width + FRAME_BOUNDS_EPSILON ||
      frame.y + frame.height > anchor.height + FRAME_BOUNDS_EPSILON
    return if (outsideAnchor) FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS else null
  }

  private fun rejectFrame(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
  ) {
    val retryScheduled = scheduleFrameRetry(host)
    val hideRejectedFrame = shouldHideRejectedFrame(reason, retryScheduled)
    if (hideRejectedFrame) {
      frameTemporarilyInvalid = true
      scope.launch(MacMainThreadDispatcher) {
        updateNativeVisibility()
      }
    }
    logRejectedFrame(host, anchor, frame, reason, retryScheduled)
    logParentChainIfNeeded(host, anchor, frame, reason, retryScheduled)
  }

  private fun shouldHideRejectedFrame(reason: FrameRejectionReason, retryScheduled: Boolean): Boolean {
    return !positiveFrameApplied || reason != FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS || !retryScheduled
  }

  private fun scheduleFrameRetry(host: Component): Boolean {
    if (frameRetryTimer != null) return true
    if (frameRetryCount >= FRAME_RETRY_LIMIT) return false

    frameRetryCount++
    frameRetryTimer = Timer(FRAME_RETRY_DELAY_MS) {
      frameRetryTimer = null
      if (attached) {
        scheduleFrameUpdate(host)
      }
    }.apply {
      isRepeats = false
      start()
    }
    return true
  }

  private fun resetFrameRetries() {
    frameRetryTimer?.stop()
    frameRetryTimer = null
    frameRetryCount = 0
    parentChainDumped = false
  }

  private fun logFrame(frame: SwingWebViewHostPanel.NativeFrame, firstPositiveFrame: Boolean) {
    if (firstPositiveFrame) {
      WebViewLogger.LOG.info("Applying first positive WKWebView frame: $frame")
    }
    else {
      WebViewLogger.LOG.debug("Applying WKWebView frame: $frame")
    }
  }

  private fun logRejectedFrame(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
    retryScheduled: Boolean,
  ) {
    val retryStatus = when {
      retryScheduled -> "$frameRetryCount/$FRAME_RETRY_LIMIT"
      else -> "exhausted"
    }
    val anchorDescription = anchor?.let(::describeComponent) ?: "<unavailable>"
    WebViewLogger.LOG.info(
      "Rejected WKWebView frame: reason=${reason.logName}, frame=$frame, " +
      "host=${describeComponent(host)}, anchor=$anchorDescription, retry=$retryStatus"
    )
  }

  private fun logParentChainIfNeeded(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
    retryScheduled: Boolean,
  ) {
    val retryExhausted = !retryScheduled
    if (reason != FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS && !retryExhausted) return
    if (parentChainDumped && !retryExhausted) return

    parentChainDumped = true
    val anchorDescription = anchor?.let(::describeComponent) ?: "<unavailable>"
    WebViewLogger.LOG.warn(
      "WKWebView frame rejection details: reason=${reason.logName}, frame=$frame, " +
      "anchor=$anchorDescription, chain=${describeParentChain(host)}"
    )
  }

  private fun describeParentChain(host: Component): String {
    val components = generateSequence(host as Component?) { it.parent }
      .take(PARENT_CHAIN_LIMIT)
      .toList()
    val suffix = if (components.lastOrNull()?.parent != null) " <- ..." else ""
    return components.joinToString(" <- ") { describeComponent(it) } + suffix
  }

  private fun describeComponent(component: Component): String {
    return "${component.javaClass.name}[bounds=${component.bounds}, preferred=${component.preferredSize}, " +
           "showing=${component.isShowing}, displayable=${component.isDisplayable}]"
  }

  private fun resolveNSWindow(host: Component): ID? {
    val window = SwingUtilities.getWindowAncestor(host) ?: return null
    val nsWindow = MacUtil.getWindowFromJavaWindow(window)
    return if (Foundation.isNil(nsWindow)) null else nsWindow
  }
}
