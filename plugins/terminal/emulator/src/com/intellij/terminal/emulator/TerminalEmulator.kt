// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// A backend-agnostic terminal emulator API: feed it the bytes a child process writes to its PTY, then
// read back the resulting screen. No assumptions about the implementation (Ghostty, a pure-JVM
// emulator, …). The interface and its value types are self-contained and reference no engine; a
// concrete backend is obtained from the factory (see TerminalEmulatorFactory.kt).
//
// The API is split one file per concept, each named after its primary type, and every file in this
// package is part of that API:
//
//   TerminalEmulator.kt      the interface below — the only entry point
//   TerminalGrid.kt          the grid to read back: TerminalSize, Cell (+ CellStyle, CellWidth,
//                            TerminalColor, Underline), TerminalRow and its StyledText projection
//                            (+ StyleRange, HyperlinkRange)
//   TerminalCursor.kt        Cursor, CursorShape
//   TerminalMouseModes.kt    MouseProtocol, MouseEncoding
//   TerminalKeyEvents.kt     TerminalKeyEvent (+ TerminalKey, TerminalKeyAction) — key events to
//                            encode into PTY bytes
//   TerminalMouseEvents.kt   TerminalMouseEvent (+ TerminalMouseAction, TerminalMouseButton)
//   TerminalInputModifier.kt TerminalInputModifier — modifier state shared by both event types
//   TerminalProgress.kt      TerminalProgress, TerminalProgressState (OSC 9;4)
//   TerminalChangeTracking.kt ScreenChange, HistoryMark — what to repaint / append
//   TerminalListener.kt      TerminalListener, TerminalCustomCommandListener — the push callbacks
//
// Add a new concept as its own file rather than growing this one; keep this list in sync.

/**
 * A VT terminal emulator. Feed it the bytes a child process writes to its PTY via [write], then read
 * the resulting screen back through [screenLine] / [scrollbackLine] and the state properties.
 *
 * Change tracking is pull-based: after writing, call [takeChanges] to learn what to repaint. There is
 * no push callback for screen damage (protocol responses are delivered via [TerminalListener]).
 *
 * Not thread-safe: serialize all calls (one thread, or an external lock).
 */
@ApiStatus.Internal
interface TerminalEmulator : AutoCloseable {

  // geometry
  val size: TerminalSize

  /** Number of lines currently retained in scrollback (above the active screen). */
  val scrollbackRows: Int

  /** Process bytes emitted by the child process (PTY output). */
  fun write(data: ByteArray)

  fun write(text: String): Unit = write(text.encodeToByteArray())

  /** Change the screen size; the primary screen reflows if wraparound is enabled. */
  fun resize(size: TerminalSize)

  // screen state (always current)
  val cursor: Cursor

  /** Current cursor drawing shape, as selected by the program via DECSCUSR (`CSI Ps SP q`). */
  val cursorShape: CursorShape

  /**
   * Whether the cursor should currently blink. Orthogonal to [cursorShape]: it reflects the effective
   * blink state the program requested, folding in both the DECSCUSR (`CSI Ps SP q`) odd/even parity
   * (`1`/`3`/`5` blink, `2`/`4`/`6` steady) and DEC private mode 12 (`CSI ?12 h` blink / `CSI ?12 l`
   * steady). The embedder decides how (and whether) to actually animate the caret.
   */
  val cursorBlinking: Boolean

  val title: String

  /**
   * Progress the running program reports via `OSC 9;4` (`ESC ] 9 ; 4 ; <state> [; <percent>] <terminator>`),
   * or null when none is being reported — nothing was reported yet, or the program removed its previous
   * report with `OSC 9;4;0`.
   *
   * Polled state rather than an event, like [title]: the last report stands until the program replaces or
   * removes it (a terminal reset does not clear it, since the sequence carries no screen state). A program
   * typically emits a long run of `OSC 9;4;1;<percent>` reports as its work advances, so an embedder driving
   * a progress indicator should compare against the value it last rendered.
   */
  val progress: TerminalProgress?

  /** Effective default foreground color (the embedder default, or a program OSC 10 override); null if unset. */
  val foregroundColor: TerminalColor.Rgb?

  /** Effective default background color (the embedder default, or a program OSC 11 override); null if unset. */
  val backgroundColor: TerminalColor.Rgb?

  /**
   * The current RGB of palette slot [index] (`0..255`), reflecting any program `OSC 4` overrides (and
   * `OSC 104` resets). Slots `0..15` are the ANSI colors; `16..255` the xterm cube + grayscale ramp.
   *
   * This is the palette against which [TerminalColor.IndexedExtended] colors resolve to
   * [TerminalColor.Rgb]; it also lets an embedder observe program-driven recoloring of the ANSI
   * `0..15` slots (which are surfaced as [TerminalColor.IndexedAnsi] rather than resolved). Cheap and
   * safe to call per frame.
   *
   * @throws IllegalArgumentException if [index] is outside `0..255`.
   */
  fun paletteColor(index: Int): TerminalColor.Rgb

  /** True when the alternate screen buffer is active (e.g. a full-screen TUI). */
  val usingAlternateScreen: Boolean

  // input-relevant modes the UI must honor when encoding keys / mouse / paste
  val applicationCursorKeys: Boolean
  val applicationKeypad: Boolean
  val bracketedPaste: Boolean
  val mouseProtocol: MouseProtocol
  val mouseEncoding: MouseEncoding

  // encoding input into PTY bytes
  /**
   * Encodes [event] into the bytes the embedder should write to the PTY, honoring the terminal's
   * current input modes (application cursor keys / keypad, modifyOtherKeys, the Kitty keyboard
   * protocol, …). Empty when the event produces nothing — an unmodified modifier key, a release
   * outside the Kitty protocol, an event mid-IME-composition.
   */
  fun encodeKeyEvent(event: TerminalKeyEvent): ByteArray

  /**
   * Encodes [event] into the bytes the embedder should write to the PTY, honoring the terminal's
   * current mouse tracking mode and report format ([mouseProtocol] / [mouseEncoding]). Empty when the
   * event is not reported — mouse tracking off, or motion the active mode does not track.
   */
  fun encodeMouseEvent(event: TerminalMouseEvent): ByteArray

  /**
   * True while the program is inside a synchronized-output block (DEC mode 2026). Callers should
   * defer presenting frames until this is false, to avoid showing partial updates.
   *
   * Deferring is the caller's job alone: the mode gates *presentation* only, so the grid, the cursor and
   * [takeChanges] all keep advancing inside the block. Bound the wait with a timeout as well — nothing forces
   * a program to close its block, and once it stops writing there is no further input to re-check this flag
   * on, so an unbounded wait freezes the view for good.
   */
  val synchronizedOutput: Boolean

  // reading the grid
  /**
   * Row [row] of the active screen, 0-based from the top (valid range: `0 until size.rows`).
   * Out-of-range indices yield a row of empty cells rather than throwing.
   */
  fun screenLine(row: Int): TerminalRow

  /**
   * Row [row] of scrollback, 0-based from the oldest retained line (valid range:
   * `0 until scrollbackRows`). Out-of-range indices yield a row of empty cells rather than throwing.
   */
  fun scrollbackLine(row: Int): TerminalRow

  // change tracking (pull); resets the pending set
  fun takeChanges(): ScreenChange

  /**
   * Creates a [HistoryMark] at the current boundary between finalized scrollback and the live active
   * screen, for incrementally appending scrolled-off lines to a growing document. See [HistoryMark].
   */
  fun markHistoryBoundary(): HistoryMark

  // events
  var listener: TerminalListener?

  /**
   * Listener for custom OSC 1341 commands sniffed out of the [write] stream, or null (the default) to
   * disable sniffing entirely. See [TerminalCustomCommandListener].
   */
  var customCommandListener: TerminalCustomCommandListener?

  /**
   * Releases the backend's resources (for the Ghostty backend, the native terminal and its FFM
   * arena). Idempotent: calling it more than once is a no-op. After close, every other member throws
   * [IllegalStateException].
   */
  override fun close()
}
