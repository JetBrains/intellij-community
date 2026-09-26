// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of the source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownHeaderNavigationHandler {
  /**
   * Navigates the preview before the source caret is moved to the header. Implementations may use the call to suppress the
   * duplicate preview update caused by the ensuing caret event.
   */
  fun navigateToHeader(textOffset: Int, lineNumber: Int)
}
