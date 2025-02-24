// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.terminal.session.StyleRange

/**
 * @param styles ranges inside the [text] bounds.
 * @param logicalLineIndex absolut index of the logical line relative to the start of the command output where provided [text] starts.
 * For example, a command can have 1000 lines of output.
 * This partial output can contain 100 lines with [logicalLineIndex] equal to 900.
 * So, by applying this output, we will replace the output from line 900 with the new 100 lines.
 * @param terminalWidth width at the moment of partial output collection.
 * @param isChangesDiscarded whether some lines were discarded from the history before being collected.
 * If this property is true, then it means that from the last call of [TerminalOutputChangesTracker.collectChangedOutputOrWait]
 * so much output arrived to the TextBuffer, so we had to discard some not yet collected lines.
 * This property is used to indicate that there are missed lines between the previously collected output and this output.
 */
internal data class PartialCommandOutput(
  val text: String,
  val styles: List<StyleRange>,
  val logicalLineIndex: Int,
  val terminalWidth: Int,
  val isChangesDiscarded: Boolean,
)