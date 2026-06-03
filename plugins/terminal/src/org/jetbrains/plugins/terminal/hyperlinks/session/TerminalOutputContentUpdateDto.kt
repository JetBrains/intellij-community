// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset

@Serializable
@ApiStatus.Internal
data class TerminalOutputContentUpdateDto(
  val text: String,
  val startLine: Long,
  val endLine: Long,
  val startOffset: Long,
  val trimStartLine: Long,
  val trimStartOffset: Long,
  val modificationStamp: Long,
)

fun TerminalOutputContentUpdate.toDto(): TerminalOutputContentUpdateDto {
  return TerminalOutputContentUpdateDto(
    text = charsSequence.toString(),
    startLine = startLine.toAbsolute(),
    endLine = endLine.toAbsolute(),
    startOffset = startOffset.toAbsolute(),
    trimStartLine = trimStartLine.toAbsolute(),
    trimStartOffset = trimStartOffset.toAbsolute(),
    modificationStamp = modificationStamp,
  )
}

fun TerminalOutputContentUpdateDto.toUpdate(): TerminalOutputContentUpdate {
  return TerminalOutputContentUpdate(
    charsSequence = text,
    startLine = TerminalLineIndex.of(startLine),
    endLine = TerminalLineIndex.of(endLine),
    startOffset = TerminalOffset.of(startOffset),
    trimStartLine = TerminalLineIndex.of(trimStartLine),
    trimStartOffset = TerminalOffset.of(trimStartOffset),
    modificationStamp = modificationStamp,
  )
}