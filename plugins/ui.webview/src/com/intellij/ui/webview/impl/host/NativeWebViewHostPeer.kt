// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.host

import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
interface NativeWebViewHostPeer {
  fun attach(host: Component): Boolean
  fun detach()
  fun scheduleFrameUpdate(host: Component)
  fun hasNonEmptyNativeBounds(host: Component): Boolean = SwingWebViewHostPanel.hasNonEmptyClippedBounds(host)
  fun updateVisibility(host: Component, hidden: Boolean)
  fun requestFocus()
  fun clearFocus()

  /**
   * Called when Swing focus moves outside the host. Backends may need a platform-specific
   * transfer that differs from clearing native focus completely.
   */
  fun clearFocusForSwingFocusTransfer() {
  }
}
