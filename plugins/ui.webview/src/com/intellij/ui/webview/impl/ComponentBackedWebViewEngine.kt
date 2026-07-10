// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface ComponentBackedWebViewEngine : WebViewEngineBridge {
  override val isHeavyweight: Boolean
    get() = false

  val component: JComponent

  fun requestWebViewFocus()

  fun clearWebViewFocus()
}
