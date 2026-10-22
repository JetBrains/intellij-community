// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.CellPixelSize
import com.intellij.terminal.emulator.TerminalGridGeometry
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_BOOL
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_INT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_PTR
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_SHORT
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalData
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

/**
 * Keeps the cell pixel size of a native terminal and answers its size queries.
 *
 * The library asks through the `GHOSTTY_TERMINAL_OPT_SIZE` callback when the program
 * sends `CSI 14/16/18/19 t` or enables mode 2048. The grid size comes from the native
 * terminal through [columns] and [rows], because the program can change it without the
 * embedder: DECCOLM (modes 40 and 3) sets 80 or 132 columns. The cell pixel size comes
 * from [resize].
 *
 * Not thread-safe: it follows the emulator's single-threaded contract.
 *
 * @param readTerminalDataAsU16 reads a `uint16_t` value of the terminal, such as
 *   [GhosttyTerminalData.COLS], and throws if the read fails.
 */
internal class GhosttySizeHandler(
  initialCellSize: CellPixelSize,
  private val readTerminalDataAsU16: (GhosttyTerminalData) -> Int,
) {
  private var lastCellSize: CellPixelSize = initialCellSize

  /**
   * The column count of the native terminal.
   *
   * @throws IllegalStateException if the library rejects the read.
   */
  val columns: Int
    get() = readTerminalDataAsU16(GhosttyTerminalData.COLS)

  /**
   * The row count of the active screen of the native terminal.
   *
   * @throws IllegalStateException if the library rejects the read.
   */
  val rows: Int
    get() = readTerminalDataAsU16(GhosttyTerminalData.ROWS)

  /**
   * Installs the size callback on [terminal]. The upcall stub lives in [arena].
   *
   * @throws IllegalStateException if the library rejects the callback.
   * @throws RuntimeException if the stub cannot be created or the native call fails.
   */
  fun install(terminal: MemorySegment, arena: Arena) {
    val result = try {
      val handle = MethodHandles.lookup().bind(this, "onSizeQuery", SIZE_DESC.toMethodType())
      val stub = Linker.nativeLinker().upcallStub(handle, SIZE_DESC, arena)
      LibGhosttyVt.terminalSet(terminal, GhosttyTerminalOption.SIZE.code, stub)
    }
    catch (t: Throwable) {
      rethrowControlFlowException(t)
      throw RuntimeException("installing SIZE callback failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("ghostty_terminal_set(SIZE) returned $result")
    }
  }

  /**
   * Resizes [terminal] to [geometry] and keeps its cell pixel size for the size queries.
   * While mode 2048 is on, the native call sends the in-band size report from these values.
   *
   * @throws IllegalStateException if the library rejects the resize.
   * @throws RuntimeException if the native call fails.
   */
  fun resize(terminal: MemorySegment, geometry: TerminalGridGeometry) {
    val (gridSize, cellSize) = geometry
    val result = try {
      terminalResize(terminal, gridSize.columns.toShort(), gridSize.rows.toShort(), cellSize.width, cellSize.height)
    }
    catch (t: Throwable) {
      rethrowControlFlowException(t)
      throw RuntimeException("ghostty_terminal_resize failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("ghostty_terminal_resize returned $result")
    }
    this.lastCellSize = cellSize
  }

  /**
   * Invoked from native code when the program queries the size
   * (`XTWINOPS`, `CSI 14/16/18/19 t`) or enables the in-band size reports (mode 2048).
   * Fills [out], a `GhosttySizeReportSize`, with [columns], [rows], and [lastCellSize].
   * Returns false, which leaves the query unanswered, if a read throws.
   *
   * Never throws: an exception thrown out of an FFM upcall terminates the JVM.
   */
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onSizeQuery(terminal: MemorySegment, userdata: MemorySegment, out: MemorySegment): Boolean {
    val (columns, rows) = try {
      TerminalSize(columns, rows)
    }
    catch (t: Throwable) {
      // No rethrow, not even of a control flow exception: it would escape the upcall.
      // A control flow exception cannot arise in the native read anyway.
      return false
    }
    val struct = out.reinterpret(SIZE_REPORT_SIZE.byteSize())
    struct.set(C_SHORT, OFF_ROWS, rows.toShort())
    struct.set(C_SHORT, OFF_COLUMNS, columns.toShort())
    struct.set(C_INT, OFF_CELL_WIDTH, lastCellSize.width)
    struct.set(C_INT, OFF_CELL_HEIGHT, lastCellSize.height)
    return true
  }

  companion object {
    // `GhosttyTerminalSizeFn` (terminal.h):
    // `bool(terminal, userdata, GhosttySizeReportSize* out)`.
    private val SIZE_DESC: FunctionDescriptor = FunctionDescriptor.of(C_BOOL, C_PTR, C_PTR, C_PTR)

    /**
     * `GhosttySizeReportSize` (size_report.h, 12 bytes, align 4), the struct that the
     * callback fills:
     * `uint16 rows; uint16 columns; uint32 cell_width; uint32 cell_height;`.
     */
    val SIZE_REPORT_SIZE: MemoryLayout = MemoryLayout.structLayout(
      C_SHORT.withName("rows"),
      C_SHORT.withName("columns"),
      C_INT.withName("cell_width"),
      C_INT.withName("cell_height"),
    )
    const val OFF_ROWS = 0L
    const val OFF_COLUMNS = 2L
    const val OFF_CELL_WIDTH = 4L
    const val OFF_CELL_HEIGHT = 8L

    /**
     * `ghostty_terminal_resize`: resize to [cols]x[rows] with cells of
     * [cellWidthPx]x[cellHeightPx] pixels.
     */
    private fun terminalResize(terminal: MemorySegment, cols: Short, rows: Short, cellWidthPx: Int, cellHeightPx: Int): GhosttyResult =
      GhosttyResult.of(TERMINAL_RESIZE.invokeExact(terminal, cols, rows, cellWidthPx, cellHeightPx) as Int)

    private val TERMINAL_RESIZE: MethodHandle by lazy {
      LibGhosttyVt.downcall("ghostty_terminal_resize", FunctionDescriptor.of(C_INT, C_PTR, C_SHORT, C_SHORT, C_INT, C_INT))
    }
  }
}
