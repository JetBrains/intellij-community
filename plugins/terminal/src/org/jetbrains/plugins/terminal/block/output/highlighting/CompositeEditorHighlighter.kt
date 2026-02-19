// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output.highlighting

import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * A `CompositeEditorHighlighter` combines multiple editor highlighters to allow for different highlighting behaviors.
 *
 * Highlighters can be switched based on their applicability. If multiple switchable highlighters can be applied,
 * or if no switchable highlighters are applicable, a default highlighter is used.
 *
 * @property defaultEditorHighlighter Default highlighter used if no other highlighters are applicable or
 *                                    at least two switchable highlighters are applicable
 * @property switchableEditorHighlighters List of switchable editor highlighters to combine in this composite highlighter
 */
internal abstract class CompositeEditorHighlighter(
  private val defaultEditorHighlighter: EditorHighlighter,
) : EditorHighlighter {

  protected abstract val switchableEditorHighlighters: List<SwitchableEditorHighlighter>
    @RequiresEdt(generateAssertion = false)
    get

  private var editor: HighlighterClient? = null

  /**
   * Creates a HighlighterIterator starting from the specified start offset.
   * Only one HighlighterIterator can be created for the particular start offset.
   */
  override fun createIterator(startOffset: Int): HighlighterIterator {
    val willingHighlighters = switchableEditorHighlighters.filter { highlighter ->
      highlighter.shouldHighlight(startOffset)
    }
    val chosenHighlighter = willingHighlighters.singleOrNull() ?: defaultEditorHighlighter
    return chosenHighlighter.createIterator(startOffset)
  }

  override fun setEditor(editor: HighlighterClient) {
    this.editor = editor
    switchableEditorHighlighters.forEach { it.setEditor(editor) }
  }
}
