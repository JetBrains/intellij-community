// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Mouse events the embedder asks the emulator to encode into PTY bytes: TerminalMouseEvent and its
// enums. Part of the backend-agnostic API; see TerminalEmulator.kt.

/** What happened to the mouse. Wheel steps are [PRESS] of [TerminalMouseButton.WHEEL_UP] / [TerminalMouseButton.WHEEL_DOWN]. */
@ApiStatus.Internal
enum class TerminalMouseAction {
  PRESS,
  RELEASE,
  MOTION,
}

@ApiStatus.Internal
enum class TerminalMouseButton {
  LEFT,
  RIGHT,
  MIDDLE,

  /** One wheel step away from the user; encodes as X11 button 4. */
  WHEEL_UP,

  /** One wheel step towards the user; encodes as X11 button 5. */
  WHEEL_DOWN,
}

/**
 * A mouse event to encode into PTY bytes via [TerminalEmulator.encodeMouseEvent].
 *
 * @param action press/release/motion.
 * @param button the button involved, or null for motion with no button held.
 * @param column 0-based cell column of the event.
 * @param row 0-based cell row of the event.
 * @param modifiers modifier state; encoded into the report per the active mouse protocol.
 */
@ApiStatus.Internal
class TerminalMouseEvent(
  val action: TerminalMouseAction,
  val button: TerminalMouseButton?,
  val column: Int,
  val row: Int,
  val modifiers: Set<TerminalInputModifier> = emptySet(),
)
