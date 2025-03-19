// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorDescription
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorStateListener

/**
 * Model that is managing the prompt and command positions in the Prompt editor.
 */
@ApiStatus.Internal
interface TerminalPromptModel : Disposable {
  val editor: EditorEx

  /** Values used to build the prompt string */
  @get:RequiresEdt
  val promptState: TerminalPromptState?

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
   * Clears the document modifications history, so it is no more possible to Undo/Redo the changes made before this call.
   */
  @RequiresEdt
  fun resetChangesHistory()

  @RequiresEdt
  fun setErrorDescription(errorDescription: TerminalPromptErrorDescription?)

  fun addErrorStateListener(listener: TerminalPromptErrorStateListener, parentDisposable: Disposable)

  companion object {
    val KEY: Key<TerminalPromptModel> = Key.create("TerminalPromptModel")
  }
}

internal fun TerminalPromptModel.clearCommandAndResetChangesHistory() {
  commandText = ""
  editor.caretModel.moveToOffset(editor.document.textLength)
  resetChangesHistory()
}
