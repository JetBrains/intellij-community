// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import junit.framework.TestCase
import org.intellij.plugins.markdown.ui.preview.SourceTextPreprocessor

internal class WebViewMarkdownPreviewPanelProviderTest : TestCase() {
  fun `test provider keeps raw markdown source text`() {
    assertSame(SourceTextPreprocessor.default, WebViewMarkdownPreviewPanelProvider().sourceTextPreprocessor)
  }
}
