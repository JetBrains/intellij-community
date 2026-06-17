// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Swing host contract for mounting a `WebViewEngine` into the IntelliJ UI tree.
 *
 * The [component] property returns a Swing component that can be added to any Swing container.
 * The native WebView is attached when the component becomes displayable (added to a visible
 * hierarchy) and detached when removed.
 *
 * **Threading**: Access [component] only on the EDT.
 */
@ApiStatus.Internal
interface SwingWebViewHost {
  val component: JComponent

  /**
   * Transfers keyboard focus into the native WebView.
   *
   * Must be called on the EDT.
   */
  fun requestWebViewFocus()

  /**
   * Clears focus from the native WebView and returns it to Swing focus traversal.
   *
   * Must be called on the EDT.
   */
  fun clearWebViewFocus()
}
