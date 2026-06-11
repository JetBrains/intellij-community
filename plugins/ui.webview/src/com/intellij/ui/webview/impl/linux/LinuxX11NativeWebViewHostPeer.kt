// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import java.awt.Component

internal class LinuxX11NativeWebViewHostPeer(
    private val engine: LinuxWebKitWebViewEngine,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val parentXid = LinuxX11WindowUtil.resolveWindowXid(host) ?: return false
    engine.setHidden(true)
    engine.attachToX11Parent(parentXid)
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    return true
  }

  override fun detach() {
    if (!attached) return
    engine.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host) ?: return
    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
    val scale = LinuxX11WindowUtil.scale(host)
    val frame = AppliedFrame(bounds, scale)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    engine.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    engine.setHidden(hidden)
  }

  override fun requestFocus() {
    if (!attached) return
    engine.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    engine.clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )
}
