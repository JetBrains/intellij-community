// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownLivePreviewUtils {

  fun isCheckbox(text: CharSequence, offset: Int): Boolean {
    require(offset >= 0)
    require(offset + 2 < text.length)
    return text[offset] == '[' && text[offset + 1] in " xX" && text[offset + 2] == ']'
  }
}