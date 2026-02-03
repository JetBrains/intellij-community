// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ScrollableMarkdownPreview {
  suspend fun scrollTo(editor: Editor, line: Int)
}
