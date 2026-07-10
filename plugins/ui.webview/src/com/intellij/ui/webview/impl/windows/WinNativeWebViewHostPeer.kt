// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.traceWebViewPerf
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import kotlin.time.measureTimedValue

private val LOG = logger<WinNativeWebViewHostPeer>()

@ApiStatus.Internal
internal class WinNativeWebViewHostPeer(
  private val engine: WinWebViewEngine,
) : NativeWebViewHostPeer {

  private var attached = false
  private var hostHidden = true
  private var currentParentHwnd: Long? = null
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val hostPanel = host as SwingWebViewHostPanel
    return LOG.traceWebViewPerf(
      "win-webview2.host.attach",
      "displayable=${host.isDisplayable}, showing=${host.isShowing}, size=${host.width}x${host.height}",
    ) {
      engine.setShortcutTarget(host)
      engine.setBeforeMouseFocusHandler { hostPanel.activateWebViewFocusFromNativeMouse() }
      engine.setFocusGainedHandler { hostPanel.nativeWebViewFocusGained() }
      attached = true
      hostHidden = true
      lastAppliedFrame = null
      engine.setHidden(true)
      when (updateFrame(host)) {
        FrameUpdateResult.Applied -> Unit
        FrameUpdateResult.Deferred -> engine.setHidden(true)
        FrameUpdateResult.Failed -> {
          attached = false
          engine.setShortcutTarget(null)
          engine.setBeforeMouseFocusHandler(null)
          engine.setFocusGainedHandler(null)
          return@traceWebViewPerf false
        }
      }
      true
    }
  }

  override fun detach() {
    if (!attached) return
    engine.detachFromParent()
    engine.setShortcutTarget(null)
    engine.setBeforeMouseFocusHandler(null)
    engine.setFocusGainedHandler(null)
    attached = false
    hostHidden = true
    currentParentHwnd = null
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    updateFrame(host)
  }

  private fun updateFrame(host: Component): FrameUpdateResult {
    val timedUpdate = measureTimedValue {
      val parentHwnd = WindowsHwndUtil.resolveWindowHwnd(host)
      if (parentHwnd == null) {
        return@measureTimedValue FrameUpdate(FrameUpdateResult.Failed, "reason=no-parent-hwnd")
      }
      val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host)
      if (anchor == null) {
        return@measureTimedValue FrameUpdate(FrameUpdateResult.Failed, "reason=no-anchor")
      }
      val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
      val scale = WindowsHwndUtil.scale(host)
      val frame = AppliedFrame(bounds, scale)
      if (!isReadyForNativeFrame(bounds)) {
        lastAppliedFrame = null
        engine.setHidden(true)
        return@measureTimedValue FrameUpdate(FrameUpdateResult.Deferred, frameDiagnosticDetails(bounds, scale))
      }

      if (parentHwnd != currentParentHwnd) {
        currentParentHwnd = parentHwnd
        lastAppliedFrame = frame
        LOG.traceWebViewPerf(
          "win-webview2.host.updateFrame.attachToParent",
          frameDiagnosticDetails(bounds, scale),
        ) {
          engine.attachToParent(parentHwnd, bounds.x, bounds.y, bounds.width, bounds.height, scale)
        }
        updateNativeVisibility()
        return@measureTimedValue FrameUpdate(FrameUpdateResult.Applied, frameDiagnosticDetails(bounds, scale))
      }
      val frameChanged = frame != lastAppliedFrame
      lastAppliedFrame = frame
      if (frameChanged) {
        engine.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
      }
      updateNativeVisibility()
      FrameUpdate(FrameUpdateResult.Applied, "frameChanged=$frameChanged, ${frameDiagnosticDetails(bounds, scale)}")
    }
    val update = timedUpdate.value
    LOG.traceWebViewPerf("win-webview2.host.updateFrame", timedUpdate.duration, "result=${update.result}, ${update.details}")
    return update.result
  }

  private fun frameDiagnosticDetails(bounds: SwingWebViewHostPanel.NativeBounds, scale: Double): String {
    return "bounds=${bounds.x},${bounds.y} ${bounds.width}x${bounds.height}, scale=$scale"
  }

  private fun updateNativeVisibility() {
    engine.setHidden(hostHidden)
  }

  private fun isReadyForNativeFrame(bounds: SwingWebViewHostPanel.NativeBounds): Boolean {
    return bounds.width > 0 && bounds.height > 0
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    hostHidden = hidden
    if (hidden) {
      engine.setHidden(true)
      return
    }

    if (updateFrame(host) != FrameUpdateResult.Applied) {
      engine.setHidden(true)
    }
  }

  override fun requestFocus() {
    if (!attached) return
    engine.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    engine.clearFocus()
  }

  override fun clearFocusForSwingFocusTransfer() {
    clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )

  private data class FrameUpdate(
    val result: FrameUpdateResult,
    val details: String,
  )

  private enum class FrameUpdateResult {
    Applied,
    Deferred,
    Failed,
  }
}
