// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import org.jetbrains.annotations.ApiStatus

/**
 * The builder with additional options for sending text to the shell process.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalSendTextBuilder {
  /**
   * Call this method to insert the line wrap after the provided text to execute the command.
   * Prefer using this option rather than adding a line wrap manually to the text.
   */
  fun shouldExecute(): TerminalSendTextBuilder

  /**
   * Specifies to wrap the provided text into a bracketed paste mode escape sequences (if it is supported by the shell).
   * It makes the shell treat the text like it was pasted from the clipboard.
   * And also ensure that the text won't be interpreted as a shell key binding.
   */
  fun useBracketedPasteMode(): TerminalSendTextBuilder

  /**
   * Schedules writing the specified [text] with the specified options to the input stream of the shell process.
   * Actual writing is performed asynchronously.
   * It is guaranteed that if this method is called multiple times, the text will be written in the same order.
   *
   * If the shell process is not yet available, the text will be buffered and sent later.
   */
  fun send(text: String)
}