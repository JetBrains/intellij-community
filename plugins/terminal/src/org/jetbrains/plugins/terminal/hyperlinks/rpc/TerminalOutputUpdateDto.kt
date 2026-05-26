// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.rpc

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputTrimmingUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputUpdate
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset

@Serializable
@ApiStatus.Internal
sealed interface TerminalOutputUpdateDto

@Serializable
@ApiStatus.Internal
data class TerminalOutputContentUpdateDto(
  val text: String,
  val startLine: Long,
  val endLine: Long,
  val startOffset: Long,
  val modificationStamp: Long,
) : TerminalOutputUpdateDto

@Serializable
@ApiStatus.Internal
data class TerminalOutputTrimmingUpdateDto(
  val firstLine: Long,
  val startOffset: Long,
  val endOffset: Long,
  val modificationStamp: Long,
) : TerminalOutputUpdateDto

fun TerminalOutputUpdate.toDto(): TerminalOutputUpdateDto {
  return when (this) {
    is TerminalOutputContentUpdate -> toDto()
    is TerminalOutputTrimmingUpdate -> toDto()
  }
}

fun TerminalOutputUpdateDto.toUpdate(): TerminalOutputUpdate {
  return when (this) {
    is TerminalOutputContentUpdateDto -> toUpdate()
    is TerminalOutputTrimmingUpdateDto -> toUpdate()
  }
}

fun TerminalOutputContentUpdate.toDto(): TerminalOutputContentUpdateDto {
  return TerminalOutputContentUpdateDto(
    text = charsSequence.toString(),
    startLine = startLine.toAbsolute(),
    endLine = endLine.toAbsolute(),
    startOffset = startOffset.toAbsolute(),
    modificationStamp = modificationStamp,
  )
}

fun TerminalOutputContentUpdateDto.toUpdate(): TerminalOutputContentUpdate {
  return TerminalOutputContentUpdate(
    charsSequence = text,
    startLine = TerminalLineIndex.of(startLine),
    endLine = TerminalLineIndex.of(endLine),
    startOffset = TerminalOffset.of(startOffset),
    modificationStamp = modificationStamp,
  )
}

fun TerminalOutputTrimmingUpdate.toDto(): TerminalOutputTrimmingUpdateDto {
  return TerminalOutputTrimmingUpdateDto(
    firstLine = firstLine.toAbsolute(),
    startOffset = startOffset.toAbsolute(),
    endOffset = endOffset.toAbsolute(),
    modificationStamp = modificationStamp,
  )
}

fun TerminalOutputTrimmingUpdateDto.toUpdate(): TerminalOutputTrimmingUpdate {
  return TerminalOutputTrimmingUpdate(
    firstLine = TerminalLineIndex.of(firstLine),
    startOffset = TerminalOffset.of(startOffset),
    endOffset = TerminalOffset.of(endOffset),
    modificationStamp = modificationStamp,
  )
}