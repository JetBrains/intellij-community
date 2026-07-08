// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import com.intellij.terminal.emulator.impl.ghostty.GhosttyLog
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_BOOL
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_INT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_LONG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_PTR
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_SHORT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.POINT
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle

/**
 * Raw, hand-written FFM ("-sys") binding over the libghostty-vt C API: one Kotlin function per C
 * entry point, taking [MemorySegment]s and primitives that map 1:1 onto the C signatures. Functions
 * whose C return type is `GhosttyResult` surface it as the [GhosttyResult] enum (the only typing the
 * raw layer does); everything else stays primitive. It owns the [SymbolLookup] and the downcall
 * [MethodHandle]s but holds **no per-call state** — every function is allocation-free and the caller
 * supplies all memory.
 *
 * The shared library file is resolved by [LibGhosttyVtLocator].
 *
 * Only the slice of the C API the bridge currently needs is bound; the full API
 * (`ghostty/include/ghostty/vt/`, ~160 functions) is far larger.
 */
internal object LibGhosttyVt {

  /**
   * `ghostty_terminal_new`: create a [cols]x[rows] terminal (both must be non-zero); [outTerminal]
   * receives the `GhosttyTerminal` handle. Everything else — the scrollback limits included — starts at
   * ghostty's defaults and is configured afterwards via [terminalSet].
   */
  fun terminalNew(allocator: MemorySegment, outTerminal: MemorySegment, cols: Short, rows: Short): GhosttyResult =
    GhosttyResult.of(TERMINAL_NEW.invokeExact(allocator, outTerminal, cols, rows) as Int)

  /** `ghostty_terminal_free`: destroy a terminal. */
  fun terminalFree(terminal: MemorySegment) {
    TERMINAL_FREE.invokeExact(terminal)
  }

  /** `ghostty_terminal_vt_write`: feed [len] raw VT bytes from [data] (equivalent to PTY output). */
  fun terminalVtWrite(terminal: MemorySegment, data: MemorySegment, len: Long) {
    TERMINAL_VT_WRITE.invokeExact(terminal, data, len)
  }

  /** `ghostty_terminal_resize`: resize to [cols]x[rows]; cell pixel size is irrelevant for reflow. */
  fun terminalResize(terminal: MemorySegment, cols: Short, rows: Short, cellWidthPx: Int, cellHeightPx: Int): GhosttyResult =
    GhosttyResult.of(TERMINAL_RESIZE.invokeExact(terminal, cols, rows, cellWidthPx, cellHeightPx) as Int)

  /** `ghostty_terminal_get`: read a [GhosttyTerminalData] scalar/string into [out]. */
  fun terminalGet(terminal: MemorySegment, dataKind: Int, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(TERMINAL_GET.invokeExact(terminal, dataKind, out) as Int)

  /** `ghostty_terminal_grid_ref`: resolve [point] to a `GhosttyGridRef` in [outRef]. */
  fun terminalGridRef(terminal: MemorySegment, point: MemorySegment, outRef: MemorySegment): GhosttyResult =
    GhosttyResult.of(TERMINAL_GRID_REF.invokeExact(terminal, point, outRef) as Int)

  /** `ghostty_grid_ref_cell`: fetch the `GhosttyCell` (an opaque u64) referenced by [gridRef]. */
  fun gridRefCell(gridRef: MemorySegment, outCell: MemorySegment): GhosttyResult =
    GhosttyResult.of(GRID_REF_CELL.invokeExact(gridRef, outCell) as Int)

  /** `ghostty_grid_ref_style`: fetch the `GhosttyStyle` referenced by [gridRef] into [outStyle]. */
  fun gridRefStyle(gridRef: MemorySegment, outStyle: MemorySegment): GhosttyResult =
    GhosttyResult.of(GRID_REF_STYLE.invokeExact(gridRef, outStyle) as Int)

  /**
   * `ghostty_grid_ref_hyperlink_uri`: write the cell's OSC 8 URI bytes into [buf] (capacity [bufLen]);
   * [outLen] receives the byte length (0 when the cell has no hyperlink, or the required size on
   * `OUT_OF_SPACE`).
   */
  fun gridRefHyperlinkUri(gridRef: MemorySegment, buf: MemorySegment, bufLen: Long, outLen: MemorySegment): GhosttyResult =
    GhosttyResult.of(GRID_REF_HYPERLINK_URI.invokeExact(gridRef, buf, bufLen, outLen) as Int)

  /**
   * `ghostty_grid_ref_graphemes`: write the cell's grapheme-cluster code points (the primary code point
   * followed by any combining code points) into [buf] (capacity [bufLen] `uint32_t` elements); [outLen]
   * receives the code-point count (0 when the cell has no text, or the required size on `OUT_OF_SPACE`).
   */
  fun gridRefGraphemes(gridRef: MemorySegment, buf: MemorySegment, bufLen: Long, outLen: MemorySegment): GhosttyResult =
    GhosttyResult.of(GRID_REF_GRAPHEMES.invokeExact(gridRef, buf, bufLen, outLen) as Int)

  /** `ghostty_grid_ref_row`: fetch the `GhosttyRow` (an opaque u64) whose row [gridRef] points into. */
  fun gridRefRow(gridRef: MemorySegment, outRow: MemorySegment): GhosttyResult =
    GhosttyResult.of(GRID_REF_ROW.invokeExact(gridRef, outRow) as Int)

  /** `ghostty_cell_get`: read a [GhosttyCellData] field of the opaque [cell] into [out]. */
  fun cellGet(cell: Long, cellData: Int, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(CELL_GET.invokeExact(cell, cellData, out) as Int)

  /** `ghostty_row_get`: read a [GhosttyRowData] field of the opaque [row] into [out]. */
  fun rowGet(row: Long, rowData: Int, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(ROW_GET.invokeExact(row, rowData, out) as Int)

  /** `ghostty_terminal_mode_get`: query a (packed) [GhosttyMode]; [out] receives a bool byte. */
  fun terminalModeGet(terminal: MemorySegment, packedMode: Short, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(TERMINAL_MODE_GET.invokeExact(terminal, packedMode, out) as Int)

  /** `ghostty_terminal_set`: set a [GhosttyTerminalOption] (e.g. install the WRITE_PTY effect). */
  fun terminalSet(terminal: MemorySegment, option: Int, value: MemorySegment): GhosttyResult =
    GhosttyResult.of(TERMINAL_SET.invokeExact(terminal, option, value) as Int)

  // ---- render state (render.h): incremental dirty tracking of the viewport ----

  /** `ghostty_render_state_new`: create a render state; [outState] receives the handle. */
  fun renderStateNew(allocator: MemorySegment, outState: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_NEW.invokeExact(allocator, outState) as Int)

  /** `ghostty_render_state_free`: destroy a render state. */
  fun renderStateFree(state: MemorySegment) {
    RENDER_STATE_FREE.invokeExact(state)
  }

  /** `ghostty_render_state_update`: refresh [state] from [terminal], consuming its dirty state. */
  fun renderStateUpdate(state: MemorySegment, terminal: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_UPDATE.invokeExact(state, terminal) as Int)

  /** `ghostty_render_state_get`: read a [GhosttyRenderStateData] value into [out]. */
  fun renderStateGet(state: MemorySegment, dataKind: Int, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_GET.invokeExact(state, dataKind, out) as Int)

  /** `ghostty_render_state_set`: set a [GhosttyRenderStateOption] from the value at [value]. */
  fun renderStateSet(state: MemorySegment, option: Int, value: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_SET.invokeExact(state, option, value) as Int)

  /**
   * `ghostty_render_state_row_iterator_new`: create a reusable row iterator; [outIterator] receives the
   * handle. Populate it for the current frame via [renderStateGet] with `GhosttyRenderStateData.ROW_ITERATOR`.
   */
  fun renderStateRowIteratorNew(allocator: MemorySegment, outIterator: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_ROW_ITERATOR_NEW.invokeExact(allocator, outIterator) as Int)

  /** `ghostty_render_state_row_iterator_free`: destroy a row iterator. */
  fun renderStateRowIteratorFree(iterator: MemorySegment) {
    RENDER_STATE_ROW_ITERATOR_FREE.invokeExact(iterator)
  }

  /** `ghostty_render_state_row_iterator_next`: advance to the next row; `false` once past the last row. */
  fun renderStateRowIteratorNext(iterator: MemorySegment): Boolean =
    RENDER_STATE_ROW_ITERATOR_NEXT.invokeExact(iterator) as Boolean

  /** `ghostty_render_state_row_get`: read a [GhosttyRenderStateRowData] value for the current row into [out]. */
  fun renderStateRowGet(iterator: MemorySegment, dataKind: Int, out: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_ROW_GET.invokeExact(iterator, dataKind, out) as Int)

  /** `ghostty_render_state_row_set`: set a [GhosttyRenderStateRowOption] for the current row from [value]. */
  fun renderStateRowSet(iterator: MemorySegment, option: Int, value: MemorySegment): GhosttyResult =
    GhosttyResult.of(RENDER_STATE_ROW_SET.invokeExact(iterator, option, value) as Int)

  // ---- tracked grid references (grid_ref_tracked.h): stable, scroll-surviving row identity ----

  /**
   * `ghostty_terminal_grid_ref_track`: create a tracked grid reference pinned at [point] (a `GhosttyPoint`
   * passed by value); [outRef] receives the `GhosttyTrackedGridRef` handle, which must be freed with
   * [trackedGridRefFree]. Unlike a plain grid ref, a tracked ref follows its content as the screen scrolls.
   */
  fun terminalGridRefTrack(terminal: MemorySegment, point: MemorySegment, outRef: MemorySegment): GhosttyResult =
    GhosttyResult.of(TERMINAL_GRID_REF_TRACK.invokeExact(terminal, point, outRef) as Int)

  /** `ghostty_tracked_grid_ref_free`: destroy a tracked grid reference. */
  fun trackedGridRefFree(ref: MemorySegment) {
    TRACKED_GRID_REF_FREE.invokeExact(ref)
  }

  /**
   * `ghostty_tracked_grid_ref_point`: convert [ref] to a point in coordinate space [tag] ([GhosttyPointTag]),
   * writing the `GhosttyPointCoordinate` into [outCoord]. Returns `GHOSTTY_NO_VALUE` once the tracked location
   * has been discarded (evicted from scrollback, or the owning terminal freed) or cannot be represented in [tag].
   */
  fun trackedGridRefPoint(ref: MemorySegment, tag: Int, outCoord: MemorySegment): GhosttyResult =
    GhosttyResult.of(TRACKED_GRID_REF_POINT.invokeExact(ref, tag, outCoord) as Int)

  /** `ghostty_tracked_grid_ref_set`: move [ref] to track [point] (a `GhosttyPoint` by value) on [terminal]. */
  fun trackedGridRefSet(ref: MemorySegment, terminal: MemorySegment, point: MemorySegment): GhosttyResult =
    GhosttyResult.of(TRACKED_GRID_REF_SET.invokeExact(ref, terminal, point) as Int)

  // ---- key encoding (key/encoder.h, key/event.h): key events -> PTY escape sequences ----

  /** `ghostty_key_encoder_new`: create a key encoder; [outEncoder] receives the handle. */
  fun keyEncoderNew(allocator: MemorySegment, outEncoder: MemorySegment): GhosttyResult =
    GhosttyResult.of(KEY_ENCODER_NEW.invokeExact(allocator, outEncoder) as Int)

  /** `ghostty_key_encoder_free`: destroy a key encoder. */
  fun keyEncoderFree(encoder: MemorySegment) {
    KEY_ENCODER_FREE.invokeExact(encoder)
  }

  /** `ghostty_key_encoder_setopt_from_terminal`: load the encoder options from [terminal]'s current modes. */
  fun keyEncoderSetoptFromTerminal(encoder: MemorySegment, terminal: MemorySegment) {
    KEY_ENCODER_SETOPT_FROM_TERMINAL.invokeExact(encoder, terminal)
  }

  /**
   * `ghostty_key_encoder_encode`: encode [event] into [outBuf] (capacity [outBufSize]); [outLen] receives
   * the byte count (0 when the event produces nothing, or the required size on `OUT_OF_SPACE`).
   */
  fun keyEncoderEncode(encoder: MemorySegment, event: MemorySegment, outBuf: MemorySegment, outBufSize: Long, outLen: MemorySegment): GhosttyResult =
    GhosttyResult.of(KEY_ENCODER_ENCODE.invokeExact(encoder, event, outBuf, outBufSize, outLen) as Int)

  /** `ghostty_key_event_new`: create a reusable key event; [outEvent] receives the handle. */
  fun keyEventNew(allocator: MemorySegment, outEvent: MemorySegment): GhosttyResult =
    GhosttyResult.of(KEY_EVENT_NEW.invokeExact(allocator, outEvent) as Int)

  /** `ghostty_key_event_free`: destroy a key event. */
  fun keyEventFree(event: MemorySegment) {
    KEY_EVENT_FREE.invokeExact(event)
  }

  /** `ghostty_key_event_set_action`: set the [GhosttyKeyAction]. */
  fun keyEventSetAction(event: MemorySegment, action: Int) {
    KEY_EVENT_SET_ACTION.invokeExact(event, action)
  }

  /** `ghostty_key_event_set_key`: set the physical key code. */
  fun keyEventSetKey(event: MemorySegment, key: Int) {
    KEY_EVENT_SET_KEY.invokeExact(event, key)
  }

  /** `ghostty_key_event_set_mods`: set the [GhosttyMods] bitmask. */
  fun keyEventSetMods(event: MemorySegment, mods: Short) {
    KEY_EVENT_SET_MODS.invokeExact(event, mods)
  }

  /** `ghostty_key_event_set_utf8`: set the layout-produced text; the event borrows [utf8] until re-set. */
  fun keyEventSetUtf8(event: MemorySegment, utf8: MemorySegment, len: Long) {
    KEY_EVENT_SET_UTF8.invokeExact(event, utf8, len)
  }

  /** `ghostty_key_event_set_unshifted_codepoint`: set the unmodified code point of the key. */
  fun keyEventSetUnshiftedCodepoint(event: MemorySegment, codepoint: Int) {
    KEY_EVENT_SET_UNSHIFTED_CODEPOINT.invokeExact(event, codepoint)
  }

  /** `ghostty_key_event_set_composing`: set whether an IME composition is in progress. */
  fun keyEventSetComposing(event: MemorySegment, composing: Boolean) {
    KEY_EVENT_SET_COMPOSING.invokeExact(event, composing)
  }

  // ---- mouse encoding (mouse/encoder.h, mouse/event.h): mouse events -> PTY escape sequences ----

  /** `ghostty_mouse_encoder_new`: create a mouse encoder; [outEncoder] receives the handle. */
  fun mouseEncoderNew(allocator: MemorySegment, outEncoder: MemorySegment): GhosttyResult =
    GhosttyResult.of(MOUSE_ENCODER_NEW.invokeExact(allocator, outEncoder) as Int)

  /** `ghostty_mouse_encoder_free`: destroy a mouse encoder. */
  fun mouseEncoderFree(encoder: MemorySegment) {
    MOUSE_ENCODER_FREE.invokeExact(encoder)
  }

  /** `ghostty_mouse_encoder_setopt`: set a [GhosttyMouseEncoderOption] from the value at [value]. */
  fun mouseEncoderSetopt(encoder: MemorySegment, option: Int, value: MemorySegment) {
    MOUSE_ENCODER_SETOPT.invokeExact(encoder, option, value)
  }

  /** `ghostty_mouse_encoder_setopt_from_terminal`: load tracking mode and format from [terminal]. */
  fun mouseEncoderSetoptFromTerminal(encoder: MemorySegment, terminal: MemorySegment) {
    MOUSE_ENCODER_SETOPT_FROM_TERMINAL.invokeExact(encoder, terminal)
  }

  /**
   * `ghostty_mouse_encoder_encode`: encode [event] into [outBuf] (capacity [outBufSize]); [outLen] receives
   * the byte count (0 when the event is not reported, or the required size on `OUT_OF_SPACE`).
   */
  fun mouseEncoderEncode(encoder: MemorySegment, event: MemorySegment, outBuf: MemorySegment, outBufSize: Long, outLen: MemorySegment): GhosttyResult =
    GhosttyResult.of(MOUSE_ENCODER_ENCODE.invokeExact(encoder, event, outBuf, outBufSize, outLen) as Int)

  /** `ghostty_mouse_event_new`: create a reusable mouse event; [outEvent] receives the handle. */
  fun mouseEventNew(allocator: MemorySegment, outEvent: MemorySegment): GhosttyResult =
    GhosttyResult.of(MOUSE_EVENT_NEW.invokeExact(allocator, outEvent) as Int)

  /** `ghostty_mouse_event_free`: destroy a mouse event. */
  fun mouseEventFree(event: MemorySegment) {
    MOUSE_EVENT_FREE.invokeExact(event)
  }

  /** `ghostty_mouse_event_set_action`: set the [GhosttyMouseAction]. */
  fun mouseEventSetAction(event: MemorySegment, action: Int) {
    MOUSE_EVENT_SET_ACTION.invokeExact(event, action)
  }

  /** `ghostty_mouse_event_set_button`: set the [GhosttyMouseButton]. */
  fun mouseEventSetButton(event: MemorySegment, button: Int) {
    MOUSE_EVENT_SET_BUTTON.invokeExact(event, button)
  }

  /** `ghostty_mouse_event_clear_button`: mark the event as carrying no button (pure motion). */
  fun mouseEventClearButton(event: MemorySegment) {
    MOUSE_EVENT_CLEAR_BUTTON.invokeExact(event)
  }

  /** `ghostty_mouse_event_set_mods`: set the [GhosttyMods] bitmask. */
  fun mouseEventSetMods(event: MemorySegment, mods: Short) {
    MOUSE_EVENT_SET_MODS.invokeExact(event, mods)
  }

  /** `ghostty_mouse_event_set_position`: set the surface position ([position] is a `GhosttyMousePosition` by value). */
  fun mouseEventSetPosition(event: MemorySegment, position: MemorySegment) {
    MOUSE_EVENT_SET_POSITION.invokeExact(event, position)
  }

  // ---- sys interface (sys.h): process-global, runtime-swappable hooks ----

  /**
   * `ghostty_sys_set`: install a process-global sys hook (e.g. the log callback). [value] is the raw
   * value for the option — for callback options it is the upcall-stub function pointer itself (not a
   * pointer to it), or [MemorySegment.NULL] to clear the hook.
   */
  fun sysSet(option: Int, value: MemorySegment): GhosttyResult =
    GhosttyResult.of(SYS_SET.invokeExact(option, value) as Int)

  /**
   * `ghostty_type_json`: the JSON description of every C API struct's size and field
   * offsets, as compiled into the loaded library. This is what the hand-written geometry
   * in [GhosttyLayouts] must match; `GhosttyLayoutsTest` checks it. Cold diagnostic call —
   * unlike the rest of this binding it allocates, decoding the returned static C string.
   */
  fun typeJson(): String {
    val ptr = TYPE_JSON.invokeExact() as MemorySegment
    return ptr.reinterpret(Long.MAX_VALUE).getString(0)
  }

  /**
   * Bind [handle] as a native upcall stub for the WRITE_PTY effect callback
   * `void(terminal, userdata, data, len)`, scoped to [arena]. The bound method's signature must be
   * `(MemorySegment, MemorySegment, MemorySegment, long) -> void`.
   */
  fun writePtyUpcallStub(handle: MethodHandle, arena: Arena): MemorySegment =
    LINKER.upcallStub(handle, WRITE_PTY_DESC, arena)

  /**
   * Bind [handle] as a native upcall stub for the BELL effect callback `void(terminal, userdata)`,
   * scoped to [arena]. The bound method's signature must be `(MemorySegment, MemorySegment) -> void`.
   */
  fun bellUpcallStub(handle: MethodHandle, arena: Arena): MemorySegment =
    LINKER.upcallStub(handle, BELL_DESC, arena)

  /**
   * Bind [handle] as a native upcall stub for the PROGRESS_REPORT effect callback
   * `void(terminal, userdata, report)`, scoped to [arena]. The bound method's signature must be
   * `(MemorySegment, MemorySegment, MemorySegment) -> void`; `report` points at a (borrowed, sized)
   * `GhosttyTerminalProgressReport`.
   */
  fun progressReportUpcallStub(handle: MethodHandle, arena: Arena): MemorySegment =
    LINKER.upcallStub(handle, PROGRESS_REPORT_DESC, arena)

  /**
   * Bind [handle] as a native upcall stub for the sys log callback
   * `void(userdata, level, scope, scope_len, message, message_len)`. The bound method's signature
   * must be `(MemorySegment, int, MemorySegment, long, MemorySegment, long) -> void`.
   *
   * The stub is scoped to [LIB_ARENA] (the library's process-lifetime arena) because the log hook is
   * process-global and set once: it must outlive any individual terminal (whose [Arena] is closed on
   * `close()`).
   */
  fun logCallbackUpcallStub(handle: MethodHandle): MemorySegment =
    LINKER.upcallStub(handle, LOG_CB_DESC, LIB_ARENA)

  // ---- linker / lookup / downcall handles ----
  private val LINKER: Linker = Linker.nativeLinker()
  private val LIB_ARENA: Arena = Arena.ofShared()

  // Lazy: a missing/unsupported native library must fail as a normal, retryable exception at first
  // real use. Failing eagerly here would fail this object's class initializer instead, which the JVM
  // never retries — every later reference would throw `NoClassDefFoundError` for the rest of the process.
  private val LOOKUP: SymbolLookup by lazy { openLibrary() }

  private val WRITE_PTY_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_PTR, C_LONG)

  // GhosttyTerminalBellFn: void(terminal, userdata).
  private val BELL_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(C_PTR, C_PTR)

  // GhosttyTerminalProgressReportFn: void(terminal, userdata, const GhosttyTerminalProgressReport*).
  private val PROGRESS_REPORT_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_PTR)

  // GhosttySysLogFn: void(userdata, level, scope, scope_len, message, message_len); size_t -> C_LONG.
  private val LOG_CB_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(C_PTR, C_INT, C_PTR, C_LONG, C_PTR, C_LONG)

  // Lazy for the same reason as [LOOKUP], since resolving any of these forces it.
  private val TERMINAL_NEW: MethodHandle by lazy { downcall("ghostty_terminal_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, C_SHORT, C_SHORT)) }
  private val TERMINAL_FREE: MethodHandle by lazy { downcall("ghostty_terminal_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val TERMINAL_VT_WRITE: MethodHandle by lazy { downcall("ghostty_terminal_vt_write",
    FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_LONG)) }
  private val TERMINAL_RESIZE: MethodHandle by lazy { downcall("ghostty_terminal_resize",
    FunctionDescriptor.of(C_INT, C_PTR, C_SHORT, C_SHORT, C_INT, C_INT)) }
  private val TERMINAL_GET: MethodHandle by lazy { downcall("ghostty_terminal_get",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val TERMINAL_GRID_REF: MethodHandle by lazy { downcall("ghostty_terminal_grid_ref",
    FunctionDescriptor.of(C_INT, C_PTR, POINT, C_PTR)) }
  private val GRID_REF_CELL: MethodHandle by lazy { downcall("ghostty_grid_ref_cell",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val GRID_REF_STYLE: MethodHandle by lazy { downcall("ghostty_grid_ref_style",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val GRID_REF_HYPERLINK_URI: MethodHandle by lazy { downcall("ghostty_grid_ref_hyperlink_uri",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, C_LONG, C_PTR)) }
  private val GRID_REF_GRAPHEMES: MethodHandle by lazy { downcall("ghostty_grid_ref_graphemes",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, C_LONG, C_PTR)) }
  private val GRID_REF_ROW: MethodHandle by lazy { downcall("ghostty_grid_ref_row",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val CELL_GET: MethodHandle by lazy { downcall("ghostty_cell_get",
    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_PTR)) }
  private val ROW_GET: MethodHandle by lazy { downcall("ghostty_row_get",
    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_PTR)) }
  private val TERMINAL_MODE_GET: MethodHandle by lazy { downcall("ghostty_terminal_mode_get",
    FunctionDescriptor.of(C_INT, C_PTR, C_SHORT, C_PTR)) }
  private val TERMINAL_SET: MethodHandle by lazy { downcall("ghostty_terminal_set",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val RENDER_STATE_NEW: MethodHandle by lazy { downcall("ghostty_render_state_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val RENDER_STATE_FREE: MethodHandle by lazy { downcall("ghostty_render_state_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val RENDER_STATE_UPDATE: MethodHandle by lazy { downcall("ghostty_render_state_update",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val RENDER_STATE_GET: MethodHandle by lazy { downcall("ghostty_render_state_get",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val RENDER_STATE_SET: MethodHandle by lazy { downcall("ghostty_render_state_set",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val RENDER_STATE_ROW_ITERATOR_NEW: MethodHandle by lazy { downcall("ghostty_render_state_row_iterator_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val RENDER_STATE_ROW_ITERATOR_FREE: MethodHandle by lazy { downcall("ghostty_render_state_row_iterator_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val RENDER_STATE_ROW_ITERATOR_NEXT: MethodHandle by lazy { downcall("ghostty_render_state_row_iterator_next",
    FunctionDescriptor.of(C_BOOL, C_PTR)) }
  private val RENDER_STATE_ROW_GET: MethodHandle by lazy { downcall("ghostty_render_state_row_get",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val RENDER_STATE_ROW_SET: MethodHandle by lazy { downcall("ghostty_render_state_row_set",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val TERMINAL_GRID_REF_TRACK: MethodHandle by lazy { downcall("ghostty_terminal_grid_ref_track",
    FunctionDescriptor.of(C_INT, C_PTR, POINT, C_PTR)) }
  private val TRACKED_GRID_REF_FREE: MethodHandle by lazy { downcall("ghostty_tracked_grid_ref_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val TRACKED_GRID_REF_POINT: MethodHandle by lazy { downcall("ghostty_tracked_grid_ref_point",
    FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR)) }
  private val TRACKED_GRID_REF_SET: MethodHandle by lazy { downcall("ghostty_tracked_grid_ref_set",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, POINT)) }
  private val SYS_SET: MethodHandle by lazy { downcall("ghostty_sys_set",
    FunctionDescriptor.of(C_INT, C_INT, C_PTR)) }
  private val TYPE_JSON: MethodHandle by lazy { downcall("ghostty_type_json",
    FunctionDescriptor.of(C_PTR)) }
  private val KEY_ENCODER_NEW: MethodHandle by lazy { downcall("ghostty_key_encoder_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val KEY_ENCODER_FREE: MethodHandle by lazy { downcall("ghostty_key_encoder_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val KEY_ENCODER_SETOPT_FROM_TERMINAL: MethodHandle by lazy { downcall("ghostty_key_encoder_setopt_from_terminal",
    FunctionDescriptor.ofVoid(C_PTR, C_PTR)) }
  private val KEY_ENCODER_ENCODE: MethodHandle by lazy { downcall("ghostty_key_encoder_encode",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, C_PTR, C_LONG, C_PTR)) }
  private val KEY_EVENT_NEW: MethodHandle by lazy { downcall("ghostty_key_event_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val KEY_EVENT_FREE: MethodHandle by lazy { downcall("ghostty_key_event_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val KEY_EVENT_SET_ACTION: MethodHandle by lazy { downcall("ghostty_key_event_set_action",
    FunctionDescriptor.ofVoid(C_PTR, C_INT)) }
  private val KEY_EVENT_SET_KEY: MethodHandle by lazy { downcall("ghostty_key_event_set_key",
    FunctionDescriptor.ofVoid(C_PTR, C_INT)) }
  private val KEY_EVENT_SET_MODS: MethodHandle by lazy { downcall("ghostty_key_event_set_mods",
    FunctionDescriptor.ofVoid(C_PTR, C_SHORT)) }
  private val KEY_EVENT_SET_UTF8: MethodHandle by lazy { downcall("ghostty_key_event_set_utf8",
    FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_LONG)) }
  private val KEY_EVENT_SET_UNSHIFTED_CODEPOINT: MethodHandle by lazy { downcall("ghostty_key_event_set_unshifted_codepoint",
    FunctionDescriptor.ofVoid(C_PTR, C_INT)) }
  private val KEY_EVENT_SET_COMPOSING: MethodHandle by lazy { downcall("ghostty_key_event_set_composing",
    FunctionDescriptor.ofVoid(C_PTR, C_BOOL)) }
  private val MOUSE_ENCODER_NEW: MethodHandle by lazy { downcall("ghostty_mouse_encoder_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val MOUSE_ENCODER_FREE: MethodHandle by lazy { downcall("ghostty_mouse_encoder_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val MOUSE_ENCODER_SETOPT: MethodHandle by lazy { downcall("ghostty_mouse_encoder_setopt",
    FunctionDescriptor.ofVoid(C_PTR, C_INT, C_PTR)) }
  private val MOUSE_ENCODER_SETOPT_FROM_TERMINAL: MethodHandle by lazy { downcall("ghostty_mouse_encoder_setopt_from_terminal",
    FunctionDescriptor.ofVoid(C_PTR, C_PTR)) }
  private val MOUSE_ENCODER_ENCODE: MethodHandle by lazy { downcall("ghostty_mouse_encoder_encode",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR, C_PTR, C_LONG, C_PTR)) }
  private val MOUSE_EVENT_NEW: MethodHandle by lazy { downcall("ghostty_mouse_event_new",
    FunctionDescriptor.of(C_INT, C_PTR, C_PTR)) }
  private val MOUSE_EVENT_FREE: MethodHandle by lazy { downcall("ghostty_mouse_event_free",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val MOUSE_EVENT_SET_ACTION: MethodHandle by lazy { downcall("ghostty_mouse_event_set_action",
    FunctionDescriptor.ofVoid(C_PTR, C_INT)) }
  private val MOUSE_EVENT_SET_BUTTON: MethodHandle by lazy { downcall("ghostty_mouse_event_set_button",
    FunctionDescriptor.ofVoid(C_PTR, C_INT)) }
  private val MOUSE_EVENT_CLEAR_BUTTON: MethodHandle by lazy { downcall("ghostty_mouse_event_clear_button",
    FunctionDescriptor.ofVoid(C_PTR)) }
  private val MOUSE_EVENT_SET_MODS: MethodHandle by lazy { downcall("ghostty_mouse_event_set_mods",
    FunctionDescriptor.ofVoid(C_PTR, C_SHORT)) }
  private val MOUSE_EVENT_SET_POSITION: MethodHandle by lazy { downcall("ghostty_mouse_event_set_position",
    FunctionDescriptor.ofVoid(C_PTR, GhosttyLayouts.MOUSE_POSITION)) }

  init {
    GhosttyLog.installIfEnabled()
  }

  private fun openLibrary(): SymbolLookup =
    SymbolLookup.libraryLookup(LibGhosttyVtLocator.findLibraryFile(), LIB_ARENA)

  private fun downcall(symbol: String, descriptor: FunctionDescriptor): MethodHandle {
    val address = LOOKUP.find(symbol)
      .orElseThrow { IllegalStateException("libghostty-vt symbol not found: $symbol") }
    return LINKER.downcallHandle(address, descriptor)
  }
}
