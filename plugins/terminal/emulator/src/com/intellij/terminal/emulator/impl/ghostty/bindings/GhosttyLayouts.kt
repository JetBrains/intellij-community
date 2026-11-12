// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import java.lang.foreign.AddressLayout
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Native ABI geometry for the libghostty-vt C types the bridge touches: the primitive value
 * layouts, the by-value struct layouts, and the hand-computed field offsets used to read/write
 * those structs.
 *
 * This is the single source of truth for struct sizes/offsets — the fragile part that must track
 * the C API. `GhosttyStyle` in particular is read via raw offsets rather
 * than layout var-handles, so the offsets live here alongside the layouts.
 *
 * Every size and offset here is checked against `ghostty_type_json()` by `GhosttyStructLayoutsTest`.
 */
internal object GhosttyLayouts {

  val C_INT: ValueLayout.OfInt = ValueLayout.JAVA_INT
  val C_LONG: ValueLayout.OfLong = ValueLayout.JAVA_LONG // size_t / GhosttyCell (64-bit)
  val C_SHORT: ValueLayout.OfShort = ValueLayout.JAVA_SHORT
  val C_BYTE: ValueLayout.OfByte = ValueLayout.JAVA_BYTE
  val C_BOOL: ValueLayout.OfBoolean = ValueLayout.JAVA_BOOLEAN // C `bool` (1 byte)
  val C_FLOAT: ValueLayout.OfFloat = ValueLayout.JAVA_FLOAT
  val C_PTR: AddressLayout = ValueLayout.ADDRESS

  /** `GhosttyPoint`: `enum tag; union { {u16 x; u32 y;}; u64[2]; }` (24 bytes, align 8). */
  val POINT: MemoryLayout = MemoryLayout.structLayout(
    C_INT.withName("tag"),
    MemoryLayout.paddingLayout(4L),
    MemoryLayout.sequenceLayout(2L, C_LONG).withName("value"))
  const val POINT_OFF_TAG = 0L
  const val POINT_OFF_X = 8L
  const val POINT_OFF_Y = 12L

  /** `GhosttyPointCoordinate`: `struct { uint16 x; uint32 y; }` (8 bytes, align 4) — output of `ghostty_tracked_grid_ref_point`. */
  val POINT_COORD: MemoryLayout = MemoryLayout.structLayout(
    C_SHORT.withName("x"),
    MemoryLayout.paddingLayout(2L),
    C_INT.withName("y"))
  const val POINT_COORD_OFF_Y = 4L

  /** `GhosttyString`: `const uint8_t* ptr; size_t len;` (16 bytes, align 8). */
  val STRING: MemoryLayout = MemoryLayout.structLayout(
    C_PTR.withName("ptr"),
    C_LONG.withName("len"))
  const val STRING_OFF_LEN = 8L

  /**
   * Allocates a `GhosttyString` in [arena] that points at the UTF-8 bytes of [text].
   * The bytes live in [arena] as well.
   */
  fun allocateString(arena: Arena, text: String): MemorySegment {
    val bytes = text.encodeToByteArray()
    val data = arena.allocate(bytes.size.toLong())
    MemorySegment.copy(bytes, 0, data, C_BYTE, 0L, bytes.size)
    val string = arena.allocate(STRING)
    string.set(C_PTR, 0L, data)
    string.set(C_LONG, STRING_OFF_LEN, bytes.size.toLong())
    return string
  }

  /** `GhosttyGridRef`: `void* node; uint16 x; uint16 y;` (frozen layout, 16 bytes, align 8). */
  val GRID_REF: MemoryLayout = MemoryLayout.structLayout(
    C_PTR.withName("node"),
    C_SHORT.withName("x"),
    C_SHORT.withName("y"),
    MemoryLayout.paddingLayout(4L))

  // `GhosttyTerminalProgressReport` (frozen layout, 8 bytes, align 4): `int state; int8_t progress`
  // — the payload borrowed by the GHOSTTY_TERMINAL_OPT_PROGRESS_REPORT callback. Field byte offsets:
  //   state@0  progress@4 ; total padded to 8.
  const val PROGRESS_REPORT_SIZE = 8L
  const val PROGRESS_REPORT_OFF_STATE = 0L
  const val PROGRESS_REPORT_OFF_PROGRESS = 4L

  /**
   * `GhosttyTerminalModeConfig` (terminal.h, frozen layout, 4 bytes, align 2): `GhosttyMode mode; bool value;`.
   * Used both ways: `mode` is set before `ghostty_terminal_get`/`ghostty_terminal_set` and `value` is read back (get) or set (set).
   */
  const val MODE_CONFIG_OFF_VALUE = 2L

  /** `GhosttyMousePosition` (mouse/event.h): `struct { float x; float y; }`, passed by value. */
  val MOUSE_POSITION: MemoryLayout = MemoryLayout.structLayout(
    C_FLOAT.withName("x"),
    C_FLOAT.withName("y"))
  const val MOUSE_POSITION_OFF_X = 0L
  const val MOUSE_POSITION_OFF_Y = 4L

  // `GhosttyMouseEncoderSize` (mouse/encoder.h, sized struct, 40 bytes, align 8):
  //   size_t size; uint32 screen_width, screen_height, cell_width, cell_height,
  //   padding_top, padding_bottom, padding_right, padding_left.
  const val MOUSE_ENCODER_SIZE_BYTES = 40L
  const val MOUSE_ENCODER_SIZE_OFF_SCREEN_WIDTH = 8L
  const val MOUSE_ENCODER_SIZE_OFF_SCREEN_HEIGHT = 12L
  const val MOUSE_ENCODER_SIZE_OFF_CELL_WIDTH = 16L
  const val MOUSE_ENCODER_SIZE_OFF_CELL_HEIGHT = 20L

  // `GhosttyStyle` (frozen layout, 16 bytes, align 2). Each GhosttyStyleColor is {uint8 tag; union{...} value} =
  // 4 bytes (tag @+0, 3-byte value @+1). Field byte offsets:
  //   fg_color@0  bg_color@4  underline_color@8  flags(uint16, see GhosttyStyleFlags)@12  reserved@14.
  const val STYLE_SIZE = 16L
  const val STYLE_OFF_FG = 0L
  const val STYLE_OFF_BG = 4L
  const val STYLE_COLOR_OFF_VALUE = 1L
  const val STYLE_OFF_FLAGS = 12L
}
