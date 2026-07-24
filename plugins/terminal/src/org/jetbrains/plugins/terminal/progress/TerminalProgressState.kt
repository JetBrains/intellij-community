// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.progress

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalProgressState(
  val status: TerminalProgressStatus,
  val percent: Int,
) {
  val isVisible: Boolean
    get() = status != TerminalProgressStatus.NONE

  val isIndeterminate: Boolean
    get() = status == TerminalProgressStatus.INDETERMINATE

  companion object {
    val NONE: TerminalProgressState = TerminalProgressState(TerminalProgressStatus.NONE, 0)

    fun normal(percent: Int): TerminalProgressState = determinate(TerminalProgressStatus.NORMAL, percent)

    fun error(percent: Int): TerminalProgressState = determinate(TerminalProgressStatus.ERROR, percent)

    fun warning(percent: Int): TerminalProgressState = determinate(TerminalProgressStatus.WARNING, percent)

    fun indeterminate(): TerminalProgressState = TerminalProgressState(TerminalProgressStatus.INDETERMINATE, 0)

    private fun determinate(status: TerminalProgressStatus, percent: Int): TerminalProgressState {
      return TerminalProgressState(status, percent.coerceIn(0, 100))
    }
  }
}

@ApiStatus.Internal
enum class TerminalProgressStatus {
  NONE,
  NORMAL,
  ERROR,
  INDETERMINATE,
  WARNING,
}
