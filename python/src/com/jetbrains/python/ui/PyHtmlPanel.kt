// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * A panel that shows one HTML document.
 *
 * [PyEmbeddedBrowserProvider.createHtmlPanel] picks the implementation that fits the running IDE.
 */
@ApiStatus.Internal
interface PyHtmlPanel : Disposable {
  val component: JComponent

  /**
   * Replaces the shown document. Any thread can call this method.
   */
  fun setHtml(html: String)
}
