package org.jetbrains.plugins.terminal.session.impl.dto

import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingResult

sealed interface KeyEventProcessingResultDto {
  val shouldScrollToBottom: Boolean

  data class StringResult(val string: String, override val shouldScrollToBottom: Boolean) : KeyEventProcessingResultDto
  data class BytesResult(val bytes: ByteArray, override val shouldScrollToBottom: Boolean) : KeyEventProcessingResultDto
  data object Unhandled : KeyEventProcessingResultDto {
    override val shouldScrollToBottom: Boolean = false
  }
}

fun KeyEventProcessingResult.toDto(): KeyEventProcessingResultDto = when (this) {
  is KeyEventProcessingResult.StringResult -> KeyEventProcessingResultDto.StringResult(string, shouldScrollToBottom)
  is KeyEventProcessingResult.BytesResult -> KeyEventProcessingResultDto.BytesResult(bytes, shouldScrollToBottom)
  KeyEventProcessingResult.Unhandled -> KeyEventProcessingResultDto.Unhandled
}
