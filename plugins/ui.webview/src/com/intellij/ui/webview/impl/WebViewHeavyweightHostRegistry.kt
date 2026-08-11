// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import java.awt.Component
import java.awt.IllegalComponentStateException
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal object WebViewHeavyweightHostRegistry {
  private val hosts = ContainerUtil.createWeakSet<JComponent>()
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<Runnable>()

  @RequiresEdt
  fun register(host: JComponent): Disposable {
    hosts.add(host)
    fireChanged()
    return Disposable { unregister(host) }
  }

  @RequiresEdt
  fun componentChanged(host: JComponent) {
    if (hosts.contains(host)) {
      fireChanged()
    }
  }

  @RequiresEdt
  fun hasOverlappingHost(targetBounds: Rectangle, target: Component): Boolean {
    if (targetBounds.isEmpty) return false
    val targetWindow = SwingUtilities.getWindowAncestor(target)

    return hosts.any { host ->
      if (host === target || SwingUtilities.isDescendingFrom(host, target)) return@any false
      if (!host.isVisible || !host.isShowing || host.width <= 0 || host.height <= 0) return@any false
      if (targetWindow != null && SwingUtilities.getWindowAncestor(host) !== targetWindow) return@any false

      val hostBounds = try {
        Rectangle(host.locationOnScreen, host.size)
      }
      catch (_: IllegalComponentStateException) {
        return@any false
      }

      hostBounds.intersects(targetBounds)
    }
  }

  @RequiresEdt
  fun addChangeListener(listener: Runnable) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun removeChangeListener(listener: Runnable) {
    listeners.remove(listener)
  }

  @RequiresEdt
  private fun unregister(host: JComponent) {
    if (hosts.remove(host)) {
      fireChanged()
    }
  }

  @RequiresEdt
  private fun fireChanged() {
    listeners.forEach { it.run() }
  }
}
