// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Model that is managing the prompt and command positions in the Prompt editor.
 */
@ApiStatus.Internal
interface TerminalPromptModel : Disposable {
  val editor: EditorEx

  /** Text and highlightings of the prompt string. Command text is not included there. */
  @get:RequiresEdt
  val renderingInfo: TerminalPromptRenderingInfo

  /**
   * Offset where the command is starting in the [editor]'s document.
   * Command occupies the range [commandStartOffset, document.textLength).
   */
  @get:RequiresEdt
  val commandStartOffset: Int

  /**
   * Returns the typed command test. Also allows updating it, leaving the prompt string untouched.
   */
  @get:RequiresEdt
  @set:RequiresEdt
  var commandText: String

  /**
   * Clears the command text, so only prompt is left.
   * Also clears the document modifications history, so it is no more possible to Undo/Redo this change.
   */
  @RequiresEdt
  fun reset()

  /**
   * Updates the prompt string, leaving the command untouched.
   */
  fun updatePrompt(state: TerminalPromptState)

  companion object {
    val KEY: Key<TerminalPromptModel> = Key.create("TerminalPromptModel")
  }
}
