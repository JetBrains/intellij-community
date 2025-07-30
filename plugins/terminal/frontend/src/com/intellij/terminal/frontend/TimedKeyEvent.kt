package com.intellij.terminal.frontend

import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * [java.awt.event.KeyEvent] wrapper that contains the [initTime] - the moment when this event was initialized.
 * Original [java.awt.event.KeyEvent] already has the [KeyEvent.`when`] property, but this value is not monotonic
 * and can't be used for reliable measurements. So, we use [kotlin.time.TimeMark] instead.
 */
@ApiStatus.Internal
data class TimedKeyEvent(
  val original: KeyEvent,
  val initTime: TimeMark = TimeSource.Monotonic.markNow(),
)