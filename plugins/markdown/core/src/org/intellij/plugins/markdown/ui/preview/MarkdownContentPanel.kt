// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface MarkdownContentPanel : MarkdownHtmlPanelEx {
  fun setMarkdown(markdown: String, initialScrollOffset: Int, initialScrollLineNumber: Int, document: VirtualFile?)
}
