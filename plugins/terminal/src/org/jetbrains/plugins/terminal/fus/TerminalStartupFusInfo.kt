// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import org.jetbrains.annotations.ApiStatus
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ApiStatus.Internal
data class TerminalStartupFusInfo @JvmOverloads constructor(
  val way: TerminalTabOpeningWay,
  val triggerTime: TimeMark? = TimeSource.Monotonic.markNow(),
)

@ApiStatus.Internal
enum class TerminalTabOpeningWay {
  OPEN_TOOLWINDOW,
  OPEN_NEW_TAB,
  START_NEW_PREDEFINED_SESSION,
  TABS_RESTORE,
  SWITCH_ENGINE,
  SPLIT_TOOLWINDOW,
  OPEN_IN_TERMINAL,
  SELECT_IN_TERMINAL,
  DND_FILE_TO_TOOLWINDOW_FROM_IDE,
  DND_FILE_TO_TOOLWINDOW_FROM_EXTERNAL_APP,
  AI_AGENTS_BUTTON,
}