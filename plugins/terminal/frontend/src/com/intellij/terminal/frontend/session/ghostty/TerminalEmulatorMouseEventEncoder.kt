// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalKey
import com.intellij.terminal.emulator.TerminalKeyEvent
import com.intellij.terminal.emulator.TerminalInputModifier
import com.intellij.terminal.emulator.TerminalMouseAction
import com.intellij.terminal.emulator.TerminalMouseButton
import com.intellij.terminal.emulator.TerminalMouseEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlin.math.abs

/**
 * Turns AWT mouse events into PTY bytes for [GhosttyTerminalSession]. The escape
 * sequences themselves come from [TerminalEmulator.encodeMouseEvent], i.e. from the
 * emulator's own encoder, which consults the live mouse tracking modes and report
 * formats. What jediterm's `TerminalMouseEventEncoder` does with a hand-maintained
 * table for the JediTerm session, this class gets from the emulator.
 *
 * What stays at this layer is policy the wire protocol does not know about: which mouse
 * events belong to the IDE, not the program — shift-clicks (text selection), right
 * clicks (context menu), and the wheel-to-arrow-keys translation for full-screen
 * programs in the alternate screen.
 *
 * Not thread-safe: it drives the lock-protected emulator, so every call must happen
 * under the owning session's lock.
 */
internal class TerminalEmulatorMouseEventEncoder(
  private val emulator: TerminalEmulator,
  private val settings: JBTerminalSystemSettingsProviderBase,
) {
  /**
   * The cell of the last observed mouse motion: AWT reports motion per pixel, the
   * protocol per cell.
   */
  private var lastMotionColumn = -1
  private var lastMotionRow = -1

  /**
   * [x] and [y] are zero-based cell coordinates. Returns null when nothing should reach
   * the PTY.
   */
  fun encodeMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray? {
    if (e is MouseWheelEvent) {
      return encodeWheelEvent(e, x, y)
    }
    // Shift-clicks belong to the IDE (text selection), and reporting can be switched
    // off entirely.
    val reporting = settings.enableMouseReporting() && !e.isShiftDown
    return when (e.id) {
      MouseEvent.MOUSE_PRESSED -> {
        if (!reporting) return null
        val button = pressedButton(e) ?: return null
        report(TerminalMouseAction.PRESS, button, x, y, e)
      }
      MouseEvent.MOUSE_RELEASED -> {
        lastMotionColumn = -1
        lastMotionRow = -1
        if (!reporting) return null
        val button = pressedButton(e) ?: return null
        report(TerminalMouseAction.RELEASE, button, x, y, e)
      }
      MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED -> {
        if (x == lastMotionColumn && y == lastMotionRow) return null
        lastMotionColumn = x
        lastMotionRow = y
        if (!reporting) return null
        val button = if (e.id == MouseEvent.MOUSE_DRAGGED) pressedButton(e) else null
        report(TerminalMouseAction.MOTION, button, x, y, e)
      }
      else -> null
    }
  }

  private fun encodeWheelEvent(e: MouseWheelEvent, x: Int, y: Int): ByteArray? {
    if (e.wheelRotation == 0 || e.isShiftDown) return null // shift-wheel is a horizontal scroll
    val towardUser = e.wheelRotation > 0
    if (settings.enableMouseReporting()) {
      val button = if (towardUser) TerminalMouseButton.WHEEL_DOWN else TerminalMouseButton.WHEEL_UP
      val reported = report(TerminalMouseAction.PRESS, button, x, y, e)
      if (reported != null) return reported
    }
    // No tracking requested: full-screen programs still expect the wheel to scroll, so
    // translate it to the arrow keys they do understand (the encoder honors DECCKM).
    if (emulator.usingAlternateScreen && settings.simulateMouseScrollWithArrowKeysInAlternativeScreen()) {
      val arrow = if (towardUser) TerminalKey.ARROW_DOWN else TerminalKey.ARROW_UP
      val arrowBytes = emulator.encodeKeyEvent(TerminalKeyEvent(arrow))
      val repeatCount = abs(e.unitsToScroll)
      if (arrowBytes.isEmpty() || repeatCount == 0) return null
      val result = ByteArray(arrowBytes.size * repeatCount)
      for (i in 0 until repeatCount) {
        arrowBytes.copyInto(result, i * arrowBytes.size)
      }
      return result
    }
    return null
  }

  /**
   * Empty encoder output means the program did not request this kind of tracking —
   * report nothing.
   */
  private fun report(action: TerminalMouseAction, button: TerminalMouseButton?, x: Int, y: Int, e: MouseEvent): ByteArray? {
    val bytes = emulator.encodeMouseEvent(TerminalMouseEvent(action, button, x, y, mouseModifiers(e)))
    return bytes.takeIf { it.isNotEmpty() }
  }

  private fun pressedButton(e: MouseEvent): TerminalMouseButton? = when {
    SwingUtilities.isLeftMouseButton(e) -> TerminalMouseButton.LEFT
    SwingUtilities.isMiddleMouseButton(e) -> TerminalMouseButton.MIDDLE
    // The right button opens the IDE context menu and is never reported (the JediTerm
    // path does the same).
    else -> null
  }

  private fun mouseModifiers(e: MouseEvent): Set<TerminalInputModifier> = buildSet {
    if (e.isControlDown) add(TerminalInputModifier.CTRL)
    if (e.isAltDown) add(TerminalInputModifier.ALT)
    if (e.isMetaDown) add(TerminalInputModifier.SUPER)
  }
}
