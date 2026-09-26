// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.openapi.util.Disposer
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/**
 * The Swing fallback for [PyHtmlPanel]. [PyEmbeddedBrowserProvider] uses it when no embedded browser is available.
 */
internal class PySwingHtmlPanel : PyHtmlPanel {
  private val pane = JBHtmlPane().apply {
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
  }

  override val component: JComponent = JBScrollPane(pane).apply {
    border = JBUI.Borders.empty()
  }

  override fun setHtml(html: String) {
    UIUtil.invokeLaterIfNeeded {
      pane.text = html
      pane.caretPosition = 0
    }
  }

  override fun dispose() {
    Disposer.dispose(pane)
  }
}
