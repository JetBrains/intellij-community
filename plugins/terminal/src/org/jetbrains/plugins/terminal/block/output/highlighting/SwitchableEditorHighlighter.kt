// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.editor.highlighter.EditorHighlighter

/**
 * Provides the ability to switch on and off
 * highlighting for specific start offsets in the document.
 */
interface SwitchableEditorHighlighter : EditorHighlighter {

  /**
   * Determines whether the highlighting should be applied starting from the specified start offset.
   *
   * @param startOffset the start offset in the document
   * @return true if highlighting should be applied, false otherwise
   */
  fun shouldHighlight(startOffset: Int): Boolean
}
