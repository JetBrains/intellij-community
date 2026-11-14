// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalInputModifier
import com.intellij.terminal.emulator.TerminalKey
import com.intellij.terminal.emulator.TerminalKeyAction
import com.intellij.terminal.emulator.TerminalKeyEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns AWT key events into PTY bytes for [GhosttyTerminalSession]. The escape
 * sequences themselves come from [TerminalEmulator.encodeKeyEvent], i.e. from the
 * emulator's own encoder, which consults the live terminal modes (DECCKM, keypad, the
 * Kitty keyboard protocol). What jediterm's `TerminalKeyEventProcessor` does with a
 * hand-maintained table for the JediTerm session, this class gets from the emulator.
 *
 * What stays at this layer is policy the wire protocol does not know about:
 * - the macOS "natural text editing" chords (Cmd/Option + arrows, Cmd+Backspace). The
 *   Ghostty app resolves them above VT encoding too: a VT encoder reports SUPER as the
 *   xterm meta modifier, which shells ignore;
 * - Shift+Enter as `ESC CR` outside the Kitty keyboard protocol, while the "Send Esc+CR on Shift+Enter" setting is on;
 * - whether Alt is a modifier for the shell (the `altSendsEscape` setting). The encoder
 *   gets it as its `macos_option_as_alt` option and applies the active protocol;
 * - splitting AWT's KEY_PRESSED/KEY_TYPED pair so each keystroke is encoded exactly once.
 *   The typed half has no key code, so the pressed half lends it the physical key;
 * - telling a repeat from a press, which AWT does not report (see [pressAction]);
 * - reporting a release only for a key whose press the program saw (see [keyReleased]);
 * - the classic bytes of Ctrl+I, Ctrl+M and Ctrl+[ outside the Kitty keyboard protocol.
 *   The encoder's fixterms rule would turn them into CSI u chords (see [classicControlByte]).
 *
 * Not thread-safe: it drives the lock-protected emulator, so every call must happen
 * under the owning session's lock.
 *
 * @param lockingKeys reads the Caps Lock and Num Lock state; a test replaces it.
 */
internal class TerminalEmulatorKeyEventEncoder(
  private val emulator: TerminalEmulator,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val lockingKeys: () -> Set<TerminalInputModifier> = LockingKeys::current,
) {
  /** What this layer knows about a key that is held down. */
  private class DownKey {
    /**
     * The event the encoder encoded for the press or its typed half; null when the program
     * never saw the press.
     */
    var press: TerminalKeyEvent? = null

    /** Whether a flag of the Kitty keyboard protocol was active when the press went out. */
    var underKittyProtocol = false

    /** Whether the typed half reported the press, with the modifiers of [typedModifiers]. */
    var typed = false
  }

  /** The KEY_PRESSED that still waits for the KEY_TYPED half of its keystroke. */
  private class PendingPress(val keyCode: Int, val action: TerminalKeyAction, val downKey: DownKey)

  private val downKeys = HashMap<Int, DownKey>()
  private var pendingPress: PendingPress? = null

  /** The lock state at the last KEY_PRESSED; the typed and the released halves reuse it. */
  private var locks: Set<TerminalInputModifier> = emptySet()

  fun encodeKeyEvent(e: KeyEvent): KeyEventProcessingResultDto {
    // The setting can change at any time; the emulator applies the option on every encode.
    emulator.setOptionAsAlt(settings.altSendsEscape())
    return when (e.id) {
      KeyEvent.KEY_PRESSED -> {
        locks = lockingKeys()
        val downKey = DownKey()
        val pending = PendingPress(e.keyCode, pressAction(e, downKey), downKey)
        pendingPress = pending
        val result = keyPressed(e, pending)
        if (result != KeyEventProcessingResultDto.Unhandled) {
          pendingPress = null // the handler swallows the typed half of a handled press
        }
        result
      }
      KeyEvent.KEY_TYPED -> keyTyped(e)
      KeyEvent.KEY_RELEASED -> {
        if (pendingPress?.keyCode == e.keyCode) {
          pendingPress = null // the keystroke ended without a typed half: a bare modifier, a dead key
        }
        keyReleased(e)
      }
      else -> KeyEventProcessingResultDto.Unhandled
    }
  }

  /**
   * Forgets the held keys when the terminal loses the keyboard focus. Their releases go to the
   * component that took the focus, so the next press of such a key would count as a repeat.
   */
  fun focusLost() {
    downKeys.clear()
    pendingPress = null
  }

  /** Remembers the press the program saw, so [keyReleased] can pair the release with it. */
  private fun DownKey.record(event: TerminalKeyEvent, typed: Boolean = false) {
    press = event
    underKittyProtocol = emulator.kittyKeyboardFlags.isNotEmpty()
    this.typed = typed
  }

  private fun keyPressed(e: KeyEvent, pending: PendingPress): KeyEventProcessingResultDto {
    // Numpad Delete with NumLock on: the key code says Delete, but the key types '.'.
    if (e.keyCode == KeyEvent.VK_DELETE && e.keyChar == '.') {
      return bytesResult(byteArrayOf('.'.code.toByte()), e)
    }

    if (isShiftEnterAsEscCR(e)) {
      return bytesResult(byteArrayOf(ESC, CR), e)
    }

    macNaturalTextEditingChord(e)?.let { chord ->
      return bytesResult(chord, e)
    }

    val functionalKey = functionalKey(e)
    if (functionalKey != null) {
      val event = TerminalKeyEvent(functionalKey, pending.action, modifiers = terminalModifiers(e))
      val bytes = emulator.encodeKeyEvent(event)
      if (bytes.isEmpty()) {
        return KeyEventProcessingResultDto.Unhandled
      }
      pending.downKey.record(event)
      return bytesResult(bytes, e)
    }

    // Alt chords: with the "Alt sends Escape" setting on, Alt is a modifier for the shell and
    // the encoder applies the active protocol: an ESC prefix in legacy mode, CSI 27 under
    // modifyOtherKeys, a CSI u chord under Kitty. The text is the key's US character, not
    // e.keyChar: on macOS Option+F types 'ƒ' while ESC f is wanted. A key outside the US table
    // has only the character AWT computed. With the setting off, Option composes text and
    // KEY_TYPED types it.
    if (isAltChord(e) && settings.altSendsEscape()) {
      val writingKey = writingKey(e.keyCode)
      val modifiers = terminalModifiers(e)
      val event = when {
        writingKey != null -> TerminalKeyEvent(
          writingKey.key,
          pending.action,
          modifiers = modifiers,
          text = usText(writingKey, e, capsLock = TerminalInputModifier.CAPS_LOCK in modifiers),
          unshiftedCodepoint = writingKey.codepoint,
        )
        isPrintable(e.keyChar) -> TerminalKeyEvent(
          TerminalKey.UNIDENTIFIED,
          pending.action,
          modifiers = modifiers,
          text = e.keyChar.toString(),
          unshiftedCodepoint = e.keyChar.lowercaseChar().code,
        )
        else -> null
      }
      if (event != null) {
        encodeChord(event, pending)?.let { return it }
      }
    }

    if ((e.isAltGraphDown || (SystemInfoRt.isWindows && e.isControlDown && e.isAltDown)) &&
        Character.isDefined(e.keyChar) && !Character.isISOControl(e.keyChar)) {
      // Windows synthesizes AltGr as Ctrl+Alt(+AltGraph) down (never plain Alt alone), so this
      // isn't a real Ctrl chord; its keyChar already holds the AltGr symbol, not a control code
      // like a genuine Ctrl chord would carry. Leave it for KEY_TYPED to type normally. Elsewhere,
      // real AltGr reports as isAltGraphDown alone (X11's level-3 shift carries no Ctrl+Alt
      // synthesis), so a genuine Ctrl+Alt chord there is never mistaken for AltGr text.
      return KeyEventProcessingResultDto.Unhandled
    }

    // Ctrl chords arrive as KEY_PRESSED (AWT reduces their keyChar to a control character,
    // or to a plain space for Ctrl+Space). Hand the encoder the physical key, the text the
    // key produces without Ctrl, and its unmodified codepoint, so it can derive the control
    // byte, a fixterms CSI u chord, or a Kitty sequence.
    if (e.isControlDown) {
      val writingKey = writingKey(e.keyCode)
      if (writingKey != null) {
        classicControlByte(writingKey, e)?.let { bytes ->
          return KeyEventProcessingResultDto.BytesResult(bytes, settings.scrollToBottomOnTyping())
        }
        val modifiers = terminalModifiers(e)
        val event = TerminalKeyEvent(
          writingKey.key,
          pending.action,
          modifiers = modifiers,
          text = ctrlChordText(writingKey, e, capsLock = TerminalInputModifier.CAPS_LOCK in modifiers),
          unshiftedCodepoint = writingKey.codepoint,
        )
        encodeChord(event, pending)?.let { return it }
      }
    }

    // A control character the encoder produced nothing for (a chord on a layout the tables
    // above don't cover): send it the way AWT computed it. The encoder's text must never carry
    // a control byte, so this bypasses it. Printable characters are left to KEY_TYPED.
    if (isControlByte(e.keyChar)) {
      return KeyEventProcessingResultDto.BytesResult(byteArrayOf(e.keyChar.code.toByte()), settings.scrollToBottomOnTyping())
    }
    return KeyEventProcessingResultDto.Unhandled
  }

  /** Encodes a chord of the pressed half; null when the encoder produced nothing for it. */
  private fun encodeChord(event: TerminalKeyEvent, pending: PendingPress): KeyEventProcessingResultDto? {
    val bytes = emulator.encodeKeyEvent(event)
    if (bytes.isEmpty()) return null
    pending.downKey.record(event)
    return KeyEventProcessingResultDto.BytesResult(bytes, settings.scrollToBottomOnTyping())
  }

  private fun keyTyped(e: KeyEvent): KeyEventProcessingResultDto {
    if (isControlByte(e.keyChar)) {
      pendingPress = null // the KEY_PRESSED half of the pair owns control characters
      return KeyEventProcessingResultDto.Unhandled
    }
    return typedCharacter(e)
  }

  private fun typedCharacter(e: KeyEvent): KeyEventProcessingResultDto {
    // The pressed half of this keystroke named the physical key; a typed event carries none.
    val pending = pendingPress
    pendingPress = null
    val writingKey = pending?.let { writingKey(it.keyCode) }

    if (isAltChord(e) && settings.altSendsEscape()) {
      return KeyEventProcessingResultDto.Unhandled // the KEY_PRESSED half owns Alt chords
    }
    if (SystemInfoRt.isMac && e.keyChar == '`' && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0) {
      return KeyEventProcessingResultDto.Unhandled // Cmd+backtick cycles macOS windows; never type it
    }

    // Shift is consumed when it changed the character: under the Kitty keyboard protocol "@"
    // is Shift+2 typed, not a chord. Shift+Space types the same space, so Shift stays a
    // modifier and a program can bind the chord.
    val event = TerminalKeyEvent(
      writingKey?.key ?: TerminalKey.UNIDENTIFIED,
      pending?.action ?: TerminalKeyAction.PRESS,
      modifiers = typedModifiers(e),
      text = e.keyChar.toString(),
      unshiftedCodepoint = unshiftedCodepoint(writingKey, e.keyChar, e.isShiftDown),
      consumedModifiers = if (e.isShiftDown && shiftChangesCharacter(writingKey)) setOf(TerminalInputModifier.SHIFT) else emptySet(),
    )
    val bytes = emulator.encodeKeyEvent(event)
    if (bytes.isEmpty()) {
      return KeyEventProcessingResultDto.Unhandled
    }
    pending?.downKey?.record(event, typed = true)
    return KeyEventProcessingResultDto.StringResult(bytes.toString(Charsets.UTF_8), settings.scrollToBottomOnTyping())
  }

  /**
   * A release reaches the program only under the Kitty keyboard protocol, and only for a key
   * whose press the program saw through the encoder under that protocol. A press that a policy
   * of this layer, a key-event listener or the IDE took never reached the encoder, so its
   * release would be an orphan. So would the release of a press that went out in legacy mode,
   * before the program enabled the protocol. The release repeats the key, the code point and
   * the text of the press, so a program can pair the two events.
   */
  private fun keyReleased(e: KeyEvent): KeyEventProcessingResultDto {
    val downKey = downKeys.remove(downKeyId(e)) ?: return KeyEventProcessingResultDto.Unhandled
    val press = downKey.press
    if (press == null || !downKey.underKittyProtocol) {
      return KeyEventProcessingResultDto.Unhandled
    }
    val event = TerminalKeyEvent(
      press.key,
      TerminalKeyAction.RELEASE,
      modifiers = if (downKey.typed) typedModifiers(e) else terminalModifiers(e),
      text = press.text,
      unshiftedCodepoint = press.unshiftedCodepoint,
      consumedModifiers = if (e.isShiftDown) press.consumedModifiers else emptySet(),
    )
    val bytes = emulator.encodeKeyEvent(event)
    if (bytes.isEmpty()) {
      return KeyEventProcessingResultDto.Unhandled
    }
    return KeyEventProcessingResultDto.BytesResult(bytes, shouldScrollToBottom = false)
  }

  /**
   * Whether a KEY_PRESSED is a repeat. AWT has no repeat flag, so a second press of a key
   * still held is one. [focusLost] forgets the held keys whose releases the terminal never saw.
   */
  private fun pressAction(e: KeyEvent, downKey: DownKey): TerminalKeyAction =
    if (downKeys.put(downKeyId(e), downKey) != null) TerminalKeyAction.REPEAT else TerminalKeyAction.PRESS

  /**
   * The identity of a key in [downKeys]: the two Shift keys are different keys. So are the
   * keys AWT reports as VK_UNDEFINED, such as Ö and Ä on a German layout. The extended key
   * code tells them apart.
   */
  private fun downKeyId(e: KeyEvent): Int {
    val keyCode = if (e.keyCode == KeyEvent.VK_UNDEFINED) e.extendedKeyCode else e.keyCode
    return (keyCode shl 3) or (e.keyLocation and 7)
  }

  /**
   * The text a Ctrl chord produces without Ctrl, as the Ghostty hosts report it: "M" for
   * Ctrl+Shift+M. AWT reports the control character instead, so the text comes from the US
   * table whenever the character is not printable.
   */
  private fun ctrlChordText(writingKey: WritingKey, e: KeyEvent, capsLock: Boolean): String {
    val ch = e.keyChar
    // A printable keyChar is the platform's own answer, except a space on a key other than
    // Space: macOS reports Ctrl+Shift+2 (Ctrl+@, a NUL) as a space, the way it reports
    // Ctrl+Space.
    if (isPrintable(ch) && (ch != ' ' || writingKey.key == TerminalKey.SPACE)) return ch.toString()
    return usText(writingKey, e, capsLock)
  }

  /**
   * The classic byte of Ctrl+I, Ctrl+M and Ctrl+[ outside the Kitty keyboard protocol; null
   * for every other chord. An Alt chord gets the ESC prefix. The fixterms rule in the encoder
   * turns the three into CSI u chords. Every classic terminal sends Tab, Enter and Escape
   * instead, and bash, less and fzf expect those. Under a Kitty flag the encoder gets the
   * chord like any other. Under `modifyOtherKeys` state 2 the classic byte stays too, where
   * xterm sends `CSI 27;5;105~`. The C API exposes no way to read that mode.
   */
  private fun classicControlByte(writingKey: WritingKey, e: KeyEvent): ByteArray? {
    val byte = CLASSIC_CONTROL_BYTES[writingKey.key] ?: return null
    if (e.isShiftDown || emulator.kittyKeyboardFlags.isNotEmpty()) return null
    return if (e.isAltDown && settings.altSendsEscape()) byteArrayOf(ESC, byte) else byteArrayOf(byte)
  }

  /** The character the key produces on the US layout with the Shift and Caps Lock state of [e]. */
  private fun usText(writingKey: WritingKey, e: KeyEvent, capsLock: Boolean): String {
    val base = writingKey.codepoint.toChar()
    return when {
      base.isLetter() -> (if (e.isShiftDown != capsLock) base.uppercaseChar() else base).toString()
      e.isShiftDown -> writingKey.shiftedCodepoint.toChar().toString()
      else -> base.toString()
    }
  }

  /**
   * The code point the pressed key produces with no modifier. When the typed character is
   * what the US layout puts on that key, the table answers: '2' for '@'. A shifted digit key
   * has the digit unshifted on every layout but AZERTY, whatever symbol Shift typed. Otherwise
   * the layout is another one, and the lowercase of the character is the best available
   * answer: right for a letter in any script, the shifted symbol itself for a symbol.
   */
  private fun unshiftedCodepoint(writingKey: WritingKey?, ch: Char, shift: Boolean): Int {
    if (writingKey != null && (ch.code == writingKey.codepoint || ch.code == writingKey.shiftedCodepoint)) {
      return writingKey.codepoint
    }
    if (writingKey != null && shift && writingKey.codepoint.toChar().isDigit()) {
      return writingKey.codepoint
    }
    return ch.lowercaseChar().code
  }

  /**
   * Whether Shift changes what the key types. The US table says it does on every key but
   * Space. A key outside the table counts as changed: the layout's own symbol is all there is.
   */
  private fun shiftChangesCharacter(writingKey: WritingKey?): Boolean =
    writingKey == null || writingKey.shiftedCodepoint != writingKey.codepoint

  /** A C0 control character or DEL: the bytes the encoder's text must never carry. */
  private fun isControlByte(ch: Char): Boolean = ch.code < 0x20 || ch.code == 0x7F

  /** A character AWT computed for the key, as opposed to a control byte or none at all. */
  private fun isPrintable(ch: Char): Boolean = ch != KeyEvent.CHAR_UNDEFINED && !isControlByte(ch)

  /**
   * The macOS "natural text editing" chords, resolved above VT encoding like the
   * Ghostty app's default keybinds: Cmd+arrows edit the line via Ctrl+A / Ctrl+E,
   * Option+arrows jump words via ESC b / ESC f, and Cmd+Backspace kills the line via Ctrl+U.
   */
  private fun macNaturalTextEditingChord(e: KeyEvent): ByteArray? {
    if (!SystemInfoRt.isMac) return null
    val mods = modifierKeys(e)
    val cmd = mods == InputEvent.META_DOWN_MASK
    val option = mods == InputEvent.ALT_DOWN_MASK
    return when {
      cmd && e.keyCode == KeyEvent.VK_LEFT -> byteArrayOf(1) // Ctrl+A: line start
      cmd && e.keyCode == KeyEvent.VK_RIGHT -> byteArrayOf(5) // Ctrl+E: line end
      cmd && e.keyCode == KeyEvent.VK_BACK_SPACE -> byteArrayOf(NAK) // Ctrl+U: kill line
      option && e.keyCode == KeyEvent.VK_LEFT -> byteArrayOf(ESC, 'b'.code.toByte()) // backward-word
      option && e.keyCode == KeyEvent.VK_RIGHT -> byteArrayOf(ESC, 'f'.code.toByte()) // forward-word
      else -> null
    }
  }

  /**
   * Whether Shift+Enter is encoded as `ESC CR`, the legacy xterm encoding of Alt+Enter. JediTerm sends the same bytes.
   * This applies while the "Send Esc+CR on Shift+Enter" setting is on and the Kitty keyboard protocol is off.
   * Under the Kitty protocol, this function returns false, and the program gets `CSI 13;2u`, a real Shift+Enter.
   *
   * This is not a standard encoding.
   * Without the Kitty protocol and without this function, the encoder sends `CSI 27;2;13~`, the xterm modifyOtherKeys form.
   * However, ConPTY drops that sequence, so a Windows console program gets no key, and Codex inserts no new line (IJPL-260895).
   *
   * TODO: Consider to expose the modifyOtherKeys state in the emulator.
   *  Then this function can return false when modifyOtherKeys is on and no ConPTY is used.
   *  Another option is to support win32-input-mode. Then a Windows console program gets a real Shift+Enter through ConPTY.
   */
  private fun isShiftEnterAsEscCR(e: KeyEvent): Boolean =
    e.keyCode == KeyEvent.VK_ENTER &&
    modifierKeys(e) == InputEvent.SHIFT_DOWN_MASK &&
    settings.shiftEnterSendsEscCR() &&
    emulator.kittyKeyboardFlags.isEmpty()

  private fun bytesResult(bytes: ByteArray, e: KeyEvent): KeyEventProcessingResultDto.BytesResult {
    val shouldScroll = settings.scrollToBottomOnTyping() && isCodeThatScrolls(e.keyCode)
    return KeyEventProcessingResultDto.BytesResult(bytes, shouldScroll)
  }

  /**
   * A key from the functional block that encodes on KEY_PRESSED and produces no typed text
   * of interest. A modifier or lock key is one too: the encoder reports it under the Kitty
   * "report all keys" flag and stays silent otherwise.
   */
  private fun functionalKey(e: KeyEvent): TerminalKey? {
    val right = e.keyLocation == KeyEvent.KEY_LOCATION_RIGHT
    return when (e.keyCode) {
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
      in KeyEvent.VK_F1..KeyEvent.VK_F12 -> TerminalKey.entries[TerminalKey.F1.ordinal + (e.keyCode - KeyEvent.VK_F1)]
      KeyEvent.VK_SHIFT -> if (right) TerminalKey.SHIFT_RIGHT else TerminalKey.SHIFT_LEFT
      KeyEvent.VK_CONTROL -> if (right) TerminalKey.CONTROL_RIGHT else TerminalKey.CONTROL_LEFT
      KeyEvent.VK_ALT -> if (right) TerminalKey.ALT_RIGHT else TerminalKey.ALT_LEFT
      KeyEvent.VK_ALT_GRAPH -> TerminalKey.ALT_RIGHT
      KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> if (right) TerminalKey.META_RIGHT else TerminalKey.META_LEFT
      KeyEvent.VK_CAPS_LOCK -> TerminalKey.CAPS_LOCK
      KeyEvent.VK_NUM_LOCK -> TerminalKey.NUM_LOCK
      KeyEvent.VK_SCROLL_LOCK -> TerminalKey.SCROLL_LOCK
      // VK_CONTEXT_MENU is left out: the Menu key opens the IDE context menu.
      KeyEvent.VK_PRINTSCREEN -> TerminalKey.PRINT_SCREEN
      KeyEvent.VK_PAUSE -> TerminalKey.PAUSE
      else -> null
    }
  }

  /**
   * A key from the writing-system block: the [TerminalKey], and the code points it produces
   * on the US layout with no modifier and with Shift.
   */
  private class WritingKey(val key: TerminalKey, val codepoint: Int, val shiftedCodepoint: Int)

  private fun writingKey(keyCode: Int): WritingKey? = when (keyCode) {
    // VK codes for letters are the uppercase ASCII letters; both key ranges are
    // contiguous blocks.
    in KeyEvent.VK_A..KeyEvent.VK_Z ->
      WritingKey(TerminalKey.entries[TerminalKey.A.ordinal + (keyCode - KeyEvent.VK_A)], keyCode.toChar().lowercaseChar().code, keyCode)
    // VK codes for digits are the ASCII digits.
    in KeyEvent.VK_0..KeyEvent.VK_9 ->
      WritingKey(TerminalKey.entries[TerminalKey.DIGIT_0.ordinal + (keyCode - KeyEvent.VK_0)], keyCode, US_SHIFTED_DIGITS[keyCode - KeyEvent.VK_0].code)
    KeyEvent.VK_SPACE -> symbolKey(TerminalKey.SPACE, ' ', ' ')
    KeyEvent.VK_BACK_QUOTE -> symbolKey(TerminalKey.BACKQUOTE, '`', '~')
    KeyEvent.VK_MINUS -> symbolKey(TerminalKey.MINUS, '-', '_')
    KeyEvent.VK_EQUALS -> symbolKey(TerminalKey.EQUAL, '=', '+')
    KeyEvent.VK_OPEN_BRACKET -> symbolKey(TerminalKey.BRACKET_LEFT, '[', '{')
    KeyEvent.VK_CLOSE_BRACKET -> symbolKey(TerminalKey.BRACKET_RIGHT, ']', '}')
    KeyEvent.VK_BACK_SLASH -> symbolKey(TerminalKey.BACKSLASH, '\\', '|')
    KeyEvent.VK_SEMICOLON -> symbolKey(TerminalKey.SEMICOLON, ';', ':')
    KeyEvent.VK_QUOTE -> symbolKey(TerminalKey.QUOTE, '\'', '"')
    KeyEvent.VK_COMMA -> symbolKey(TerminalKey.COMMA, ',', '<')
    KeyEvent.VK_PERIOD -> symbolKey(TerminalKey.PERIOD, '.', '>')
    KeyEvent.VK_SLASH -> symbolKey(TerminalKey.SLASH, '/', '?')
    else -> null
  }

  private fun symbolKey(key: TerminalKey, codepoint: Char, shifted: Char) = WritingKey(key, codepoint.code, shifted.code)

  private fun terminalModifiers(e: KeyEvent): Set<TerminalInputModifier> = buildSet {
    if (e.isShiftDown) add(TerminalInputModifier.SHIFT)
    if (e.isControlDown) add(TerminalInputModifier.CTRL)
    if (e.isAltDown) add(TerminalInputModifier.ALT)
    if (e.isMetaDown) add(TerminalInputModifier.SUPER)
    addAll(locks)
  }

  /**
   * The modifiers of a typed character, and of its release: Shift, Super and the lock keys.
   * The character already reflects the keyboard layout, and Ctrl or Alt would make the encoder
   * derive a chord from it. AltGr text arrives with Ctrl+Alt down on Windows, and Option text
   * with Alt down on macOS. SUPER lets the encoder apply the macOS rule that a command chord
   * never types text.
   */
  private fun typedModifiers(e: KeyEvent): Set<TerminalInputModifier> = buildSet {
    if (e.isShiftDown) add(TerminalInputModifier.SHIFT)
    if (e.isMetaDown) add(TerminalInputModifier.SUPER)
    addAll(locks)
  }

  private fun modifierKeys(e: KeyEvent): Int =
    e.modifiersEx and (InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK
      or InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK)

  /** Alt held without AltGr and without Ctrl; a Ctrl+Alt chord belongs to the Ctrl path. */
  private fun isAltChord(e: KeyEvent): Boolean = e.isAltDown && !e.isAltGraphDown && !e.isControlDown

  private fun isCodeThatScrolls(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
    KeyEvent.VK_BACK_SPACE, KeyEvent.VK_INSERT, KeyEvent.VK_DELETE, KeyEvent.VK_ENTER,
    KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
      -> true
    else -> false
  }

  companion object {
    private const val TAB: Byte = 0x09
    private const val ESC: Byte = 0x1B
    private const val CR: Byte = 0x0D
    private const val NAK: Byte = 0x15  // Ctrl+U: kill line

    /** The US layout's shifted digits, indexed by digit. */
    private const val US_SHIFTED_DIGITS = ")!@#$%^&*("

    /** The chords fixterms reserves for CSI u, and the bytes a classic terminal sends for them. */
    private val CLASSIC_CONTROL_BYTES: Map<TerminalKey, Byte> =
      mapOf(TerminalKey.I to TAB, TerminalKey.M to CR, TerminalKey.BRACKET_LEFT to ESC)
  }
}

/**
 * The Caps Lock and Num Lock state, read from the toolkit. A lock the platform cannot report
 * (Num Lock on macOS; both on a headless toolkit) is asked once and then skipped.
 */
private object LockingKeys {
  private val locks = mapOf(
    KeyEvent.VK_CAPS_LOCK to TerminalInputModifier.CAPS_LOCK,
    KeyEvent.VK_NUM_LOCK to TerminalInputModifier.NUM_LOCK,
  )
  private val unsupported: MutableSet<Int> = ConcurrentHashMap.newKeySet()

  fun current(): Set<TerminalInputModifier> {
    val toolkit = Toolkit.getDefaultToolkit()
    return buildSet {
      for ((keyCode, modifier) in locks) {
        if (keyCode in unsupported) continue
        try {
          if (toolkit.getLockingKeyState(keyCode)) add(modifier)
        }
        catch (_: UnsupportedOperationException) {
          unsupported += keyCode
        }
      }
    }
  }
}
