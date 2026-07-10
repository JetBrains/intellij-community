// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.webview.impl.WebViewLogger
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Window
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal object WindowsHwndUtil {
  fun resolveWindowHwnd(component: Component): Long? {
    if (!SystemInfoRt.isWindows) return null
    val window = SwingUtilities.getWindowAncestor(component) ?: return null
    return getHwnd(window)
  }

  fun scale(component: Component): Double {
    return component.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
  }

  private fun getHwnd(window: Window): Long? {
    return try {
      val peerField = Component::class.java.getDeclaredField("peer")
      peerField.isAccessible = true
      val peer = peerField.get(window) ?: return null
      val getHWnd = peer.javaClass.getMethod("getHWnd")
      getHWnd.invoke(peer) as? Long
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      WebViewLogger.LOG.warn("Failed to resolve Windows HWND for WebView host", e)
      null
    }
  }
}
