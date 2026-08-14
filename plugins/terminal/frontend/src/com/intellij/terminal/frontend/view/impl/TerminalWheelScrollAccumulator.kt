// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.scroll.TouchScrollUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import java.awt.event.MouseWheelEvent

/**
 * Turns a raw AWT mouse-wheel signal into a whole number of terminal rows to scroll, evening out how bursty
 * or coarse that signal can be.
 *
 * The per-event pixel delta calculation follows the logic defined in the platform:
 * [com.intellij.ui.components.JBScrollPane.JBMouseWheelListener.mouseWheelMoved]
 */
@ApiStatus.Internal
class TerminalWheelScrollAccumulator(private val editor: Editor) {
  private var pendingPixels = 0.0

  /**
   * Returns the number of whole rows [event] should scroll (signed: positive/negative matching
   * [MouseWheelEvent.getWheelRotation]'s own sign). Returns 0 when the event hasn't accumulated a full row
   * yet - the fractional remainder is kept for the next call.
   */
  fun consumeLines(event: MouseWheelEvent): Int {
    val rowHeight = editor.lineHeight
    pendingPixels += getDelta(event, rowHeight)
    val lines = (pendingPixels / rowHeight).toInt()
    pendingPixels -= lines * rowHeight
    return lines
  }

  /**
   * Calculates the pixel delta for the given mouse wheel event according to the platform's JBScrollPane logic
   * defined in [com.intellij.ui.components.JBScrollPane.JBMouseWheelListener.mouseWheelMoved]
   */
  private fun getDelta(event: MouseWheelEvent, rowHeight: Int): Double {
    return when {
      TouchScrollUtil.isTouchScroll(event) -> {
        if (TouchScrollUtil.isUpdate(event)) TouchScrollUtil.getDelta(event) else 0.0
      }
      event.scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL -> {
        val terminalSize = editor.calculateTerminalSize()
        if (terminalSize != null) {
          val direction = if (event.preciseWheelRotation < 0) -1 else 1
          direction * terminalSize.rows * rowHeight.toDouble()
        }
        else 0.0  // Strange case, but we don't want to crash.
      }
      event.scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL -> {
        @OptIn(LowLevelLocalMachineAccess::class)
        if (OS.CURRENT == OS.macOS && SystemInfo.isJetBrainsJvm) {
          // Like in JBScrollBar#getPreciseDelta
          10.0 * event.preciseWheelRotation
        }
        else {
          event.preciseWheelRotation * event.scrollAmount * rowHeight
        }
      }
      else -> {
        0.0  // Unknown scroll type.
      }
    }
  }
}
