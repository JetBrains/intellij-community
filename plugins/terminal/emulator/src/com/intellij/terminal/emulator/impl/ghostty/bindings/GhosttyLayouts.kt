// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import java.lang.foreign.AddressLayout
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

/**
 * Native ABI geometry for the libghostty-vt C types the bridge touches: the primitive value
 * layouts, the by-value struct layouts, and the hand-computed field offsets used to read/write
 * those structs.
 *
 * This is the single source of truth for struct sizes/offsets — the fragile part that must track
 * the (pre-1.0, still-moving) C API. `GhosttyStyle` in particular is read via raw offsets rather
 * than layout var-handles, so the offsets live here alongside the layouts.
 *
 * Every size and offset here is checked against `ghostty_type_json()` — the layout
 * description compiled into the bundled library — by `GhosttyLayoutsTest`.
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

  /** `GhosttyGridRef`: `size_t size; void* node; uint16 x; uint16 y;` (24 bytes, align 8). */
  val GRID_REF: MemoryLayout = MemoryLayout.structLayout(
    C_LONG.withName("size"),
    C_PTR.withName("node"),
    C_SHORT.withName("x"),
    C_SHORT.withName("y"),
    MemoryLayout.paddingLayout(4L))

  // `GhosttyTerminalProgressReport` (sized struct, 16 bytes, align 8): `size_t size; int state; int8_t progress`
  // — the payload borrowed by the GHOSTTY_TERMINAL_OPT_PROGRESS_REPORT callback. Field byte offsets:
  //   size@0  state@8  progress@12 ; total padded to 16.
  const val PROGRESS_REPORT_OFF_STATE = 8L
  const val PROGRESS_REPORT_OFF_PROGRESS = 12L

  /** Smallest `GhosttyTerminalProgressReport.size` that still covers every field the bridge reads. */
  const val PROGRESS_REPORT_MIN_SIZE = PROGRESS_REPORT_OFF_PROGRESS + 1L

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

  // `GhosttyStyle` (sized struct, 72 bytes). Each GhosttyStyleColor is {int tag; union{...} value} =
  // 16 bytes (tag @+0, 8-byte value @+8). Field byte offsets:
  //   size@0  fg(tag@8,val@16)  bg(tag@24,val@32)  underline_color(tag@40,val@48)
  //   bold@56 italic@57 faint@58 blink@59 inverse@60 invisible@61 strikethrough@62 overline@63
  //   underline(int)@64 ; total padded to 72.
  const val STYLE_SIZE = 72L
  const val STYLE_OFF_FG_TAG = 8L
  const val STYLE_OFF_FG_VAL = 16L
  const val STYLE_OFF_BG_TAG = 24L
  const val STYLE_OFF_BG_VAL = 32L
  const val STYLE_OFF_BOLD = 56L
  const val STYLE_OFF_ITALIC = 57L
  const val STYLE_OFF_FAINT = 58L
  const val STYLE_OFF_BLINK = 59L
  const val STYLE_OFF_INVERSE = 60L
  const val STYLE_OFF_INVISIBLE = 61L
  const val STYLE_OFF_UNDERLINE = 64L
}
