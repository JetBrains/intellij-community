// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import java.awt.Component

internal class LinuxWaylandSnapshotWebViewHostPeer(
    private val engine: LinuxWebKitWebViewEngine,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null
  private var snapshotHost: SwingWebViewHostPanel? = null

  override fun attach(host: Component): Boolean {
    val hostPanel = host as? SwingWebViewHostPanel ?: return false
    snapshotHost = hostPanel
    engine.setSnapshotHandler { width, height, pixels ->
      hostPanel.setSnapshotImage(width, height, pixels)
    }
    engine.setHidden(true)
    engine.attachOffscreen()
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    return true
  }

  override fun detach() {
    if (!attached) return
    snapshotHost?.clearSnapshotImage()
    snapshotHost = null
    engine.setSnapshotHandler(null)
    engine.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val frame = AppliedFrame(host.width, host.height)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    engine.setBounds(0, 0, host.width, host.height, 1.0)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    engine.setHidden(hidden)
    if (!hidden) {
      scheduleFrameUpdate(host)
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

  private data class AppliedFrame(
    val width: Int,
    val height: Int,
  )
}
