// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange

internal data class CommandBlockImpl(
  override val command: String?,
  override val prompt: String?,
  override val rightPrompt: String?,
  val range: RangeMarker,
  private val commandAndRightPromptLength: Int
) : CommandBlock {
  override val startOffset: Int
    get() = range.startOffset

  override val endOffset: Int
    get() = range.endOffset

  override val commandStartOffset: Int
    get() = range.startOffset + prompt.orEmpty().length

  override val outputStartOffset: Int
    // If command or right prompt are not empty, the line break will be added after them, so add +1
    get() = commandStartOffset + if (commandAndRightPromptLength > 0) commandAndRightPromptLength + 1 else 0

  override val isFinalized: Boolean
    get() = !range.isGreedyToRight
}

internal val CommandBlock.withPrompt: Boolean
  get() = !prompt.isNullOrEmpty()

internal val CommandBlock.withCommand: Boolean
  get() = !command.isNullOrEmpty()

internal val CommandBlock.withOutput: Boolean
  get() = outputStartOffset < endOffset

internal val CommandBlock.textRange: TextRange
  get() = TextRange(startOffset, endOffset)