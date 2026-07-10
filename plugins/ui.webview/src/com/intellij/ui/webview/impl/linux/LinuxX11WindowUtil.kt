// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Window
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal object LinuxX11WindowUtil {
  fun isSupportedToolkit(): Boolean = SystemInfoRt.isLinux && StartupUiUtil.isXToolkit()

  fun resolveWindowXid(component: Component): Long? {
    if (!isSupportedToolkit()) return null
    val window = SwingUtilities.getWindowAncestor(component) ?: return null
    return getXWindow(window)
  }

  fun scale(component: Component): Double {
    return component.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
  }

  private fun getXWindow(window: Window): Long? {
    return try {
      val peerField = Component::class.java.getDeclaredField("peer")
      peerField.isAccessible = true
      val peer = peerField.get(window) ?: return null
      val getWindow = Class.forName("sun.awt.X11.XBaseWindow").getDeclaredMethod("getWindow")
      getWindow.isAccessible = true
      getWindow.invoke(peer) as? Long
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      WebViewLogger.LOG.warn("Failed to resolve Linux X11 window id for WebView host", e)
      null
    }
  }
}
