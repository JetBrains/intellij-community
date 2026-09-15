// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.jcef

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.python.ui.PyEmbeddedBrowserProvider
import com.jetbrains.python.ui.PyHtmlPanel
import javax.swing.JComponent

/**
 * Supplies the JCEF browser to the Python plugin. Loads only when the JCEF plugin is present.
 */
internal class PyJcefEmbeddedBrowserProvider : PyEmbeddedBrowserProvider {
  override fun isSupported(): Boolean = JBCefApp.isSupported()

  override fun createHtmlPanel(project: Project): PyHtmlPanel = PyJcefHtmlPanel(PyPackagingJcefHtmlPanel(project))
}

private class PyJcefHtmlPanel(private val panel: PyPackagingJcefHtmlPanel) : PyHtmlPanel {
  override val component: JComponent
    get() = panel.component

  override fun setHtml(html: String) {
    panel.setHtml(html)
  }

  override fun dispose() {
    Disposer.dispose(panel)
  }
}
