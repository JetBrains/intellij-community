// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyCellData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyCellWide
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyCursorVisualStyle
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyKeyAction
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_BYTE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_FLOAT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_INT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_LONG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_PTR
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_SHORT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.GRID_REF
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT_COORD
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT_COORD_OFF_Y
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT_OFF_TAG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT_OFF_X
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT_OFF_Y
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.PROGRESS_REPORT_MIN_SIZE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.PROGRESS_REPORT_OFF_PROGRESS
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.PROGRESS_REPORT_OFF_STATE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_BG_TAG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_BG_VAL
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_BLINK
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_BOLD
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_FAINT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_FG_TAG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_FG_VAL
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_INVERSE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_INVISIBLE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_ITALIC
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_OFF_UNDERLINE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STYLE_SIZE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyMode
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyMods
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyMouseAction
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyMouseButton
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyMouseEncoderOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyPointTag
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRenderStateData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRenderStateDirty
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRenderStateOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRenderStateRowData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRenderStateRowOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyRowData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttySgrUnderline
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyStyleColorTag
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalProgressState
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import com.intellij.terminal.emulator.Cell
import com.intellij.terminal.emulator.CellStyle
import com.intellij.terminal.emulator.CellWidth
import com.intellij.terminal.emulator.Cursor
import com.intellij.terminal.emulator.CursorShape
import com.intellij.terminal.emulator.HistoryMark
import com.intellij.terminal.emulator.MouseEncoding
import com.intellij.terminal.emulator.MouseProtocol
import com.intellij.terminal.emulator.ScreenChange
import com.intellij.terminal.emulator.TerminalColor
import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalInputModifier
import com.intellij.terminal.emulator.TerminalKeyAction
import com.intellij.terminal.emulator.TerminalKeyEvent
import com.intellij.terminal.emulator.TerminalListener
import com.intellij.terminal.emulator.TerminalMouseAction
import com.intellij.terminal.emulator.TerminalMouseButton
import com.intellij.terminal.emulator.TerminalMouseEvent
import com.intellij.terminal.emulator.TerminalProgress
import com.intellij.terminal.emulator.TerminalProgressState
import com.intellij.terminal.emulator.TerminalRow
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.Underline
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.charset.StandardCharsets

/**
 * A [TerminalEmulator] backed by the ghostty VT engine (libghostty-vt). This class owns the native
 * memory (a shared [Arena] + reusable scratch buffers) and maps ghostty's C API onto the
 * engine-agnostic [TerminalEmulator] surface; [LibGhosttyVt] holds the actual FFM downcall handles.
 * All ghostty-specific types stay private to this class.
 *
 * Only the slice of the C API needed to mirror the screen + scrollback is used: terminal lifecycle,
 * `vt_write`, `terminal_get` for geometry/modes, the render state for dirty tracking, and the
 * grid-reference read path (`grid_ref` → `grid_ref_cell` / `grid_ref_style` / `grid_ref_row`).
 *
 * The shared library file is resolved by [com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVtLocator].
 *
 * Not thread-safe: serialize all calls externally (it uses a shared Arena but reusable scratch
 * buffers).
 */
internal class GhosttyTerminalEmulator(
  initialSize: TerminalSize,
  maxScrollbackBytes: Long,
) : TerminalEmulator {

  // Shared (not confined): the embedding touches ghostty from more than one thread (the VT read
  // loop and the resize executor). Callers MUST serialize access externally.
  private val arena: Arena = Arena.ofShared()
  private val terminal: MemorySegment

  // Render state: a viewport snapshot with its own dirty tracking, used by takeChanges() (render.h).
  private val renderState: MemorySegment
  private var closed = false

  // Whether a broken-contract grid read has been reported; see logGridReadFailure.
  private var gridReadFailureLogged = false

  override var listener: TerminalListener? = null

  override var customCommandListener: TerminalCustomCommandListener? = null

  // Sniffs OSC 1341 "custom command" sequences out of the raw write() stream: ghostty treats them as
  // unknown OSC and drops them, so we scan for them ourselves and forward to customCommandListener.
  private val customCommandSniffer = OscCustomCommandSniffer { args -> customCommandListener?.onCustomCommand(args) }

  // Last OSC 9;4 report, set by the progress-report effect and kept until the program replaces or removes
  // it (see [progress]).
  private var progressReport: TerminalProgress? = null

  // Reusable scratch buffers + cell holder (this instance is single-threaded).
  private val scratchPoint: MemorySegment = arena.allocate(POINT)
  private val scratchGridRef: MemorySegment = arena.allocate(GRID_REF)
  private val scratchCell: MemorySegment = arena.allocate(8L)
  private val scratchRow: MemorySegment = arena.allocate(8L)
  private val scratchOut: MemorySegment = arena.allocate(16L)
  private val scratchStyle: MemorySegment = arena.allocate(STYLE_SIZE)
  private var scratchUri: MemorySegment = arena.allocate(256L)     // grows on OUT_OF_SPACE
  private val scratchUriLen: MemorySegment = arena.allocate(C_LONG)
  private var scratchGraphemes: MemorySegment = arena.allocate(8L * C_INT.byteSize()) // uint32[8]; grows on OUT_OF_SPACE
  private val scratchGraphemesLen: MemorySegment = arena.allocate(C_LONG)
  private val scratchCellData = CellData()

  // The one and only input buffer for [write], which feeds longer input through it a chunk at a time. Both
  // halves of that matter: the arena is shared and frees nothing before close(), so allocating per write
  // would grow native usage with the *total* bytes ever written, and growing this buffer on demand would
  // strand every superseded segment for the same reason.
  private val scratchWrite: MemorySegment = arena.allocate(WRITE_BUFFER_BYTES)

  // Per-row dirty tracking (render.h row iterator). [rowIterSlot] holds the reusable iterator handle;
  // [scratchFalse] is a zeroed bool passed to clear a row's dirty flag once consumed.
  private val rowIterSlot: MemorySegment = arena.allocate(C_PTR)
  private val scratchFalse: MemorySegment = arena.allocate(1L)

  // The live 256-color palette, cached in Kotlin as packed 0xRRGGBB so lookups never touch native
  // memory. [scratchPalette] is only the staging buffer for the single bulk read done by
  // ensurePaletteLoaded when [paletteDirty] (set by every write, which may carry OSC 4 / 104).
  private val scratchPalette: MemorySegment = arena.allocate(256L * 3)
  private val paletteCache = IntArray(256)
  private var paletteDirty = true

  // Key/mouse encoders and their reusable event objects (key/encoder.h, mouse/encoder.h). Encoder
  // options are refreshed from the terminal on every encode call, so a program flipping DECCKM or a
  // mouse mode mid-session is picked up without any mirroring.
  private val keyEncoder: MemorySegment
  private val keyEvent: MemorySegment
  private val mouseEncoder: MemorySegment
  private val mouseEvent: MemorySegment
  private var scratchEncode: MemorySegment = arena.allocate(128L)  // grows on OUT_OF_SPACE
  private val scratchEncodeLen: MemorySegment = arena.allocate(C_LONG)
  private var scratchKeyText: MemorySegment = arena.allocate(64L)  // grows on demand (IME strings)
  private val scratchMousePosition: MemorySegment = arena.allocate(GhosttyLayouts.MOUSE_POSITION)
  private val scratchMouseSize: MemorySegment = arena.allocate(GhosttyLayouts.MOUSE_ENCODER_SIZE_BYTES)

  init {
    // Tracks what has been created so far, so a failure partway through can roll back what already
    // succeeded: this constructor either fully succeeds or leaves no native handles behind, since a
    // thrown exception here discards the half-built instance without ever running close().
    var terminalHandle: MemorySegment? = null
    var renderStateHandle: MemorySegment? = null
    var rowIteratorCreated = false
    var keyEncoderHandle: MemorySegment? = null
    var keyEventHandle: MemorySegment? = null
    var mouseEncoderHandle: MemorySegment? = null
    var mouseEventHandle: MemorySegment? = null
    try {
      val outTerminal = arena.allocate(C_PTR)
      val result = try {
        LibGhosttyVt.terminalNew(MemorySegment.NULL, outTerminal, initialSize.columns.toShort(), initialSize.rows.toShort())
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_terminal_new failed", t)
      }
      if (result != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_terminal_new returned $result")
      }
      terminalHandle = outTerminal.get(C_PTR, 0L)

      // The scrollback budget is not a creation-time option: a fresh terminal starts at ghostty's own
      // default, so override it here, before any output can reach the engine. A limit of 0 disables
      // scrollback entirely.
      try {
        val maxScrollback = arena.allocate(C_LONG) // size_t*
        maxScrollback.set(C_LONG, 0L, maxScrollbackBytes)
        val r = LibGhosttyVt.terminalSet(terminalHandle, GhosttyTerminalOption.SCROLLBACK_MAX_BYTES.code, maxScrollback)
        if (r != GhosttyResult.SUCCESS) {
          throw IllegalStateException("ghostty_terminal_set(SCROLLBACK_MAX_BYTES) returned $r")
        }
      } catch (t: Throwable) {
        throw RuntimeException("setting the scrollback byte limit failed", t)
      }

      val outRenderState = arena.allocate(C_PTR)
      val renderResult = try {
        LibGhosttyVt.renderStateNew(MemorySegment.NULL, outRenderState)
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_render_state_new failed", t)
      }
      if (renderResult != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_render_state_new returned $renderResult")
      }
      renderStateHandle = outRenderState.get(C_PTR, 0L)

      // Create the reusable per-row dirty iterator ([rowIterSlot] receives the handle); takeChanges()
      // re-populates it from the render state each frame.
      val rowIterResult = try {
        LibGhosttyVt.renderStateRowIteratorNew(MemorySegment.NULL, rowIterSlot)
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_render_state_row_iterator_new failed", t)
      }
      if (rowIterResult != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_render_state_row_iterator_new returned $rowIterResult")
      }
      rowIteratorCreated = true

      // Install the write-pty effect once; it routes engine responses to the current [listener].
      try {
        val handle = MethodHandles.lookup().bind(this, "onWritePty",
          MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java,
            MemorySegment::class.java, java.lang.Long.TYPE))
        val stub = LibGhosttyVt.writePtyUpcallStub(handle, arena)
        val r = LibGhosttyVt.terminalSet(terminalHandle, GhosttyTerminalOption.WRITE_PTY.code, stub)
        if (r != GhosttyResult.SUCCESS) {
          throw IllegalStateException("ghostty_terminal_set(WRITE_PTY) returned $r")
        }
      } catch (t: Throwable) {
        throw RuntimeException("installing write-pty effect failed", t)
      }

      // Install the bell effect; it notifies the current [listener] on BEL (0x07).
      try {
        val bellHandle = MethodHandles.lookup().bind(this, "onBell",
          MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java))
        val bellStub = LibGhosttyVt.bellUpcallStub(bellHandle, arena)
        val r = LibGhosttyVt.terminalSet(terminalHandle, GhosttyTerminalOption.BELL.code, bellStub)
        if (r != GhosttyResult.SUCCESS) {
          throw IllegalStateException("ghostty_terminal_set(BELL) returned $r")
        }
      } catch (t: Throwable) {
        throw RuntimeException("installing bell effect failed", t)
      }

      // Install the progress-report effect; it records the program's OSC 9;4 reports for [progress].
      try {
        val progressHandle = MethodHandles.lookup().bind(this, "onProgressReport",
          MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java,
            MemorySegment::class.java))
        val progressStub = LibGhosttyVt.progressReportUpcallStub(progressHandle, arena)
        val r = LibGhosttyVt.terminalSet(terminalHandle, GhosttyTerminalOption.PROGRESS_REPORT.code, progressStub)
        if (r != GhosttyResult.SUCCESS) {
          throw IllegalStateException("ghostty_terminal_set(PROGRESS_REPORT) returned $r")
        }
      } catch (t: Throwable) {
        throw RuntimeException("installing progress-report effect failed", t)
      }

      keyEncoderHandle = createInputHandle("ghostty_key_encoder_new", LibGhosttyVt::keyEncoderNew)
      keyEventHandle = createInputHandle("ghostty_key_event_new", LibGhosttyVt::keyEventNew)
      mouseEncoderHandle = createInputHandle("ghostty_mouse_encoder_new", LibGhosttyVt::mouseEncoderNew)
      mouseEventHandle = createInputHandle("ghostty_mouse_event_new", LibGhosttyVt::mouseEventNew)

      // Motion deduplication is left to the embedder, which knows the real event stream; with it on,
      // the encoder would silently drop repeated motion in the same cell.
      try {
        scratchOut.set(C_BYTE, 0L, 0)
        LibGhosttyVt.mouseEncoderSetopt(mouseEncoderHandle, GhosttyMouseEncoderOption.TRACK_LAST_CELL.code, scratchOut)
      } catch (t: Throwable) {
        throw RuntimeException("configuring the mouse encoder failed", t)
      }
    } catch (t: Throwable) {
      if (mouseEventHandle != null) {
        try { LibGhosttyVt.mouseEventFree(mouseEventHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (mouseEncoderHandle != null) {
        try { LibGhosttyVt.mouseEncoderFree(mouseEncoderHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (keyEventHandle != null) {
        try { LibGhosttyVt.keyEventFree(keyEventHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (keyEncoderHandle != null) {
        try { LibGhosttyVt.keyEncoderFree(keyEncoderHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (rowIteratorCreated) {
        try { LibGhosttyVt.renderStateRowIteratorFree(rowIterSlot.get(C_PTR, 0L)) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (renderStateHandle != null) {
        try { LibGhosttyVt.renderStateFree(renderStateHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      if (terminalHandle != null) {
        try { LibGhosttyVt.terminalFree(terminalHandle) } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      }
      try { arena.close() } catch (suppressed: Throwable) { t.addSuppressed(suppressed) }
      throw t
    }
    terminal = terminalHandle!!
    renderState = renderStateHandle!!
    keyEncoder = keyEncoderHandle
    keyEvent = keyEventHandle
    mouseEncoder = mouseEncoderHandle
    mouseEvent = mouseEventHandle
  }

  /** Create a handle via a `(allocator, out) -> result` constructor, failing fast like the other init steps. */
  private fun createInputHandle(what: String, create: (MemorySegment, MemorySegment) -> GhosttyResult): MemorySegment {
    val out = arena.allocate(C_PTR)
    val result = try {
      create(MemorySegment.NULL, out)
    } catch (t: Throwable) {
      throw RuntimeException("$what failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("$what returned $result")
    }
    return out.get(C_PTR, 0L)
  }

  // ---- geometry / state ----

  override val size: TerminalSize
    get() = TerminalSize(terminalGetU16(GhosttyTerminalData.COLS), terminalGetU16(GhosttyTerminalData.ROWS))

  // The native count is a size_t, but the row count is bounded by the scrollback size limit (see
  // createTerminalEmulator): even the largest possible limit holds only a few hundred million rows,
  // well within Int range, so narrowing needs no overflow guard.
  override val scrollbackRows: Int
    get() = terminalGetSize(GhosttyTerminalData.SCROLLBACK_ROWS).toInt()

  override val cursor: Cursor
    get() = Cursor(
      terminalGetU16(GhosttyTerminalData.CURSOR_X),
      terminalGetU16(GhosttyTerminalData.CURSOR_Y),
      terminalGetBool(GhosttyTerminalData.CURSOR_VISIBLE),
    )

  override val cursorShape: CursorShape
    get() = readCursorShape()

  override val cursorBlinking: Boolean
    get() = readCursorBlinking()

  override val title: String
    get() = readTitle()

  override val progress: TerminalProgress?
    get() {
      ensureOpen()
      return progressReport
    }

  override val foregroundColor: TerminalColor.Rgb?
    get() = terminalGetRgb(GhosttyTerminalData.COLOR_FOREGROUND)

  override val backgroundColor: TerminalColor.Rgb?
    get() = terminalGetRgb(GhosttyTerminalData.COLOR_BACKGROUND)

  override fun paletteColor(index: Int): TerminalColor.Rgb {
    require(index in 0..255) { "palette index must be in 0..255, was $index" }
    ensureOpen()
    return paletteRgb(index)
  }

  override val usingAlternateScreen: Boolean
    get() = terminalGetU16(GhosttyTerminalData.ACTIVE_SCREEN) == 1

  override val applicationCursorKeys: Boolean get() = modeEnabled(GhosttyMode.DECCKM)
  override val applicationKeypad: Boolean get() = modeEnabled(GhosttyMode.KEYPAD_KEYS)
  override val bracketedPaste: Boolean get() = modeEnabled(GhosttyMode.BRACKETED_PASTE)
  override val synchronizedOutput: Boolean get() = modeEnabled(GhosttyMode.SYNC_OUTPUT)

  override val mouseProtocol: MouseProtocol
    get() = when {
      modeEnabled(GhosttyMode.ANY_MOUSE) -> MouseProtocol.ANY
      modeEnabled(GhosttyMode.BUTTON_MOUSE) -> MouseProtocol.BUTTON
      modeEnabled(GhosttyMode.NORMAL_MOUSE) -> MouseProtocol.NORMAL
      modeEnabled(GhosttyMode.X10_MOUSE) -> MouseProtocol.X10
      else -> MouseProtocol.NONE
    }

  override val mouseEncoding: MouseEncoding
    get() = when {
      modeEnabled(GhosttyMode.SGR_PIXELS_MOUSE) -> MouseEncoding.SGR_PIXELS
      modeEnabled(GhosttyMode.SGR_MOUSE) -> MouseEncoding.SGR
      modeEnabled(GhosttyMode.URXVT_MOUSE) -> MouseEncoding.URXVT
      modeEnabled(GhosttyMode.UTF8_MOUSE) -> MouseEncoding.UTF8
      else -> MouseEncoding.DEFAULT
    }

  // ---- input ----

  override fun write(data: ByteArray) {
    ensureOpen()
    if (customCommandListener != null) {
      customCommandSniffer.feed(data)
    }
    // Feed [scratchWrite]-sized chunks.
    // An empty write still reaches the engine.
    var offset = 0
    do {
      val chunk = minOf(data.size - offset, WRITE_BUFFER_BYTES.toInt())
      MemorySegment.copy(data, offset, scratchWrite, C_BYTE, 0L, chunk)
      try {
        LibGhosttyVt.terminalVtWrite(terminal, scratchWrite, chunk.toLong())
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_terminal_vt_write failed", t)
      }
      offset += chunk
    } while (offset < data.size)
    paletteDirty = true // only a write (OSC 4 / 104 / RIS) may change palette
  }

  override fun resize(size: TerminalSize) {
    ensureOpen()
    try {
      // cell pixel size is irrelevant for grid reflow; pass 1x1.
      val r = LibGhosttyVt.terminalResize(terminal, size.columns.toShort(), size.rows.toShort(), 1, 1)
      if (r != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_terminal_resize returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_resize failed", t)
    }
  }

  // ---- encoding input into PTY bytes ----

  override fun encodeKeyEvent(event: TerminalKeyEvent): ByteArray {
    ensureOpen()
    try {
      LibGhosttyVt.keyEventSetAction(keyEvent, event.action.toGhostty().code)
      // TerminalKey mirrors GhosttyKey entry by entry, so the ordinal is the C value.
      LibGhosttyVt.keyEventSetKey(keyEvent, event.key.ordinal)
      LibGhosttyVt.keyEventSetMods(keyEvent, modsBits(event.modifiers))
      setKeyEventText(event.text)
      LibGhosttyVt.keyEventSetUnshiftedCodepoint(keyEvent, event.unshiftedCodepoint)
      LibGhosttyVt.keyEventSetComposing(keyEvent, event.composing)

      LibGhosttyVt.keyEncoderSetoptFromTerminal(keyEncoder, terminal)
      return encodeToBytes { buf, size, outLen -> LibGhosttyVt.keyEncoderEncode(keyEncoder, keyEvent, buf, size, outLen) }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty key encoding failed", t)
    }
  }

  override fun encodeMouseEvent(event: TerminalMouseEvent): ByteArray {
    ensureOpen()
    try {
      LibGhosttyVt.mouseEncoderSetoptFromTerminal(mouseEncoder, terminal)
      // The encoder converts surface pixels to cells; 1x1-pixel cells make the cell coordinates of
      // [event] pass through unchanged. SGR-pixels reports then carry cell-granularity values, which
      // is the best this cell-based API can do.
      scratchMouseSize.fill(0)
      scratchMouseSize.set(C_LONG, 0L, GhosttyLayouts.MOUSE_ENCODER_SIZE_BYTES)
      scratchMouseSize.set(C_INT, GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_SCREEN_WIDTH, terminalGetU16(GhosttyTerminalData.COLS))
      scratchMouseSize.set(C_INT, GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_SCREEN_HEIGHT, terminalGetU16(GhosttyTerminalData.ROWS))
      scratchMouseSize.set(C_INT, GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_CELL_WIDTH, 1)
      scratchMouseSize.set(C_INT, GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_CELL_HEIGHT, 1)
      LibGhosttyVt.mouseEncoderSetopt(mouseEncoder, GhosttyMouseEncoderOption.SIZE.code, scratchMouseSize)

      // Button-event tracking (mode 1002) reports motion only while a button is held.
      val anyButtonPressed = event.button != null && event.action != TerminalMouseAction.RELEASE
      scratchOut.set(C_BYTE, 0L, if (anyButtonPressed) 1 else 0)
      LibGhosttyVt.mouseEncoderSetopt(mouseEncoder, GhosttyMouseEncoderOption.ANY_BUTTON_PRESSED.code, scratchOut)

      LibGhosttyVt.mouseEventSetAction(mouseEvent, event.action.toGhostty().code)
      val button = event.button
      if (button != null) {
        LibGhosttyVt.mouseEventSetButton(mouseEvent, button.toGhostty().code)
      }
      else {
        LibGhosttyVt.mouseEventClearButton(mouseEvent)
      }
      LibGhosttyVt.mouseEventSetMods(mouseEvent, modsBits(event.modifiers))
      scratchMousePosition.set(C_FLOAT, GhosttyLayouts.MOUSE_POSITION_OFF_X, event.column.toFloat())
      scratchMousePosition.set(C_FLOAT, GhosttyLayouts.MOUSE_POSITION_OFF_Y, event.row.toFloat())
      LibGhosttyVt.mouseEventSetPosition(mouseEvent, scratchMousePosition)

      return encodeToBytes { buf, size, outLen -> LibGhosttyVt.mouseEncoderEncode(mouseEncoder, mouseEvent, buf, size, outLen) }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty mouse encoding failed", t)
    }
  }

  /** Run an encode call against [scratchEncode], growing it once on `OUT_OF_SPACE`; empty when nothing to send. */
  private inline fun encodeToBytes(encode: (buf: MemorySegment, size: Long, outLen: MemorySegment) -> GhosttyResult): ByteArray {
    scratchEncodeLen.set(C_LONG, 0L, 0L)
    var result = encode(scratchEncode, scratchEncode.byteSize(), scratchEncodeLen)
    if (result == GhosttyResult.OUT_OF_SPACE) {
      scratchEncode = arena.allocate(scratchEncodeLen.get(C_LONG, 0L))
      result = encode(scratchEncode, scratchEncode.byteSize(), scratchEncodeLen)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("encoding returned $result")
    }
    val len = scratchEncodeLen.get(C_LONG, 0L)
    if (len <= 0L) {
      return ByteArray(0)
    }
    return scratchEncode.asSlice(0L, len).toArray(C_BYTE)
  }

  /** Stage [text] in [scratchKeyText] and point the key event at it; the event borrows the buffer until re-set. */
  private fun setKeyEventText(text: String) {
    val bytes = text.encodeToByteArray()
    if (bytes.size > scratchKeyText.byteSize()) {
      scratchKeyText = arena.allocate(bytes.size.toLong())
    }
    MemorySegment.copy(bytes, 0, scratchKeyText, C_BYTE, 0L, bytes.size)
    LibGhosttyVt.keyEventSetUtf8(keyEvent, scratchKeyText, bytes.size.toLong())
  }

  private fun modsBits(modifiers: Set<TerminalInputModifier>): Short {
    var bits = 0
    for (modifier in modifiers) {
      bits = bits or when (modifier) {
        TerminalInputModifier.SHIFT -> GhosttyMods.SHIFT
        TerminalInputModifier.CTRL -> GhosttyMods.CTRL
        TerminalInputModifier.ALT -> GhosttyMods.ALT
        TerminalInputModifier.SUPER -> GhosttyMods.SUPER
        TerminalInputModifier.CAPS_LOCK -> GhosttyMods.CAPS_LOCK
        TerminalInputModifier.NUM_LOCK -> GhosttyMods.NUM_LOCK
      }
    }
    return bits.toShort()
  }

  private fun TerminalKeyAction.toGhostty(): GhosttyKeyAction = when (this) {
    TerminalKeyAction.PRESS -> GhosttyKeyAction.PRESS
    TerminalKeyAction.RELEASE -> GhosttyKeyAction.RELEASE
    TerminalKeyAction.REPEAT -> GhosttyKeyAction.REPEAT
  }

  private fun TerminalMouseAction.toGhostty(): GhosttyMouseAction = when (this) {
    TerminalMouseAction.PRESS -> GhosttyMouseAction.PRESS
    TerminalMouseAction.RELEASE -> GhosttyMouseAction.RELEASE
    TerminalMouseAction.MOTION -> GhosttyMouseAction.MOTION
  }

  private fun TerminalMouseButton.toGhostty(): GhosttyMouseButton = when (this) {
    TerminalMouseButton.LEFT -> GhosttyMouseButton.LEFT
    TerminalMouseButton.RIGHT -> GhosttyMouseButton.RIGHT
    TerminalMouseButton.MIDDLE -> GhosttyMouseButton.MIDDLE
    TerminalMouseButton.WHEEL_UP -> GhosttyMouseButton.FOUR
    TerminalMouseButton.WHEEL_DOWN -> GhosttyMouseButton.FIVE
  }

  // ---- reading the grid ----

  override fun screenLine(row: Int): TerminalRow = buildRow(GhosttyPointTag.ACTIVE, row)

  override fun scrollbackLine(row: Int): TerminalRow = buildRow(GhosttyPointTag.HISTORY, row)

  private fun buildRow(pointTag: GhosttyPointTag, y: Int): TerminalRow {
    ensureOpen()
    val width = terminalGetU16(GhosttyTerminalData.COLS)
    val cells = ArrayList<Cell>(width)
    for (x in 0 until width) {
      cells.add(if (readCell(pointTag, x, y, scratchCellData)) scratchCellData.toCell() else Cell.Empty)
    }
    return TerminalRow(cells, wrapped = readRowWrapped(pointTag, y))
  }

  /**
   * Whether the row at ([pointTag], [y]) soft-wraps into the next one (`GHOSTTY_ROW_DATA_WRAP`); false when
   * the row is out of bounds. Read from the row behind the grid ref of its first cell.
   */
  private fun readRowWrapped(pointTag: GhosttyPointTag, y: Int): Boolean {
    scratchGridRef.fill(0.toByte())
    scratchGridRef.set(C_LONG, 0L, GRID_REF.byteSize()) // GHOSTTY_INIT_SIZED
    scratchPoint.fill(0.toByte())
    scratchPoint.set(C_INT, POINT_OFF_TAG, pointTag.code)
    scratchPoint.set(C_SHORT, POINT_OFF_X, 0)
    scratchPoint.set(C_INT, POINT_OFF_Y, y)
    try {
      if (LibGhosttyVt.terminalGridRef(terminal, scratchPoint, scratchGridRef) != GhosttyResult.SUCCESS) {
        return false // INVALID_VALUE: the row is out of bounds
      }
      val rowRead = LibGhosttyVt.gridRefRow(scratchGridRef, scratchRow)
      if (rowRead != GhosttyResult.SUCCESS) {
        logGridReadFailure("ghostty_grid_ref_row", rowRead)
        return false
      }
      scratchOut.set(C_BYTE, 0L, 0)
      val read = LibGhosttyVt.rowGet(scratchRow.get(C_LONG, 0L), GhosttyRowData.WRAP.code, scratchOut)
      if (read != GhosttyResult.SUCCESS) {
        logGridReadFailure("ghostty_row_get(WRAP)", read)
        return false
      }
      return scratchOut.get(C_BYTE, 0L).toInt() != 0
    } catch (t: Throwable) {
      throw RuntimeException("ghostty row read failed", t)
    }
  }

  // ---- change tracking ----

  override fun takeChanges(): ScreenChange {
    ensureOpen()
    try {
      if (LibGhosttyVt.renderStateUpdate(renderState, terminal) != GhosttyResult.SUCCESS) {
        return ScreenChange.All // conservative: repaint everything if the delta can't be read
      }
      scratchOut.set(C_INT, 0L, 0)
      if (LibGhosttyVt.renderStateGet(renderState, GhosttyRenderStateData.DIRTY.code, scratchOut) != GhosttyResult.SUCCESS) {
        return ScreenChange.All
      }
      val dirty = GhosttyRenderStateDirty.of(scratchOut.get(C_INT, 0L))
      if (dirty == GhosttyRenderStateDirty.FALSE) {
        return ScreenChange.None
      }
      // The global and per-row dirty layers are independent (render.h: resetting one does not reset the
      // other), so always drain the per-row flags — even on a FULL frame — so a later PARTIAL frame does
      // not re-report rows this frame already covered.
      val changedRows = collectAndClearDirtyRows()
      scratchOut.set(C_INT, 0L, GhosttyRenderStateDirty.FALSE.code)
      LibGhosttyVt.renderStateSet(renderState, GhosttyRenderStateOption.DIRTY.code, scratchOut)
      return when {
        dirty == GhosttyRenderStateDirty.FULL -> ScreenChange.All
        changedRows.isEmpty() -> ScreenChange.None
        else -> ScreenChange.Rows(changedRows)
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty render-state dirty poll failed", t)
    }
  }

  override fun markHistoryBoundary(): HistoryMark {
    ensureOpen()
    return GhosttyHistoryMark()
  }

  override fun close() {
    if (closed) {
      return
    }
    closed = true
    var failure: Throwable? = null
    // Each step is isolated so that one native free failing does not skip (and thereby leak) the rest.
    fun free(action: () -> Unit) {
      try {
        action()
      } catch (t: Throwable) {
        val current = failure
        if (current == null) failure = t else current.addSuppressed(t)
      }
    }
    free { LibGhosttyVt.keyEventFree(keyEvent) }
    free { LibGhosttyVt.keyEncoderFree(keyEncoder) }
    free { LibGhosttyVt.mouseEventFree(mouseEvent) }
    free { LibGhosttyVt.mouseEncoderFree(mouseEncoder) }
    free { LibGhosttyVt.renderStateRowIteratorFree(rowIterSlot.get(C_PTR, 0L)) }
    free { LibGhosttyVt.renderStateFree(renderState) }
    free { LibGhosttyVt.terminalFree(terminal) }
    free { arena.close() }
    failure?.let { throw RuntimeException("freeing ghostty native handles failed", it) }
  }

  // ---- ghostty internals (private) ----

  // Invoked from native code (the upcall stub) during ghostty_terminal_vt_write.
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onWritePty(terminal: MemorySegment, userdata: MemorySegment, data: MemorySegment, len: Long) {
    val l = listener
    if (l == null || len <= 0L) {
      return
    }
    l.onRespondToHost(data.reinterpret(len).toArray(C_BYTE))
  }

  // Invoked from native code (the upcall stub) when the program rings the bell (BEL, 0x07).
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onBell(terminal: MemorySegment, userdata: MemorySegment) {
    listener?.onBell()
  }

  /**
   * Invoked from native code (the upcall stub) when the program reports progress via OSC 9;4; [report]
   * points at a borrowed `GhosttyTerminalProgressReport` valid only for this call.
   *
   * Deliberately total: an unmodeled state or an out-of-range percentage is dropped or coerced rather than
   * raised, because an exception thrown out of an FFM upcall unwinds into ghostty's own stack frames.
   */
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onProgressReport(terminal: MemorySegment, userdata: MemorySegment, report: MemorySegment) {
    // A sized struct: only the fields covered by its own `size` may be read (terminal.h).
    val size = report.reinterpret(C_LONG.byteSize()).get(C_LONG, 0L)
    if (size < PROGRESS_REPORT_MIN_SIZE) {
      return
    }
    val struct = report.reinterpret(size)
    val state = GhosttyTerminalProgressState.of(struct.get(C_INT, PROGRESS_REPORT_OFF_STATE)) ?: return
    // `progress` is -1 when the program omitted (or malformed) the percentage, otherwise 0..100.
    val percent = struct.get(C_BYTE, PROGRESS_REPORT_OFF_PROGRESS).toInt().takeIf { it in 0..100 }
    progressReport = when (state) {
      GhosttyTerminalProgressState.REMOVE -> null
      GhosttyTerminalProgressState.SET -> TerminalProgress(TerminalProgressState.NORMAL, percent)
      GhosttyTerminalProgressState.ERROR -> TerminalProgress(TerminalProgressState.ERROR, percent)
      GhosttyTerminalProgressState.INDETERMINATE -> TerminalProgress(TerminalProgressState.INDETERMINATE, percent)
      GhosttyTerminalProgressState.PAUSE -> TerminalProgress(TerminalProgressState.PAUSED, percent)
    }
  }

  /**
   * Read the cursor's drawing shape from the render state. Calls `render_state_update` to sync the
   * snapshot from the terminal; per render.h `update` only *accumulates* dirty (it never resets it),
   * so this does not disturb [takeChanges]' pull-based dirty tracking.
   */
  private fun readCursorShape(): CursorShape {
    ensureOpen()
    try {
      LibGhosttyVt.renderStateUpdate(renderState, terminal)
      scratchOut.set(C_INT, 0L, 0)
      if (LibGhosttyVt.renderStateGet(renderState, GhosttyRenderStateData.CURSOR_VISUAL_STYLE.code, scratchOut) != GhosttyResult.SUCCESS) {
        return CursorShape.BLOCK
      }
      return when (GhosttyCursorVisualStyle.of(scratchOut.get(C_INT, 0L))) {
        GhosttyCursorVisualStyle.BAR -> CursorShape.BAR
        GhosttyCursorVisualStyle.UNDERLINE -> CursorShape.UNDERLINE
        // BLOCK, and BLOCK_HOLLOW (the unfocused-window render variant this headless engine never
        // emits), both surface as BLOCK; the embedder owns the hollow-on-unfocus decision.
        GhosttyCursorVisualStyle.BLOCK, GhosttyCursorVisualStyle.BLOCK_HOLLOW -> CursorShape.BLOCK
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty render-state cursor shape read failed", t)
    }
  }

  /**
   * Read whether the cursor should currently blink from the render state (a C `bool`). Ghostty
   * computes this from the terminal modes, folding in both DECSCUSR odd/even parity and DEC private
   * mode 12. Uses the same render-state sync as [readCursorShape]; `render_state_update` only
   * accumulates dirty, so pull-based [takeChanges] is unaffected.
   */
  private fun readCursorBlinking(): Boolean {
    ensureOpen()
    try {
      LibGhosttyVt.renderStateUpdate(renderState, terminal)
      scratchOut.set(C_BYTE, 0L, 0.toByte())
      if (LibGhosttyVt.renderStateGet(renderState, GhosttyRenderStateData.CURSOR_BLINKING.code, scratchOut) != GhosttyResult.SUCCESS) {
        return false
      }
      return scratchOut.get(C_BYTE, 0L).toInt() != 0
    } catch (t: Throwable) {
      throw RuntimeException("ghostty render-state cursor blink read failed", t)
    }
  }

  /**
   * Populate the reusable row iterator from the current render state, then walk the viewport rows
   * (index 0 = top of the active screen), collecting the indices whose per-row dirty flag is set and
   * clearing each as it is consumed. Returns the changed active-screen row indices.
   */
  private fun collectAndClearDirtyRows(): IntArray {
    if (LibGhosttyVt.renderStateGet(renderState, GhosttyRenderStateData.ROW_ITERATOR.code, rowIterSlot) != GhosttyResult.SUCCESS) {
      return IntArray(0)
    }
    val iterator = rowIterSlot.get(C_PTR, 0L)
    val changed = ArrayList<Int>()
    var index = 0
    while (LibGhosttyVt.renderStateRowIteratorNext(iterator)) {
      scratchOut.set(C_BYTE, 0L, 0.toByte())
      val rowDirty = LibGhosttyVt.renderStateRowGet(iterator, GhosttyRenderStateRowData.DIRTY.code, scratchOut) == GhosttyResult.SUCCESS &&
                     scratchOut.get(C_BYTE, 0L).toInt() != 0
      if (rowDirty) {
        changed.add(index)
        LibGhosttyVt.renderStateRowSet(iterator, GhosttyRenderStateRowOption.DIRTY.code, scratchFalse)
      }
      index++
    }
    return changed.toIntArray()
  }

  /**
   * A [HistoryMark] backed by a ghostty tracked grid reference (grid_ref_tracked.h) pinned at the top
   * of the active screen — the boundary between finalized scrollback and the live screen. The tracked
   * reference follows that content as the screen scrolls, so [finalizedLineCount] stays exact even
   * after the scrollback byte cap begins evicting, where the raw [scrollbackRows] delta would plateau.
   */
  private inner class GhosttyHistoryMark : HistoryMark {
    // A mark can be closed (its native ref freed) independently of the emulator, so it owns its own
    // arena + scratch rather than borrowing the emulator's shared one.
    private val markArena: Arena = Arena.ofShared()
    private val markPoint: MemorySegment = markArena.allocate(POINT)      // reused for track / set
    private val markCoord: MemorySegment = markArena.allocate(POINT_COORD)
    private val trackedRef: MemorySegment
    private var markClosed = false

    init {
      writeActiveTop(markPoint)
      val outRef = markArena.allocate(C_PTR)
      val r = try {
        LibGhosttyVt.terminalGridRefTrack(terminal, markPoint, outRef)
      } catch (t: Throwable) {
        markArena.close()
        throw RuntimeException("ghostty_terminal_grid_ref_track failed", t)
      }
      if (r != GhosttyResult.SUCCESS) {
        markArena.close()
        throw IllegalStateException("ghostty_terminal_grid_ref_track returned $r")
      }
      trackedRef = outRef.get(C_PTR, 0L)
    }

    override fun finalizedLineCount(): Int {
      ensureOpen()
      ensureMarkOpen()
      val r = try {
        LibGhosttyVt.trackedGridRefPoint(trackedRef, GhosttyPointTag.SCREEN.code, markCoord)
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_tracked_grid_ref_point failed", t)
      }
      if (r == GhosttyResult.NO_VALUE) {
        return -1 // the pinned boundary line has been evicted from the bounded scrollback
      }
      if (r != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_tracked_grid_ref_point returned $r")
      }
      // SCREEN y is the pinned line's absolute row: history occupies rows [0, scrollbackRows), then the
      // active screen. At anchor time the top active row is at y == scrollbackRows, so the number of
      // lines finalized since is the amount that absolute row has moved above the current boundary.
      val screenY = markCoord.get(C_INT, POINT_COORD_OFF_Y)
      return scrollbackRows - screenY
    }

    override fun reset() {
      ensureOpen()
      ensureMarkOpen()
      writeActiveTop(markPoint)
      val r = try {
        LibGhosttyVt.trackedGridRefSet(trackedRef, terminal, markPoint)
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_tracked_grid_ref_set failed", t)
      }
      if (r != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_tracked_grid_ref_set returned $r")
      }
    }

    override fun close() {
      if (markClosed) {
        return
      }
      markClosed = true
      // Freeing the tracked ref is safe even after the terminal is freed (grid_ref_tracked.h), so this
      // works regardless of whether the emulator was closed first.
      try {
        LibGhosttyVt.trackedGridRefFree(trackedRef)
      } catch (t: Throwable) {
        throw RuntimeException("ghostty_tracked_grid_ref_free failed", t)
      } finally {
        markArena.close()
      }
    }

    private fun ensureMarkOpen() {
      if (markClosed) {
        throw IllegalStateException("HistoryMark is closed")
      }
    }

    /** Fill [seg] (a `GhosttyPoint`) with the top-left cell of the active screen: `ACTIVE (0, 0)`. */
    private fun writeActiveTop(seg: MemorySegment) {
      seg.fill(0.toByte())
      seg.set(C_INT, POINT_OFF_TAG, GhosttyPointTag.ACTIVE.code)
      seg.set(C_SHORT, POINT_OFF_X, 0.toShort())
      seg.set(C_INT, POINT_OFF_Y, 0)
    }
  }

  private fun modeEnabled(mode: GhosttyMode): Boolean {
    ensureOpen()
    scratchOut.set(C_BYTE, 0L, 0.toByte())
    try {
      val r = LibGhosttyVt.terminalModeGet(terminal, mode.packed.toShort(), scratchOut)
      // Unknown modes return GHOSTTY_INVALID_VALUE; treat as "not enabled".
      return r == GhosttyResult.SUCCESS && scratchOut.get(C_BYTE, 0L).toInt() != 0
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_mode_get failed", t)
    }
  }

  private fun readTitle(): String {
    ensureOpen()
    scratchOut.fill(0.toByte()) // GhosttyString { const uint8_t* ptr; size_t len; }
    try {
      val r = LibGhosttyVt.terminalGet(terminal, GhosttyTerminalData.TITLE.code, scratchOut)
      if (r != GhosttyResult.SUCCESS) {
        return ""
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get(TITLE) failed", t)
    }
    val ptr = scratchOut.get(C_LONG, 0L)
    val len = scratchOut.get(C_LONG, 8L)
    if (ptr == 0L || len <= 0L) {
      return ""
    }
    val bytes = MemorySegment.ofAddress(ptr).reinterpret(len).toArray(C_BYTE)
    return String(bytes, StandardCharsets.UTF_8)
  }

  /**
   * Resolve the cell at ([pointTag], x, y) into [out]; false when the point is out of
   * bounds, or (reported via [logGridReadFailure]) when the engine fails to read the
   * resolved cell.
   */
  private fun readCell(pointTag: GhosttyPointTag, x: Int, y: Int, out: CellData): Boolean {
    ensureOpen()
    out.reset()
    scratchGridRef.fill(0.toByte())
    scratchGridRef.set(C_LONG, 0L, GRID_REF.byteSize()) // GHOSTTY_INIT_SIZED
    scratchPoint.fill(0.toByte())
    scratchPoint.set(C_INT, POINT_OFF_TAG, pointTag.code)
    scratchPoint.set(C_SHORT, POINT_OFF_X, x.toShort())
    scratchPoint.set(C_INT, POINT_OFF_Y, y)
    try {
      if (LibGhosttyVt.terminalGridRef(terminal, scratchPoint, scratchGridRef) != GhosttyResult.SUCCESS) {
        return false // INVALID_VALUE: the point is out of bounds
      }
      val cellRead = LibGhosttyVt.gridRefCell(scratchGridRef, scratchCell)
      if (cellRead != GhosttyResult.SUCCESS) {
        logGridReadFailure("ghostty_grid_ref_cell", cellRead)
        return false
      }
      val cell = scratchCell.get(C_LONG, 0L)
      out.codepoint = cellGetInt(cell, GhosttyCellData.CODEPOINT) ?: return false
      out.wide = GhosttyCellWide.of(cellGetInt(cell, GhosttyCellData.WIDE) ?: return false)
      out.combining = readGraphemeCombining()

      scratchStyle.fill(0.toByte())
      scratchStyle.set(C_LONG, 0L, STYLE_SIZE) // GHOSTTY_INIT_SIZED
      val styleRead = LibGhosttyVt.gridRefStyle(scratchGridRef, scratchStyle)
      if (styleRead == GhosttyResult.SUCCESS) {
        out.fgTag = GhosttyStyleColorTag.of(scratchStyle.get(C_INT, STYLE_OFF_FG_TAG))
        out.fg = readColorValue(out.fgTag, STYLE_OFF_FG_VAL)
        out.bgTag = GhosttyStyleColorTag.of(scratchStyle.get(C_INT, STYLE_OFF_BG_TAG))
        out.bg = readColorValue(out.bgTag, STYLE_OFF_BG_VAL)
        out.bold = scratchStyle.get(C_BYTE, STYLE_OFF_BOLD).toInt() != 0
        out.italic = scratchStyle.get(C_BYTE, STYLE_OFF_ITALIC).toInt() != 0
        out.faint = scratchStyle.get(C_BYTE, STYLE_OFF_FAINT).toInt() != 0
        out.blink = scratchStyle.get(C_BYTE, STYLE_OFF_BLINK).toInt() != 0
        out.inverse = scratchStyle.get(C_BYTE, STYLE_OFF_INVERSE).toInt() != 0
        out.invisible = scratchStyle.get(C_BYTE, STYLE_OFF_INVISIBLE).toInt() != 0
        out.underline = GhosttySgrUnderline.of(scratchStyle.get(C_INT, STYLE_OFF_UNDERLINE))
      }
      else {
        // the cell keeps the default style
        logGridReadFailure("ghostty_grid_ref_style", styleRead)
      }
      out.hyperlink = readHyperlinkUri()
      return true
    } catch (t: Throwable) {
      throw RuntimeException("ghostty cell read failed", t)
    }
  }

  /**
   * Records a grid read that failed where the C API contract permits no failure (the
   * grid ref was resolved successfully just before). The affected value falls back to
   * a safe default so one unreadable cell cannot take down the whole screen; logged
   * once per emulator because these reads run per cell on every repaint.
   */
  private fun logGridReadFailure(call: String, result: GhosttyResult) {
    if (!gridReadFailureLogged) {
      gridReadFailureLogged = true
      LOG.error("$call returned $result; affected reads fall back to defaults (reported once per emulator)")
    }
  }

  /** Read the current grid ref's OSC 8 hyperlink URI, or null if the cell carries no hyperlink. */
  private fun readHyperlinkUri(): String? {
    try {
      scratchUriLen.set(C_LONG, 0L, 0L)
      var r = LibGhosttyVt.gridRefHyperlinkUri(scratchGridRef, scratchUri, scratchUri.byteSize(), scratchUriLen)
      if (r == GhosttyResult.OUT_OF_SPACE) {
        scratchUri = arena.allocate(scratchUriLen.get(C_LONG, 0L))
        r = LibGhosttyVt.gridRefHyperlinkUri(scratchGridRef, scratchUri, scratchUri.byteSize(), scratchUriLen)
      }
      if (r != GhosttyResult.SUCCESS) {
        // a no-hyperlink cell is SUCCESS + len 0
        logGridReadFailure("ghostty_grid_ref_hyperlink_uri", r)
        return null
      }
      val len = scratchUriLen.get(C_LONG, 0L)
      if (len <= 0L) {
        return null
      }
      return String(scratchUri.reinterpret(len).toArray(C_BYTE), StandardCharsets.UTF_8)
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_grid_ref_hyperlink_uri failed", t)
    }
  }

  /**
   * The combining code points of the current grid ref's grapheme cluster: the code points after the
   * primary one (empty for a plain single-code-point cell, a spacer, or an empty cell). Grows
   * [scratchGraphemes] and retries on OUT_OF_SPACE.
   */
  private fun readGraphemeCombining(): List<Int> {
    try {
      scratchGraphemesLen.set(C_LONG, 0L, 0L)
      var capacity = scratchGraphemes.byteSize() / C_INT.byteSize()
      var r = LibGhosttyVt.gridRefGraphemes(scratchGridRef, scratchGraphemes, capacity, scratchGraphemesLen)
      if (r == GhosttyResult.OUT_OF_SPACE) {
        capacity = scratchGraphemesLen.get(C_LONG, 0L)
        scratchGraphemes = arena.allocate(capacity * C_INT.byteSize())
        r = LibGhosttyVt.gridRefGraphemes(scratchGridRef, scratchGraphemes, capacity, scratchGraphemesLen)
      }
      if (r != GhosttyResult.SUCCESS) {
        // a text-less cell is SUCCESS + len 0
        logGridReadFailure("ghostty_grid_ref_graphemes", r)
        return emptyList()
      }
      val count = scratchGraphemesLen.get(C_LONG, 0L)
      if (count <= 1L) {
        return emptyList() // just the primary code point, or an empty cell
      }
      val combining = ArrayList<Int>((count - 1).toInt())
      for (i in 1 until count.toInt()) {
        combining.add(scratchGraphemes.get(C_INT, i.toLong() * C_INT.byteSize()))
      }
      return combining
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_grid_ref_graphemes failed", t)
    }
  }

  /** For PALETTE: the index (0-255). For RGB: packed 0xRRGGBB. Else 0. */
  private fun readColorValue(tag: GhosttyStyleColorTag, valueOffset: Long): Int {
    if (tag == GhosttyStyleColorTag.PALETTE) {
      return scratchStyle.get(C_BYTE, valueOffset).toInt() and 0xFF
    }
    if (tag == GhosttyStyleColorTag.RGB) {
      val r = scratchStyle.get(C_BYTE, valueOffset).toInt() and 0xFF
      val g = scratchStyle.get(C_BYTE, valueOffset + 1).toInt() and 0xFF
      val b = scratchStyle.get(C_BYTE, valueOffset + 2).toInt() and 0xFF
      return (r shl 16) or (g shl 8) or b
    }
    return 0
  }

  /**
   * Read an int datum of the opaque [cell], or null when the engine rejects the read —
   * per the C contract only possible for a data kind the library does not know.
   */
  private fun cellGetInt(cell: Long, cellData: GhosttyCellData): Int? {
    scratchOut.set(C_INT, 0L, 0)
    try {
      val r = LibGhosttyVt.cellGet(cell, cellData.code, scratchOut)
      if (r != GhosttyResult.SUCCESS) {
        logGridReadFailure("ghostty_cell_get($cellData)", r)
        return null
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_cell_get failed", t)
    }
    return scratchOut.get(C_INT, 0L)
  }

  private fun terminalGetU16(dataKind: GhosttyTerminalData): Int {
    ensureOpen()
    scratchOut.set(C_SHORT, 0L, 0.toShort())
    try {
      val r = LibGhosttyVt.terminalGet(terminal, dataKind.code, scratchOut)
      if (r != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_terminal_get($dataKind) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_SHORT, 0L).toInt() and 0xFFFF
  }

  private fun terminalGetBool(dataKind: GhosttyTerminalData): Boolean {
    ensureOpen()
    scratchOut.set(C_BYTE, 0L, 0.toByte())
    try {
      val r = LibGhosttyVt.terminalGet(terminal, dataKind.code, scratchOut)
      if (r != GhosttyResult.SUCCESS) {
        return false
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_BYTE, 0L).toInt() != 0
  }

  private fun terminalGetSize(dataKind: GhosttyTerminalData): Long {
    ensureOpen()
    scratchOut.set(C_LONG, 0L, 0L)
    try {
      val r = LibGhosttyVt.terminalGet(terminal, dataKind.code, scratchOut)
      if (r != GhosttyResult.SUCCESS) {
        throw IllegalStateException("ghostty_terminal_get($dataKind) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_LONG, 0L)
  }

  /** Read an effective color (`GhosttyColorRgb` = 3 packed u8); null on `GHOSTTY_NO_VALUE` (unset). */
  private fun terminalGetRgb(dataKind: GhosttyTerminalData): TerminalColor.Rgb? {
    ensureOpen()
    scratchOut.fill(0.toByte())
    val r = try {
      LibGhosttyVt.terminalGet(terminal, dataKind.code, scratchOut)
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    if (r != GhosttyResult.SUCCESS) {
      return null
    }
    return TerminalColor.Rgb(
      scratchOut.get(C_BYTE, 0L).toInt() and 0xFF,
      scratchOut.get(C_BYTE, 1L).toInt() and 0xFF,
      scratchOut.get(C_BYTE, 2L).toInt() and 0xFF,
    )
  }

  private fun ensureOpen() {
    if (closed) {
      throw IllegalStateException("GhosttyTerminalEmulator is closed")
    }
  }

  private fun CellData.toCell(): Cell {
    val cellWidth = when (wide) {
      GhosttyCellWide.NARROW -> CellWidth.NARROW
      GhosttyCellWide.WIDE -> CellWidth.WIDE
      GhosttyCellWide.SPACER_TAIL, GhosttyCellWide.SPACER_HEAD -> CellWidth.SPACER
    }
    val style = CellStyle(
      foreground = toColor(fgTag, fg),
      background = toColor(bgTag, bg),
      bold = bold,
      faint = faint,
      italic = italic,
      blink = blink,
      inverse = inverse,
      hidden = invisible,
      underline = when (underline) {
        GhosttySgrUnderline.NONE -> Underline.NONE
        GhosttySgrUnderline.SINGLE -> Underline.SINGLE
        GhosttySgrUnderline.DOUBLE -> Underline.DOUBLE
        GhosttySgrUnderline.CURLY -> Underline.CURLY
        GhosttySgrUnderline.DOTTED -> Underline.DOTTED
        GhosttySgrUnderline.DASHED -> Underline.DASHED
      },
    )
    return Cell(codepoint, cellWidth, style, hyperlink, combining)
  }

  // ANSI colors (0..15) surface as IndexedAnsi for the embedder to theme; extended palette colors
  // (16..255) surface as IndexedExtended, a live reference the embedder resolves via paletteColor()
  // so palette (OSC 4) changes are always reflected instead of being frozen onto a cell.
  private fun toColor(tag: GhosttyStyleColorTag, value: Int): TerminalColor = when (tag) {
    GhosttyStyleColorTag.PALETTE ->
      if (value < 16) TerminalColor.IndexedAnsi(value) else TerminalColor.IndexedExtended(value)
    GhosttyStyleColorTag.RGB -> TerminalColor.Rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
    GhosttyStyleColorTag.NONE -> TerminalColor.Default
  }

  /**
   * Refresh [paletteCache] from ghostty if a write since the last refresh may have changed the palette
   * (OSC 4 / 104). One bulk `terminal_get` + a Kotlin copy; afterwards lookups are pure Kotlin.
   */
  private fun ensurePaletteLoaded() {
    if (!paletteDirty) {
      return
    }
    try {
      if (LibGhosttyVt.terminalGet(terminal, GhosttyTerminalData.COLOR_PALETTE.code, scratchPalette) != GhosttyResult.SUCCESS) {
        return // keep the prior cache and retry on the next read
      }
      for (i in 0 until 256) {
        val offset = i.toLong() * 3L
        val r = scratchPalette.get(C_BYTE, offset).toInt() and 0xFF
        val g = scratchPalette.get(C_BYTE, offset + 1L).toInt() and 0xFF
        val b = scratchPalette.get(C_BYTE, offset + 2L).toInt() and 0xFF
        paletteCache[i] = (r shl 16) or (g shl 8) or b
      }
      paletteDirty = false
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get(COLOR_PALETTE) failed", t)
    }
  }

  /**
   * Resolve palette [index] (0..255) to RGB, refreshing the Kotlin cache first if a write since the
   * last refresh may have dirtied it. Reached only via [paletteColor], so a frame that never resolves
   * a palette color triggers no reload.
   */
  private fun paletteRgb(index: Int): TerminalColor.Rgb {
    ensurePaletteLoaded()
    val packed = paletteCache[index]
    return TerminalColor.Rgb((packed shr 16) and 0xFF, (packed shr 8) and 0xFF, packed and 0xFF)
  }

  /** Mutable, reusable holder for a ghostty cell's raw content + style (avoids per-cell allocation). */
  private class CellData {
    var codepoint = 0
    var wide: GhosttyCellWide = GhosttyCellWide.NARROW
    var combining: List<Int> = emptyList()
    var hyperlink: String? = null
    var fgTag: GhosttyStyleColorTag = GhosttyStyleColorTag.NONE
    var fg = 0 // palette index or packed 0xRRGGBB depending on fgTag
    var bgTag: GhosttyStyleColorTag = GhosttyStyleColorTag.NONE
    var bg = 0
    var bold = false
    var italic = false
    var faint = false
    var blink = false
    var inverse = false
    var invisible = false
    var underline: GhosttySgrUnderline = GhosttySgrUnderline.NONE

    fun reset() {
      codepoint = 0
      wide = GhosttyCellWide.NARROW
      combining = emptyList()
      hyperlink = null
      fgTag = GhosttyStyleColorTag.NONE
      fg = 0
      bgTag = GhosttyStyleColorTag.NONE
      bg = 0
      bold = false
      italic = false
      faint = false
      blink = false
      inverse = false
      invisible = false
      underline = GhosttySgrUnderline.NONE
    }
  }
}

/**
 * Capacity of the write buffer, and therefore the largest slice handed to the engine in one call. The
 * reworked terminal's read loop hands over at most 4096 chars per read, i.e. up to ~12 KB of UTF-8, so in
 * practice a write is a single chunk; anything larger is simply streamed through in several.
 */
private const val WRITE_BUFFER_BYTES: Long = 16L * 1024L

private val LOG: Logger = logger<GhosttyTerminalEmulator>()
