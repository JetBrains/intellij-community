// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.progress.TerminalProgressState
import org.jetbrains.plugins.terminal.progress.TerminalProgressStatus

@ApiStatus.Internal
@Serializable
data class TerminalProgressStateDto(
  val status: TerminalProgressStatusDto = TerminalProgressStatusDto.NONE,
  val percent: Int = 0,
)

@ApiStatus.Internal
@Serializable
enum class TerminalProgressStatusDto {
  NONE,
  NORMAL,
  ERROR,
  INDETERMINATE,
  WARNING,
}

@ApiStatus.Internal
fun TerminalProgressState.toDto(): TerminalProgressStateDto {
  return TerminalProgressStateDto(status.toDto(), percent)
}

@ApiStatus.Internal
fun TerminalProgressStateDto.toTerminalProgressState(): TerminalProgressState {
  val coercedPercent = percent.coerceIn(0, 100)
  return when (status) {
    TerminalProgressStatusDto.NONE -> TerminalProgressState.NONE
    TerminalProgressStatusDto.NORMAL -> TerminalProgressState.normal(coercedPercent)
    TerminalProgressStatusDto.ERROR -> TerminalProgressState.error(coercedPercent)
    TerminalProgressStatusDto.INDETERMINATE -> TerminalProgressState.indeterminate()
    TerminalProgressStatusDto.WARNING -> TerminalProgressState.warning(coercedPercent)
  }
}

private fun TerminalProgressStatus.toDto(): TerminalProgressStatusDto {
  return when (this) {
    TerminalProgressStatus.NONE -> TerminalProgressStatusDto.NONE
    TerminalProgressStatus.NORMAL -> TerminalProgressStatusDto.NORMAL
    TerminalProgressStatus.ERROR -> TerminalProgressStatusDto.ERROR
    TerminalProgressStatus.INDETERMINATE -> TerminalProgressStatusDto.INDETERMINATE
    TerminalProgressStatus.WARNING -> TerminalProgressStatusDto.WARNING
  }
}
