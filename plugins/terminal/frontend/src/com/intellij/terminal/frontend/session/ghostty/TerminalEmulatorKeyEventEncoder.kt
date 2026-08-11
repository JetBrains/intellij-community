// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalInputModifier
import com.intellij.terminal.emulator.TerminalKey
import com.intellij.terminal.emulator.TerminalKeyEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Turns AWT key events into PTY bytes for [GhosttyTerminalSession]. The escape
 * sequences themselves come from [TerminalEmulator.encodeKeyEvent], i.e. from the
 * emulator's own encoder, which consults the live terminal modes (DECCKM, keypad, the
 * Kitty keyboard protocol). What jediterm's `TerminalKeyEventProcessor` does with a
 * hand-maintained table for the JediTerm session, this class gets from the emulator.
 *
 * What stays at this layer is policy the wire protocol does not know about:
 * - the macOS "natural text editing" chords (Cmd/Option + arrows), which both
 *   jediterm's key table and the Ghostty app's default keybinds resolve above VT
 *   encoding — a VT encoder reports SUPER as the xterm meta modifier, which shells
 *   ignore;
 * - Alt as an ESC prefix for characters (the `altSendsEscape` setting); the native
 *   encoder cannot apply it on macOS, where its `macos_option_as_alt` option defaults
 *   to off;
 * - splitting AWT's KEY_PRESSED/KEY_TYPED pair so each keystroke is encoded exactly
 *   once.
 *
 * Not thread-safe: it drives the lock-protected emulator, so every call must happen
 * under the owning session's lock.
 */
internal class TerminalEmulatorKeyEventEncoder(
  private val emulator: TerminalEmulator,
  private val settings: JBTerminalSystemSettingsProviderBase,
) {
  fun encodeKeyEvent(e: KeyEvent): KeyEventProcessingResultDto = when (e.id) {
    KeyEvent.KEY_PRESSED -> keyPressed(e)
    KeyEvent.KEY_TYPED -> keyTyped(e)
    else -> KeyEventProcessingResultDto.Unhandled
  }

  private fun keyPressed(e: KeyEvent): KeyEventProcessingResultDto {
    // Numpad Delete with NumLock on: the key code says Delete, but the key types '.'.
    if (e.keyCode == KeyEvent.VK_DELETE && e.keyChar == '.') {
      return bytesResult(byteArrayOf('.'.code.toByte()), e)
    }

    if (settings.shiftEnterSendsEscCR() && e.keyCode == KeyEvent.VK_ENTER && modifierKeys(e) == InputEvent.SHIFT_DOWN_MASK) {
      return bytesResult(byteArrayOf(ESC, CR), e)
    }

    macNaturalTextEditingChord(e)?.let { chord ->
      return bytesResult(chord, e)
    }

    val functionalKey = functionalKey(e.keyCode)
    if (functionalKey != null) {
      val bytes = emulator.encodeKeyEvent(TerminalKeyEvent(functionalKey, modifiers = terminalModifiers(e)))
      return if (bytes.isNotEmpty()) bytesResult(bytes, e) else KeyEventProcessingResultDto.Unhandled
    }

    if (isAltPressedOnly(e) && Character.isDefined(e.keyChar) && settings.altSendsEscape()) {
      // The base character, not e.keyChar: on macOS Option+F types 'ƒ' while ESC f is
      // wanted. Uppercase under shift — zsh distinguishes ESC f from ESC F.
      val base = e.keyCode.toChar().let { if (e.isShiftDown) it.uppercaseChar() else it.lowercaseChar() }
      return KeyEventProcessingResultDto.StringResult(Char(27) + base.toString(), false)
    }

    // Ctrl chords arrive as KEY_PRESSED (AWT reduces their keyChar to a control
    // character, or to a plain space for Ctrl+Space). Hand the encoder the physical key
    // and its unmodified codepoint, so modes like the Kitty keyboard protocol can
    // encode the chord instead of a lone control byte.
    if (e.isControlDown) {
      val writingKey = writingKey(e.keyCode)
      if (writingKey != null) {
        val event = TerminalKeyEvent(writingKey.key, modifiers = terminalModifiers(e), unshiftedCodepoint = writingKey.codepoint)
        val bytes = emulator.encodeKeyEvent(event)
        if (bytes.isNotEmpty()) return bytesResult(bytes, e)
      }
    }

    // A control character this layer cannot map to a key (a chord on a layout the
    // tables above don't cover): send it the way AWT computed it. Printable characters
    // are left to KEY_TYPED.
    if (Character.isISOControl(e.keyChar)) {
      return typedCharacter(e)
    }
    return KeyEventProcessingResultDto.Unhandled
  }

  private fun keyTyped(e: KeyEvent): KeyEventProcessingResultDto {
    if (Character.isISOControl(e.keyChar)) {
      return KeyEventProcessingResultDto.Unhandled // the KEY_PRESSED half of the pair owns control characters
    }
    return typedCharacter(e)
  }

  private fun typedCharacter(e: KeyEvent): KeyEventProcessingResultDto {
    if (isAltPressedOnly(e) && settings.altSendsEscape()) {
      return KeyEventProcessingResultDto.Unhandled // the KEY_PRESSED path sent ESC + base character
    }
    if (e.keyChar == '`' && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0) {
      return KeyEventProcessingResultDto.Unhandled // Cmd+backtick cycles macOS windows; never type it
    }

    // Only shift and command reach the encoder: the character already reflects the
    // keyboard layout, and passing Ctrl/Alt would make the encoder re-derive chords
    // (AltGr text arrives with Ctrl+Alt down on some platforms). SUPER lets it apply
    // the macOS rule that command chords never type text.
    val modifiers = buildSet {
      if (e.isShiftDown) add(TerminalInputModifier.SHIFT)
      if (e.isMetaDown) add(TerminalInputModifier.SUPER)
    }
    val event = TerminalKeyEvent(
      TerminalKey.UNIDENTIFIED,
      modifiers = modifiers,
      text = e.keyChar.toString(),
      unshiftedCodepoint = e.keyChar.lowercaseChar().code,
    )
    val bytes = emulator.encodeKeyEvent(event)
    if (bytes.isEmpty()) {
      return KeyEventProcessingResultDto.Unhandled
    }
    return KeyEventProcessingResultDto.StringResult(bytes.toString(Charsets.UTF_8), settings.scrollToBottomOnTyping())
  }

  /**
   * The macOS "natural text editing" chords, resolved above VT encoding like jediterm's
   * macOS key table and the Ghostty app's default keybinds: Cmd+arrows edit the line
   * via Ctrl+A / Ctrl+E, Option+arrows jump words via ESC b / ESC f.
   */
  private fun macNaturalTextEditingChord(e: KeyEvent): ByteArray? {
    if (!SystemInfoRt.isMac) return null
    val mods = modifierKeys(e)
    val cmd = mods == InputEvent.META_DOWN_MASK
    val option = mods == InputEvent.ALT_DOWN_MASK
    return when {
      cmd && e.keyCode == KeyEvent.VK_LEFT -> byteArrayOf(1) // Ctrl+A: line start
      cmd && e.keyCode == KeyEvent.VK_RIGHT -> byteArrayOf(5) // Ctrl+E: line end
      option && e.keyCode == KeyEvent.VK_LEFT -> byteArrayOf(ESC, 'b'.code.toByte()) // backward-word
      option && e.keyCode == KeyEvent.VK_RIGHT -> byteArrayOf(ESC, 'f'.code.toByte()) // forward-word
      else -> null
    }
  }

  private fun bytesResult(bytes: ByteArray, e: KeyEvent): KeyEventProcessingResultDto.BytesResult {
    val shouldScroll = settings.scrollToBottomOnTyping() && isCodeThatScrolls(e.keyCode)
    return KeyEventProcessingResultDto.BytesResult(bytes, shouldScroll)
  }

  /**
   * A key from the functional block that encodes on KEY_PRESSED and produces no typed
   * text of interest.
   */
  private fun functionalKey(keyCode: Int): TerminalKey? = when (keyCode) {
    KeyEvent.VK_ENTER -> TerminalKey.ENTER
    KeyEvent.VK_BACK_SPACE -> TerminalKey.BACKSPACE
    KeyEvent.VK_TAB -> TerminalKey.TAB
    KeyEvent.VK_ESCAPE -> TerminalKey.ESCAPE
    KeyEvent.VK_INSERT -> TerminalKey.INSERT
    KeyEvent.VK_DELETE -> TerminalKey.DELETE
    KeyEvent.VK_HOME -> TerminalKey.HOME
    KeyEvent.VK_END -> TerminalKey.END
    KeyEvent.VK_PAGE_UP -> TerminalKey.PAGE_UP
    KeyEvent.VK_PAGE_DOWN -> TerminalKey.PAGE_DOWN
    KeyEvent.VK_UP -> TerminalKey.ARROW_UP
    KeyEvent.VK_DOWN -> TerminalKey.ARROW_DOWN
    KeyEvent.VK_LEFT -> TerminalKey.ARROW_LEFT
    KeyEvent.VK_RIGHT -> TerminalKey.ARROW_RIGHT
    // Both VK_F1..VK_F12 and TerminalKey.F1..F12 are contiguous blocks.
    in KeyEvent.VK_F1..KeyEvent.VK_F12 -> TerminalKey.entries[TerminalKey.F1.ordinal + (keyCode - KeyEvent.VK_F1)]
    else -> null
  }

  /**
   * A key from the writing-system block: the [TerminalKey] and the codepoint it
   * produces with no modifiers.
   */
  private class WritingKey(val key: TerminalKey, val codepoint: Int)

  private fun writingKey(keyCode: Int): WritingKey? = when (keyCode) {
    // VK codes for letters are the uppercase ASCII letters; both key ranges are
    // contiguous blocks.
    in KeyEvent.VK_A..KeyEvent.VK_Z ->
      WritingKey(TerminalKey.entries[TerminalKey.A.ordinal + (keyCode - KeyEvent.VK_A)], keyCode.toChar().lowercaseChar().code)
    in KeyEvent.VK_0..KeyEvent.VK_9 ->
      WritingKey(TerminalKey.entries[TerminalKey.DIGIT_0.ordinal + (keyCode - KeyEvent.VK_0)], keyCode)
    KeyEvent.VK_SPACE -> WritingKey(TerminalKey.SPACE, ' '.code)
    KeyEvent.VK_OPEN_BRACKET -> WritingKey(TerminalKey.BRACKET_LEFT, '['.code)
    KeyEvent.VK_CLOSE_BRACKET -> WritingKey(TerminalKey.BRACKET_RIGHT, ']'.code)
    KeyEvent.VK_BACK_SLASH -> WritingKey(TerminalKey.BACKSLASH, '\\'.code)
    KeyEvent.VK_MINUS -> WritingKey(TerminalKey.MINUS, '-'.code)
    else -> null
  }

  private fun terminalModifiers(e: KeyEvent): Set<TerminalInputModifier> = buildSet {
    if (e.isShiftDown) add(TerminalInputModifier.SHIFT)
    if (e.isControlDown) add(TerminalInputModifier.CTRL)
    if (e.isAltDown) add(TerminalInputModifier.ALT)
    if (e.isMetaDown) add(TerminalInputModifier.SUPER)
  }

  private fun modifierKeys(e: KeyEvent): Int =
    e.modifiersEx and (InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK
      or InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK)

  private fun isAltPressedOnly(e: KeyEvent): Boolean {
    val mods = e.modifiersEx
    return (mods and InputEvent.ALT_DOWN_MASK) != 0
      && (mods and InputEvent.ALT_GRAPH_DOWN_MASK) == 0
      && (mods and InputEvent.CTRL_DOWN_MASK) == 0
      && (mods and InputEvent.SHIFT_DOWN_MASK) == 0
  }

  private fun isCodeThatScrolls(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
    KeyEvent.VK_BACK_SPACE, KeyEvent.VK_INSERT, KeyEvent.VK_DELETE, KeyEvent.VK_ENTER,
    KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
      -> true
    else -> false
  }

  companion object {
    private const val ESC: Byte = 0x1B
    private const val CR: Byte = 0x0D
  }
}
