// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

/**
 * The libghostty-vt C enums the bridge uses, mirrored as Kotlin `enum class`es so the API can pass
 * and compare them type-safely instead of trading bare ints. Each constant carries its C ABI value
 * ([code] / [packed]); enums that are *read back* from the C API also expose a fast, allocation-free
 * `of(code)` mapper for use on the per-cell hot path.
 *
 * Values come from `ghostty/include/ghostty/vt/`. Selector enums list only the members the bridge
 * references; enums that mirror a whole closed C enum list every variant.
 */

/** `GhosttyResult` (types.h) — return code of the C API calls. */
internal enum class GhosttyResult(val code: Int) {
  SUCCESS(0),
  OUT_OF_MEMORY(-1),
  INVALID_VALUE(-2),
  OUT_OF_SPACE(-3),
  NO_VALUE(-4);

  companion object {
    /** Map a raw C result code; any unmodeled code is coerced to [INVALID_VALUE] (i.e. a failure). */
    fun of(code: Int): GhosttyResult = when (code) {
      0 -> SUCCESS
      -1 -> OUT_OF_MEMORY
      -2 -> INVALID_VALUE
      -3 -> OUT_OF_SPACE
      -4 -> NO_VALUE
      else -> INVALID_VALUE
    }
  }
}

/** `GhosttyPointTag` (point.h) — coordinate space of a `GhosttyPoint`. */
internal enum class GhosttyPointTag(val code: Int) {
  ACTIVE(0),
  VIEWPORT(1),
  SCREEN(2),
  HISTORY(3),
}

/** `GhosttyCellWide` (screen.h) — a cell's wide-character role. */
internal enum class GhosttyCellWide(val code: Int) {
  NARROW(0),
  WIDE(1),
  SPACER_TAIL(2),
  SPACER_HEAD(3);

  companion object {
    /** Map a raw C wide code; unmodeled codes are coerced to [NARROW]. */
    fun of(code: Int): GhosttyCellWide = when (code) {
      0 -> NARROW
      1 -> WIDE
      2 -> SPACER_TAIL
      3 -> SPACER_HEAD
      else -> NARROW
    }
  }
}

/** `GhosttyStyleColorTag` (style.h) — how a `GhosttyStyleColor.value` is interpreted. */
internal enum class GhosttyStyleColorTag(val code: Int) {
  NONE(0),
  PALETTE(1),
  RGB(2);

  companion object {
    /** Map a raw C color-tag code; unmodeled codes are coerced to [NONE]. */
    fun of(code: Int): GhosttyStyleColorTag = when (code) {
      0 -> NONE
      1 -> PALETTE
      2 -> RGB
      else -> NONE
    }
  }
}

/**
 * `GhosttySgrUnderline` (sgr.h) — a cell's underline style, read from
 * `GhosttyStyle.underline` (style.h).
 */
internal enum class GhosttySgrUnderline(val code: Int) {
  NONE(0),
  SINGLE(1),
  DOUBLE(2),
  CURLY(3),
  DOTTED(4),
  DASHED(5);

  companion object {
    /** Map a raw C underline code; unmodeled codes are coerced to [NONE]. */
    fun of(code: Int): GhosttySgrUnderline = when (code) {
      0 -> NONE
      1 -> SINGLE
      2 -> DOUBLE
      3 -> CURLY
      4 -> DOTTED
      5 -> DASHED
      else -> NONE
    }
  }
}

/** `GhosttyTerminalData` (terminal.h) — selector for `ghostty_terminal_get`. */
internal enum class GhosttyTerminalData(val code: Int) {
  COLS(1),
  ROWS(2),
  CURSOR_X(3),
  CURSOR_Y(4),
  ACTIVE_SCREEN(6),
  CURSOR_VISIBLE(7),
  MOUSE_TRACKING(11),
  TITLE(12),
  SCROLLBACK_ROWS(15),
  COLOR_FOREGROUND(18),
  COLOR_BACKGROUND(19),
  COLOR_PALETTE(21),
}

/** `GhosttyCellData` (screen.h) — selector for `ghostty_cell_get_multi`. */
internal enum class GhosttyCellData(val code: Int) {
  CODEPOINT(1),
  CONTENT_TAG(2),
  WIDE(3),
  /** The cell's style id (`uint16_t`); 0 is always the default style, other ids are page-local. */
  STYLE_ID(6),
  /** Whether the cell carries an OSC 8 hyperlink (bool). */
  HAS_HYPERLINK(7),
}

/** `GhosttyCellContentTag` (screen.h) — what kind of content a cell holds. */
internal enum class GhosttyCellContentTag(val code: Int) {
  CODEPOINT(0),
  CODEPOINT_GRAPHEME(1),
  BG_COLOR_PALETTE(2),
  BG_COLOR_RGB(3);

  companion object {
    /**
     * Map a raw C content-tag code. Unmodeled codes are coerced to [CODEPOINT_GRAPHEME], the
     * conservative reading: it costs one grapheme lookup instead of silently dropping text.
     */
    fun of(code: Int): GhosttyCellContentTag = when (code) {
      0 -> CODEPOINT
      1 -> CODEPOINT_GRAPHEME
      2 -> BG_COLOR_PALETTE
      3 -> BG_COLOR_RGB
      else -> CODEPOINT_GRAPHEME
    }
  }
}

/** `GhosttyRowData` (screen.h) — selector for `ghostty_row_get`. */
internal enum class GhosttyRowData(val code: Int) {
  /** Whether this row soft-wraps into the next one; reads a bool. */
  WRAP(1),
}

/** `GhosttyTerminalOption` (terminal.h) — key for `ghostty_terminal_set`. */
internal enum class GhosttyTerminalOption(val code: Int) {
  WRITE_PTY(1),
  BELL(2),
  SCROLLBACK_MAX_BYTES(27),
  PROGRESS_REPORT(30),
}

/** `GhosttyTerminalProgressState` (terminal.h) — the state of an `OSC 9;4` progress report. */
internal enum class GhosttyTerminalProgressState(val code: Int) {
  REMOVE(0),
  SET(1),
  ERROR(2),
  INDETERMINATE(3),
  PAUSE(4);

  companion object {
    /**
     * Map a raw C progress-state code, or null for one this bridge does not model — i.e. a state added to
     * the (pre-1.0) C API after this enum was written. Such a report is dropped rather than guessed at:
     * for an unknown state neither hiding nor showing progress is a safe default.
     */
    fun of(code: Int): GhosttyTerminalProgressState? = when (code) {
      0 -> REMOVE
      1 -> SET
      2 -> ERROR
      3 -> INDETERMINATE
      4 -> PAUSE
      else -> null
    }
  }
}

/** `GhosttyRenderStateData` (render.h) — selector for `ghostty_render_state_get`. */
internal enum class GhosttyRenderStateData(val code: Int) {
  DIRTY(3),
  ROW_ITERATOR(4),
  CURSOR_VISUAL_STYLE(10),
  CURSOR_BLINKING(12),
}

/** `GhosttyRenderStateRowData` (render.h) — selector for `ghostty_render_state_row_get`. */
internal enum class GhosttyRenderStateRowData(val code: Int) {
  DIRTY(1),
}

/** `GhosttyRenderStateRowOption` (render.h) — key for `ghostty_render_state_row_set`. */
internal enum class GhosttyRenderStateRowOption(val code: Int) {
  DIRTY(0),
}

/** `GhosttyRenderStateCursorVisualStyle` (render.h) — the shape the renderer would draw the cursor. */
internal enum class GhosttyCursorVisualStyle(val code: Int) {
  BAR(0),
  BLOCK(1),
  UNDERLINE(2),
  BLOCK_HOLLOW(3);

  companion object {
    /** Map a raw C visual-style code; any unmodeled code is coerced to [BLOCK]. */
    fun of(code: Int): GhosttyCursorVisualStyle = when (code) {
      0 -> BAR
      1 -> BLOCK
      2 -> UNDERLINE
      3 -> BLOCK_HOLLOW
      else -> BLOCK
    }
  }
}

/** `GhosttyRenderStateOption` (render.h) — key for `ghostty_render_state_set`. */
internal enum class GhosttyRenderStateOption(val code: Int) {
  DIRTY(0),
}

/** `GhosttyRenderStateDirty` (render.h) — global dirty level a render state reports after update. */
internal enum class GhosttyRenderStateDirty(val code: Int) {
  FALSE(0),
  PARTIAL(1),
  FULL(2);

  companion object {
    /** Map a raw dirty code; any unmodeled code is coerced to [FULL] (i.e. force a full redraw). */
    fun of(code: Int): GhosttyRenderStateDirty = when (code) {
      0 -> FALSE
      1 -> PARTIAL
      2 -> FULL
      else -> FULL
    }
  }
}

/** `GhosttySysOption` (sys.h) — key for `ghostty_sys_set`. */
internal enum class GhosttySysOption(val code: Int) {
  LOG(2),
}

/** `GhosttySysLogLevel` (sys.h) — severity of a message delivered to the sys log callback. */
internal enum class GhosttySysLogLevel(val code: Int) {
  ERROR(0),
  WARNING(1),
  INFO(2),
  DEBUG(3);

  companion object {
    /** Map a raw C log-level code; any unmodeled code is coerced to [INFO]. */
    fun of(code: Int): GhosttySysLogLevel = when (code) {
      0 -> ERROR
      1 -> WARNING
      2 -> INFO
      3 -> DEBUG
      else -> INFO
    }
  }
}

/** `GhosttyKeyAction` (key/event.h) — what happened to the key. */
internal enum class GhosttyKeyAction(val code: Int) {
  RELEASE(0),
  PRESS(1),
  REPEAT(2),
}

/**
 * `GhosttyMods` (key/event.h) bit values — the modifier bitmask of key and mouse events.
 * Side (left/right) bits are not modeled; the platform layer here cannot supply them reliably.
 */
internal object GhosttyMods {
  const val SHIFT: Int = 1 shl 0
  const val CTRL: Int = 1 shl 1
  const val ALT: Int = 1 shl 2
  const val SUPER: Int = 1 shl 3
  const val CAPS_LOCK: Int = 1 shl 4
  const val NUM_LOCK: Int = 1 shl 5
}

/** `GhosttyMouseAction` (mouse/event.h) — what happened to the mouse. */
internal enum class GhosttyMouseAction(val code: Int) {
  PRESS(0),
  RELEASE(1),
  MOTION(2),
}

/** `GhosttyMouseButton` (mouse/event.h) — button identity; FOUR/FIVE encode as wheel up/down. */
internal enum class GhosttyMouseButton(val code: Int) {
  UNKNOWN(0),
  LEFT(1),
  RIGHT(2),
  MIDDLE(3),
  FOUR(4),
  FIVE(5),
}

/** `GhosttyMouseEncoderOption` (mouse/encoder.h) — key for `ghostty_mouse_encoder_setopt`. */
internal enum class GhosttyMouseEncoderOption(val code: Int) {
  EVENT(0),
  FORMAT(1),
  SIZE(2),
  ANY_BUTTON_PRESSED(3),
  TRACK_LAST_CELL(4),
}

/**
 * DEC/ANSI mode ids for `ghostty_terminal_mode_get`, [packed] as `(value | ansi << 15)`. All below
 * are DEC private (ansi = false), so the packed id equals the raw mode number.
 */
internal enum class GhosttyMode(val packed: Int) {
  DECCKM(1),          // application cursor keys
  KEYPAD_KEYS(66),    // application keypad
  X10_MOUSE(9),
  NORMAL_MOUSE(1000),
  BUTTON_MOUSE(1002),
  ANY_MOUSE(1003),
  UTF8_MOUSE(1005),
  SGR_MOUSE(1006),
  URXVT_MOUSE(1015),
  SGR_PIXELS_MOUSE(1016),
  BRACKETED_PASTE(2004),
  SYNC_OUTPUT(2026),
}
