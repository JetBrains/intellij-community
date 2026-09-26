// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import javax.swing.JComponent

interface MarkdownPreviewBrowserActions {
  fun getComponent(): JComponent

  fun showSearchBar()

  fun changeFontSize(size: Int, temporary: Boolean = false)

  fun getTemporaryFontSize(): Int?

  fun openDevtools()
}
